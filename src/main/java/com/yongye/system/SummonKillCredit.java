package com.yongye.system;

import com.yongye.YongyeConfig;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

/**
 * m300 击杀归属(作者:「召唤物击杀也算这个人击杀,要么就没意思了」)——
 * m453 从 SummonerHandler 迁出:召唤师职业彻底移除后,这条口径仍被全库九处
 * 「玩家击杀」判定共用(看板计数/随机掉落门+动态爆率/保护卷/贪婪经验/击杀任务/
 * 主线计数/讨伐看板/日常悬赏/蚀域掉落),且术士·暗影分身仍是在册召唤物,基建必须留。
 * 攻击者是玩家 → 本人;是己方暗影分身 → 折算到主人(主人在线才算)。
 * 关 summonKillsCreditOwner 回「只认亲手」。
 */
public final class SummonKillCredit {
    private SummonKillCredit() {}

    public static ServerPlayerEntity creditedKiller(net.minecraft.entity.damage.DamageSource source) {
        net.minecraft.entity.Entity a = source.getAttacker();
        if (a instanceof ServerPlayerEntity p) return p;
        if (a == null || !YongyeConfig.get().summonKillsCreditOwner) return null;
        UUID owner = null;
        if (a instanceof com.yongye.entity.WarlockCloneEntity w) owner = w.getOwner();
        if (owner == null || !(a.getWorld() instanceof ServerWorld sw)) return null;
        return sw.getPlayerByUuid(owner) instanceof ServerPlayerEntity sp ? sp : null;
    }
}
