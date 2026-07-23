package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.network.CombatFxPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
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

    public static void register() {
        // —— 命中/重击:观察者,永远 return true —— //
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableCombatFx || amount <= 0) return true;
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return true;
            if (entity instanceof PlayerEntity) return true;          // 只反馈打怪,PVP 不掺和
            if (entity == p) return true;

            long now = p.getWorld().getTime();
            Long last = LAST_HIT_FX.get(p.getUuid());
            if (last != null && now - last < HIT_THROTTLE_TICKS) return true;
            LAST_HIT_FX.put(p.getUuid(), now);

            float frac = amount / Math.max(1.0f, entity.getMaxHealth()); // 这一下占怪最大生命的比例
            int kind = frac >= 0.25f ? CombatFxPayload.HEAVY : CombatFxPayload.HIT;
            float shake = (float) (Math.min(1.0f, 0.30f + frac * 2.0f) * c.combatFxShakeScale);
            float fov   = (float) ((kind == CombatFxPayload.HEAVY ? 1.4f : 0.6f) * c.combatFxFovKick);
            ServerPlayNetworking.send(p, new CombatFxPayload(kind, shake, fov, false, false));

            if (c.combatFxParticles && entity.getWorld() instanceof ServerWorld sw) {
                int n = Math.min(14, 4 + (int) (frac * 24));
                sw.spawnParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getBodyY(0.6), entity.getZ(), n, 0.35, 0.3, 0.35, 0.25);
            }
            return true;
        });

        // —— 击杀:加重镜头冲击 + 闪光 + 确认音 + 消散粒子 —— //
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableCombatFx) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return;
            if (entity instanceof PlayerEntity) return;

            float shake = (float) (1.1f * c.combatFxShakeScale);
            float fov   = (float) (1.8f * c.combatFxFovKick);
            ServerPlayNetworking.send(p, new CombatFxPayload(
                    CombatFxPayload.KILL, shake, fov, c.combatFxKillFlash, c.combatFxKillSound));

            if (c.combatFxParticles && entity.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.CLOUD,
                        entity.getX(), entity.getBodyY(0.5), entity.getZ(), 10, 0.4, 0.4, 0.4, 0.05);
                sw.spawnParticles(ParticleTypes.CRIT,
                        entity.getX(), entity.getBodyY(0.6), entity.getZ(), 16, 0.5, 0.5, 0.5, 0.3);
            }
        });
    }
}
