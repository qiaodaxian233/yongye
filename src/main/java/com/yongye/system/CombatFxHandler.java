package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.network.CombatFxPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 沉浸式战斗手感(m239):玩家打怪的每一下都给出「看得见摸得着」的反馈——
 * 镜头微震、FOV 顿挫、命中粒子、击杀闪光 + 确认音。<b>纯视听层,零伤害改动。</b>
 *
 * <p>实现取舍:
 * <ul>
 *   <li>命中监听挂 {@code ALLOW_DAMAGE} 但<b>永远放行</b>(观察者)。注册顺序刻意排在
 *       {@link ForeignDamageFilterHandler} 之后:外来模组伤害被它取消时事件链已短路,
 *       本监听不会执行 → 无效伤害天然不出打击感,两套系统零耦合地保持一致。</li>
 *   <li>{@code ALLOW_DAMAGE} 触发在无敌帧判定之前,连点会对同一目标高频触发;
 *       用每玩家 3 tick 节流兜住,击杀反馈不受节流(那一下必须给足)。</li>
 *   <li>只对「玩家 → 非玩家」生效:PVP 不掺和,玩家挨打走原版受击镜头。</li>
 *   <li>强度按「单刀伤害 ÷ 怪最大生命」折算:后期怪血上天时单刀占比小 → 反馈自动收敛成
 *       轻微震感,不会全程狂震;一刀砍掉 25%+ 判「重击」加重反馈。</li>
 * </ul>
 */
public final class CombatFxHandler {
    private CombatFxHandler() {}

    /** 命中反馈节流:每玩家两次命中 FX 至少间隔的 tick 数(击杀不受此限)。 */
    private static final int HIT_THROTTLE_TICKS = 3;
    /** 上一次给该玩家发命中 FX 的世界时间(transient,不持久化)。 */
    private static final Map<UUID, Long> LAST_HIT_FX = new HashMap<>();

    /** m373 伤害飘字每玩家每 tick 限发条数(AOE 一刀扫几十只时防包洪与客户端铺屏)。 */
    private static final int DMG_NUM_PER_TICK = 8;
    /** 飘字限额跟踪:值=[该 tick 的世界时间, 该 tick 已发条数](transient)。 */
    private static final Map<UUID, long[]> DMG_NUM_BUDGET = new HashMap<>();

    /** 该玩家本 tick 是否还有飘字额度(有则顺手计数)。 */
    private static boolean dmgNumBudgetOk(UUID id, long now) {
        long[] st = DMG_NUM_BUDGET.get(id);
        if (st == null || st[0] != now) {
            DMG_NUM_BUDGET.put(id, new long[]{now, 1});
            return true;
        }
        if (st[1] >= DMG_NUM_PER_TICK) return false;
        st[1]++;
        return true;
    }

    public static void register() {
        // —— 命中/重击:观察者,永远 return true —— //
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableCombatFx || amount <= 0) return true;
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return true;
            if (entity instanceof PlayerEntity) return true;          // 只反馈打怪,PVP 不掺和
            if (entity == p) return true;

            long now = p.getWorld().getTime();
            float frac = amount / Math.max(1.0f, entity.getMaxHealth()); // 这一下占怪最大生命的比例

            // —— m373 伤害飘字:每一下命中都弹数字(刻意不吃下方 3t 手感节流——数字漏帧=报假账);
            //    AOE 刷屏由独立的每玩家每 tick 限额兜住,同屏总量客户端 DamageNumberManager 再兜一层。 —— //
            if ((c.enableDamageNumbers || c.enableMobHealthBar) && dmgNumBudgetOk(p.getUuid(), now)) {
                ServerPlayNetworking.send(p, new com.yongye.network.DamageNumberPayload(
                        entity.getX(), entity.getBodyY(0.9), entity.getZ(), amount,
                        frac >= 0.25f ? com.yongye.network.DamageNumberPayload.HEAVY
                                      : com.yongye.network.DamageNumberPayload.HIT,
                        entity.getId()));       // m385:目标 id 供微型血条追踪(两功能任一开即发,客户端各取所需)
            }

            Long last = LAST_HIT_FX.get(p.getUuid());
            if (last != null && now - last < HIT_THROTTLE_TICKS) return true;
            LAST_HIT_FX.put(p.getUuid(), now);

            int kind = frac >= 0.25f ? CombatFxPayload.HEAVY : CombatFxPayload.HIT;
            // m248:基准强度整体上调(实机反馈「打击感还是不强」)——普通命中 0.30→0.55 起步、
            // 封顶 1.0→1.5,重击 FOV 1.4→2.4、轻击 0.6→1.1;倍率配置照乘,想回旧手感把两倍率设 0.6 即可。
            float shake = (float) (Math.min(1.5f, 0.55f + frac * 2.6f) * c.combatFxShakeScale);
            float fov   = (float) ((kind == CombatFxPayload.HEAVY ? 2.4f : 1.1f) * c.combatFxFovKick);
            // m275 击杀顿帧:重击 2t(轻击不停,避免连打发黏);击杀 4t 在下方死亡回调里发
            int stop = (c.enableCombatFxHitstop && kind == CombatFxPayload.HEAVY)
                    ? Math.max(1, (int) Math.round(2 * c.combatFxHitstopScale)) : 0;
            ServerPlayNetworking.send(p, new CombatFxPayload(kind, shake, fov, false, false, stop));

