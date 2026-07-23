package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.item.ClassWeaponItem;
import com.yongye.item.PlayerClass;
import com.yongye.network.CombatFxPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 武器右键格挡(m259)——作者:「所有武器除了法杖,右键可以格挡,有格挡值,格挡值被打掉后就无法格挡了」。
 * 学 Epic Fight 的护盾槽(Stamina/Guard)设计,原生实现:
 *  - 举盾:主手持武器(刀光同口径:本模组武器/原版剑斧戟;唯独排除法杖=术士职业武器,它右键是蓄力弹)
 *    按住右键=格挡姿态(原版按住右键每 4t 重发一次交互,借它当「持续举着」的心跳,guardHoldTicks 内有效),
 *    举着有缓慢 I 负重感;
 *  - 格挡值:上限=最大生命 × guardMaxHealthFraction(默认 60%,跟随成长曲线,后期照样挡得动)保底 guardMinValue;
 *    只挡「正面、有攻击者」的伤害(背刺/摔落/中毒挡不了),挡下=伤害全免、扣格挡值、盾声+火花+轻震;
 *  - 破防:这一下伤害 ≥ 剩余格挡值 → 被击穿:该击**全额命中**、格挡值清零、guardBreakRecoverTicks(默认 5 秒)
 *    内无法再格挡(红字「破防!」+碎裂声+缓慢 II 硬直),期满格挡值直接回满;
 *  - 回复:未破防时,距上次挡下超过 guardRegenDelayTicks 后,每秒回 上限×guardRegenFractionPerSec。
 *  - m269 完美格挡·弹反:起手 parryWindowTicks(默认 6t=0.3s)内接住任意可挡攻击 → 全免不耗值,
 *    近身攻击者吃反噬伤害+被弹开硬直(缓速III+虚弱II),自身获力量II+速度I反击窗口;
 *    心跳续期不刷新起手时刻——按住不放蹭不出弹反,必须掐点重举。
 * 事件顺序(注册位置=CombatFxHandler 之前):外来伤害过滤/职业受击(坦克真减伤)先行,本格挡后审——
 * 坦克重放的折减伤害会被本格挡正常接住(挡的是减免后的量,不双扣);挡下的伤害不触发攻击者打击感。
 */
public final class WeaponGuardHandler {
    private WeaponGuardHandler() {}

    private static final class Guard {
        double gauge = -1;      // -1=未初始化(首次用时按上限初始化)
        long guardUntil;        // 举盾有效期(每次右键心跳续期)
        long raiseTick = Long.MIN_VALUE; // m269:本次「起手举盾」时刻(心跳续期不刷新——想再弹反必须放下重举)
        long brokenUntil;       // >now=破防硬直中
        long lastBlockTick;     // 上次挡下的时刻(回复延迟基准)
    }

    private static final Map<UUID, Guard> STATES = new HashMap<>();

    public static void register() {
        // —— 举盾:右键心跳 —— //
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (world.isClient || hand != Hand.MAIN_HAND) return TypedActionResult.pass(stack);
            if (!(player instanceof ServerPlayerEntity p)) return TypedActionResult.pass(stack);
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableWeaponGuard) return TypedActionResult.pass(stack);
            if (!ChargeSlashHandler.weaponOk(stack)) return TypedActionResult.pass(stack);
            if (stack.getItem() instanceof ClassWeaponItem cwi && cwi.playerClass == PlayerClass.WARLOCK)
                return TypedActionResult.pass(stack); // 法杖右键=蓄力弹,不参与格挡

