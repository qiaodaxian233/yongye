package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactType;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 高血量反制(文档第 17 章)。
 * Boss / 精英 / 永夜怪命中玩家时:附加「最大生命百分比 + 真实(无视护甲)伤害」,
 * 并按概率施加禁疗与短时最大生命压制。骨箭护符可概率免疫箭矢。
 *
 * 注:1.21.1 的 fabric-api(0.105.0)没有 AFTER_DAMAGE,故全部逻辑挂在 ALLOW_DAMAGE 上
 *     (该事件在伤害结算前触发,这里放行原伤害并额外补一刀)。
 */
public final class HighHpCounterHandler {
    private HighHpCounterHandler() {}

    private static final Identifier ID_SUPPRESS = Identifier.of(Yongye.MOD_ID, "hp_suppress");
    private static final double[] BONE_NEGATE = {0.10, 0.20, 0.35, 0.50, 0.65, 0.80};
    private static final Map<UUID, Long> SUPPRESS_UNTIL = new HashMap<>();

    private static boolean applying = false;
    private static int tickCounter = 0;

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;

            // 闪避(技能书):概率完全闪避本次伤害
            if (SkillEffectManager.rollEvasion(player)) {
                return false;
            }

            // 骨箭护符:概率免疫/弹开箭矢
            int bone = ArtifactManager.getActiveLevel(player, ArtifactType.BONE_ARROW_CHARM);
            if (bone > 0 && source.getSource() instanceof PersistentProjectileEntity) {
                if (player.getRandom().nextDouble() < BONE_NEGATE[bone - 1]) {
                    return false;
                }
            }

            // 反伤(技能书):把一定比例伤害反弹给攻击者
            if (!applying) {
                double tf = SkillEffectManager.thornsFactor(player);
                if (tf > 0 && source.getAttacker() instanceof net.minecraft.entity.LivingEntity atk && atk != player) {
                    applying = true;
                    atk.damage(player.getDamageSources().thorns(player), (float) (amount * tf));
                    applying = false;
                }
            }

            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableHighHpCounter || applying) return true;
            if (!(source.getAttacker() instanceof MobEntity attacker)) return true;

            boolean boss = attacker.getAttachedOrElse(ModAttachments.IS_BOSS, false);
            boolean elite = attacker.getAttachedOrElse(ModAttachments.IS_ELITE, false);
            int nf = NightfallManager.getLevel();
            if (!boss && !elite && nf < 1) return true;

            double pct = boss ? cfg.bossPercentDamage : elite ? cfg.elitePercentDamage : cfg.elitePercentDamage * 0.5;
            double trueDmg = boss ? cfg.bossTrueDamage : elite ? cfg.eliteTrueDamage : cfg.eliteTrueDamage * 0.5;
            double bonus = player.getMaxHealth() * pct + trueDmg;

            if (bonus > 0) {
                applying = true;
                player.damage(player.getDamageSources().magic(), (float) bonus); // 魔法伤害无视护甲
                applying = false;
            }

            // 禁疗
            if (player.getRandom().nextDouble() < cfg.healBlockChance) {
                long until = player.getWorld().getTime() + cfg.healBlockDurationTicks;
                player.setAttached(ModAttachments.NO_HEAL_UNTIL, until);
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER,
                        cfg.healBlockDurationTicks, 0, true, false, false));
            }

            // 最大生命压制(仅 Boss,概率触发)
            if (boss && player.getRandom().nextDouble() < 0.2) {
                applySuppress(player);
            }

            return true; // 放行原始伤害
        });

        // 过期清理最大生命压制
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (SUPPRESS_UNTIL.isEmpty()) return;
            if (++tickCounter < 20) return;
            tickCounter = 0;
            long now = server.getOverworld().getTime();
            SUPPRESS_UNTIL.entrySet().removeIf(e -> {
                if (now < e.getValue()) return false;
                ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
                if (p != null) {
                    EntityAttributeInstance inst = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
                    if (inst != null) inst.removeModifier(ID_SUPPRESS);
                }
                return true;
            });
        });

        // 玩家退出清理压制表,避免内存堆积
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                SUPPRESS_UNTIL.remove(handler.player.getUuid()));

        Yongye.LOGGER.info("[亡途荒夜] 高血量反制系统已挂载");
    }

    private static void applySuppress(ServerPlayerEntity player) {
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst == null) return;
        inst.removeModifier(ID_SUPPRESS);
        inst.addTemporaryModifier(new EntityAttributeModifier(
                ID_SUPPRESS, -0.1, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        SUPPRESS_UNTIL.put(player.getUuid(), player.getWorld().getTime() + 100L);
    }
}