            if (c.combatFxParticles && entity.getWorld() instanceof ServerWorld sw) {
                int n = Math.min(14, 4 + (int) (frac * 24));
                sw.spawnParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getBodyY(0.6), entity.getZ(), n, 0.35, 0.3, 0.35, 0.25);
            }

            // —— m383 命中音材质分层(骨=脆响/硬甲=铿锵/肉=闷响,原版音变调零新资源)——
            //    与镜头手感共用上方 3t 节流(节流内=不响,天然限流不炸耳,m379 评审 27 号预览口径);
            //    重击音量略抬、音高随伤害占比微升,叠在原版怪物受伤叫声之上出"打在材质上"的层次。
            if (c.enableCombatHitSound && entity.getWorld() instanceof ServerWorld sw2) {
                float vol = (kind == CombatFxPayload.HEAVY ? 0.55f : 0.4f);
                float pitch = 1.0f + Math.min(0.4f, frac * 0.8f);
                if (entity instanceof AbstractSkeletonEntity) {          // 骨:脆响
                    sw2.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.BLOCK_BONE_BLOCK_HIT, SoundCategory.PLAYERS, vol + 0.15f, pitch * 1.1f);
                } else if (entity.getArmor() >= 10) {                     // 硬甲(重甲怪/傀儡/多数 BOSS):铿锵
                    sw2.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.20f, 1.7f + frac);
                } else {                                                  // 肉:闷响
                    sw2.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, vol, pitch * 0.85f);
                }
            }
            return true;
        });

        // —— 击杀:加重镜头冲击 + 闪光 + 确认音 + 消散粒子 —— //
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableCombatFx) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return;
            if (entity instanceof PlayerEntity) return;

            float shake = (float) (1.7f * c.combatFxShakeScale);   // m248:击杀冲击 1.1→1.7
            float fov   = (float) (2.8f * c.combatFxFovKick);      // m248:击杀顿挫 1.8→2.8
            int stop = c.enableCombatFxHitstop
                    ? Math.max(1, (int) Math.round(4 * c.combatFxHitstopScale)) : 0; // m275 击杀顿帧
            ServerPlayNetworking.send(p, new CombatFxPayload(
                    CombatFxPayload.KILL, shake, fov, c.combatFxKillFlash, c.combatFxKillSound, stop));

            if (c.combatFxParticles && entity.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.CLOUD,
                        entity.getX(), entity.getBodyY(0.5), entity.getZ(), 10, 0.4, 0.4, 0.4, 0.05);
                sw.spawnParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getBodyY(0.6), entity.getZ(), 16, 0.5, 0.5, 0.5, 0.3);
            }
        });

        // —— m374 受击方向指示:玩家挨打 → 把来源水平坐标发给受击者(观察者,永远放行)。 —— //
        //    注册在格挡(m259)/坦克真减伤(m208)之后:被挡下/取消的伤害事件链已短路,不出指示——
        //    与「无效伤害天然不出打击感」口径一致。无坐标来源的环境伤害(摔落/中毒/凋零)天然跳过。
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableHurtDirectionFx || amount <= 0) return true;
            if (!(entity instanceof ServerPlayerEntity victim)) return true;

            // 来源坐标:攻击者优先(近战/弓手本体),弹射物/无主来源实体兜底(getSource 在树 m192 先例)
            var src = source.getAttacker() != null ? source.getAttacker() : source.getSource();
            if (src == null || src == victim) return true;

            long now = victim.getWorld().getTime();
            if (!hurtDirBudgetOk(victim.getUuid(), now)) return true;

            float severity = amount / Math.max(1.0f, victim.getMaxHealth());
            ServerPlayNetworking.send(victim, new com.yongye.network.HurtDirectionPayload(
                    src.getX(), src.getZ(), Math.min(1.0f, severity)));
            return true;
        });
    }

    /** m374 受击方向指示每玩家每 tick 限发条数(被围殴/AOE 弹幕时防包洪)。 */
    private static final int HURT_DIR_PER_TICK = 4;
    private static final Map<UUID, long[]> HURT_DIR_BUDGET = new HashMap<>();

    private static boolean hurtDirBudgetOk(UUID id, long now) {
        long[] st = HURT_DIR_BUDGET.get(id);
        if (st == null || st[0] != now) {
            HURT_DIR_BUDGET.put(id, new long[]{now, 1});
            return true;
        }
        if (st[1] >= HURT_DIR_PER_TICK) return false;
        st[1]++;
        return true;
    }
}
