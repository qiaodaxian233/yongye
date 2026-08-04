package com.yongye.system;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Box;

import java.util.UUID;

/**
 * m320:召唤物协同集火(作者:「召唤师的召唤物应该玩家攻击什么它就攻击什么」)。
 * 两个方向,覆盖己方召唤物(m453 召唤师移除后只剩术士·暗影分身,与免友伤 m299 同一套判定):
 *  ① 集火:主人攻击 X → 半径内己方召唤物全部把目标切到 X(狼系 AttackWithOwnerGoal 语义,强制切换);
 *  ② 护主:主人被 Y 打 → **无目标/目标已死**的召唤物去打 Y(TrackOwnerAttackerGoal 语义,不打断正在集火的)。
 * 钩子口径(全在树先例):
 *  - 集火 = AttackEntityCallback(通用事件,服务端侧同样回调;SlashFx 已用它,先例在树);
 *  - 护主 = ServerLivingEntityEvents.ALLOW_DAMAGE 观察式挂法(1.21.1 的 fabric-api 0.105.0 没有
 *    AFTER_DAMAGE,HighHpCounterHandler 已踩过坑,照抄其"恒 return true 只观察"口径,不影响伤害结算)。
 * 实现要点:不动召唤物 AI goal 结构(零 mixin),直接 setTarget 交给原版攻击 goal 接手。
 * 只在同维度、summonAssistRadius(默认 32 格)内响应;不打玩家、不打己方召唤物(防倒戈)。
 */
public final class SummonAssistHandler {
    private SummonAssistHandler() {}

    public static void register() {
        // ① 集火:主人攻击谁,召唤物就打谁(强制切目标)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world instanceof ServerWorld sw && player instanceof ServerPlayerEntity sp
                    && entity instanceof LivingEntity target && target.isAlive()
                    && YongyeConfig.get().summonAssistFocus
                    && !(target instanceof PlayerEntity)
                    && !isOwnSummon(target, sp.getUuid())) {
                retarget(sw, sp, target, true);
            }
            return ActionResult.PASS;
        });

        // ② 护主:主人挨打,闲着的召唤物去支援(观察式 ALLOW_DAMAGE,恒放行)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity sp)) return true;
            if (!YongyeConfig.get().summonAssistDefend) return true;
            if (!(source.getAttacker() instanceof LivingEntity atk) || atk == sp) return true;
            if (atk instanceof PlayerEntity || isOwnSummon(atk, sp.getUuid())) return true;
            if (sp.getWorld() instanceof ServerWorld sw) {
                retarget(sw, sp, atk, false);
            }
            return true;
        });
    }

    /** 半径内己方召唤物切目标;force=false(护主)时不打断已有活目标的召唤物。 */
    private static void retarget(ServerWorld sw, ServerPlayerEntity owner, LivingEntity target, boolean force) {
        double r = Math.max(4.0, YongyeConfig.get().summonAssistRadius);
        Box box = owner.getBoundingBox().expand(r);
        UUID id = owner.getUuid();
        for (MobEntity mob : sw.getEntitiesByClass(MobEntity.class, box, m -> m.isAlive() && isOwnSummon(m, id))) {
            if (mob == target) continue;
            if (!force) {
                LivingEntity cur = mob.getTarget();
                if (cur != null && cur.isAlive()) continue;   // 护主不打断正在集火的目标
            }
            mob.setTarget(target);
        }
    }

    /** 己方召唤物判定:与 SummonFriendlyFireHandler(m299)/creditedKiller(m300)同一套口径。 */
    private static boolean isOwnSummon(Entity e, UUID owner) {
        if (e instanceof com.yongye.entity.WarlockCloneEntity w) return owner.equals(w.getOwner());
        return false;
    }
}