            long now = p.server.getTicks();
            Guard g = STATES.computeIfAbsent(p.getUuid(), k -> new Guard());
            if (g.gauge < 0) g.gauge = maxGauge(p, cfg);
            if (now < g.brokenUntil) {
                p.sendMessage(Text.literal("破防中……" + (int) Math.ceil((g.brokenUntil - now) / 20.0) + "s")
                        .formatted(Formatting.RED), true);
                return TypedActionResult.pass(stack);
            }
            if (now >= g.guardUntil) g.raiseTick = now;   // m269:非心跳续期=全新起手
            g.guardUntil = now + Math.max(2, cfg.guardHoldTicks);
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 10, 0)); // 举盾负重
            p.sendMessage(gaugeBar(g.gauge, maxGauge(p, cfg), Formatting.AQUA), true);
            return TypedActionResult.pass(stack);
        });

        // —— 挡伤害:正面、有攻击者才可挡 —— //
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p)) return true;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableWeaponGuard || amount <= 0) return true;
            Guard g = STATES.get(p.getUuid());
            long now = p.server.getTicks();
            if (g == null || g.gauge < 0 || now >= g.guardUntil || now < g.brokenUntil) return true;
            Entity attacker = source.getAttacker();
            if (attacker == null) return true;                     // 环境伤害(摔落/岩浆/中毒)挡不了
            Vec3d to = attacker.getPos().subtract(p.getPos());
            Vec3d toH = new Vec3d(to.x, 0, to.z);
            if (toH.lengthSquared() > 1.0e-4) {
                Vec3d look = p.getRotationVector();
                Vec3d lookH = new Vec3d(look.x, 0, look.z).normalize();
                if (lookH.dotProduct(toH.normalize()) < cfg.guardFrontalDot) return true; // 背后/侧后挡不住
            }

            // —— m269 完美格挡·弹反:起手 parryWindowTicks 内接住的攻击(哪怕本该破防)——
            // 全免+不耗格挡值+反噬伤害+弹开硬直攻击者+自身获反击强化。奖励精准时机,Sekiro 口径。
            if (cfg.enableParry && now - g.raiseTick <= Math.max(1, cfg.parryWindowTicks)) {
                g.lastBlockTick = now;   // 只作回复延迟基准,不扣值
                if (p.getWorld() instanceof ServerWorld sw) {
                    Vec3d front = p.getPos().add(p.getRotationVector().multiply(0.9)).add(0, 1.3, 0);
                    sw.spawnParticles(ParticleTypes.FIREWORK, front.x, front.y, front.z, 16, 0.3, 0.3, 0.3, 0.12);
                    sw.spawnParticles(ParticleTypes.END_ROD,  front.x, front.y, front.z, 10, 0.25, 0.25, 0.25, 0.08);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 1.6f);
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.6f, 1.9f); // 金铁交鸣
                    if (attacker instanceof net.minecraft.entity.LivingEntity le && le.isAlive()
                            && le.squaredDistanceTo(p) <= 6.0 * 6.0) {
                        // 反噬:挡回的力道×比例,保底自己一刀的攻击力;先清无敌帧保证吃满
                        double atk = p.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
                        float reflect = (float) Math.max(atk, amount * Math.max(0.0, cfg.parryReflectFraction));
                        le.timeUntilRegen = 0;
                        le.damage(sw.getDamageSources().playerAttack(p), reflect);
                        Vec3d away = le.getPos().subtract(p.getPos());
                        le.takeKnockback(1.6, -away.x, -away.z);   // 注意 takeKnockback 语义:朝参数反方向飞 → 传负值=弹离玩家
                        le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 2));
                        le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 40, 1));
                    }
                }
                int buff = Math.max(20, cfg.parryBuffTicks);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, buff, 1)); // 力量II反击窗口
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, buff, 0));
                ServerPlayNetworking.send(p, new CombatFxPayload(CombatFxPayload.HEAVY, 1.1f, 2.2f, true, true));
                p.sendMessage(Text.literal("完美格挡!弹反!").formatted(Formatting.GOLD, Formatting.BOLD), true);
                return false;
            }

            if (amount < g.gauge) {
                // 挡下:全免 + 扣格挡值
                g.gauge -= amount;
                g.lastBlockTick = now;
                if (p.getWorld() instanceof ServerWorld sw) {
                    sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0f, 0.9f);
                    Vec3d front = p.getPos().add(p.getRotationVector().multiply(0.8)).add(0, 1.2, 0);
                    sw.spawnParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 8, 0.25, 0.25, 0.25, 0.15);
                }
                ServerPlayNetworking.send(p, new CombatFxPayload(CombatFxPayload.HIT, 0.6f, 1.2f, false, false));
                p.sendMessage(gaugeBar(g.gauge, maxGauge(p, cfg), Formatting.AQUA), true);
                return false;
            }
            // 被击穿:这一下全额命中,破防硬直,期满回满
            g.gauge = 0;
            g.guardUntil = 0;
            g.brokenUntil = now + Math.max(20, cfg.guardBreakRecoverTicks);
            if (p.getWorld() instanceof ServerWorld sw) {
                sw.playSound(null, p.getX(), p.getY(), p.getZ(),
                        SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
            }
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1)); // 破防硬直
            p.sendMessage(Text.literal("破防!格挡被击穿,该击全额命中!").formatted(Formatting.DARK_RED), false);
            return true;
        });

        // —— 回复:每秒结算 —— //
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0 || STATES.isEmpty()) return;
            YongyeConfig cfg = YongyeConfig.get();
            long now = server.getTicks();
            STATES.forEach((id, g) -> {
                if (g.gauge < 0) return;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(id);
                if (p == null) return;
                double max = maxGauge(p, cfg);
                if (g.brokenUntil > 0 && now >= g.brokenUntil) {
                    g.brokenUntil = 0;
                    g.gauge = max;                                  // 破防期满直接回满
                    p.sendMessage(Text.literal("格挡已恢复").formatted(Formatting.GREEN), true);
                } else if (g.brokenUntil == 0 && g.gauge < max
                        && now - g.lastBlockTick > Math.max(0, cfg.guardRegenDelayTicks)) {
                    g.gauge = Math.min(max, g.gauge + max * Math.max(0.0, cfg.guardRegenFractionPerSec));
                }
            });
        });

        com.yongye.Yongye.LOGGER.info("[夜蚀] 武器格挡系统已挂载(右键举盾,法杖除外)");
    }

    /** 格挡值上限=最大生命×比例(跟随成长,后期照样挡得动),保底 guardMinValue。 */
    private static double maxGauge(ServerPlayerEntity p, YongyeConfig cfg) {
        return Math.max(cfg.guardMinValue, p.getMaxHealth() * cfg.guardMaxHealthFraction);
    }

    private static Text gaugeBar(double cur, double max, Formatting color) {
        int bars = (int) Math.ceil(10.0 * Math.max(0, Math.min(1.0, cur / Math.max(1.0, max))));
        return Text.literal("格挡 " + "▮".repeat(bars) + "▯".repeat(10 - bars)
                + " " + (long) cur + "/" + (long) max).formatted(color);
    }
}
