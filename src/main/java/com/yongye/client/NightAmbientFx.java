package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleTypes;

import java.util.Random;

/**
 * 永夜环境氛围粒子(m377,3A 打磨路线图第 5 项):永夜等级 ≥1 时玩家四周空中
 * 飘浮灰烬/白烬,等级越高越浓——末世感的"空气里有东西"。
 * <b>纯客户端本地粒子零流量</b>(m310 僵尸紫光同一路子),等级读
 * {@link YongyeClient#nightfallLevel}(NightfallSyncPayload 现成同步,零新包)。
 *
 * <p>浓度口径:每 tick 撒 min(14, 2+等级×2)×density 颗(等级 1≈4 颗/t,5≈12,深渊封顶 14),
 * 90% 灰烬 ASH + 10% 白烬 WHITE_ASH 出层次;落点=玩家水平 4~18 格环带、垂直 -2~+10 格
 * (贴脸 4 格内不撒,防糊镜头)。粒子本体是原版 ambient 型自带漂移,速度参数给 0。
 * 预算自觉:每 tick 上限 14 颗远低于原版雨雪量级,且照常被 ParticleReducerMixin 全局闸管到。
 *
 * <p>零新 API 面:ClientTickEvents/addParticle/ParticleTypes 全在树
 * (MobAuraFeatureRenderer 的 WITCH 粒子先例);ASH/WHITE_ASH 为同类 SimpleParticleType
 * 常量(1.16 起),与在树 CRIT/CLOUD/WITCH 同档取用。
 */
public final class NightAmbientFx {
    private NightAmbientFx() {}

    private static final Random RAND = new Random();

    /** 客户端初始化时挂(YongyeClient 调)。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableNightAmbientParticles) return;
            if (mc.world == null || mc.player == null) return;
            int lvl = YongyeClient.nightfallLevel;
            if (lvl < 1) return;

            double density = Math.max(0.0, Math.min(3.0, c.nightAmbientDensity));
            if (density <= 0) return;
            int count = FxBudget.scaleCount((int) Math.round(Math.min(14, 2 + lvl * 2) * density)); // m381 预算闸
            if (count <= 0) return;
            for (int i = 0; i < count; i++) {
                // 水平 4~18 格环带(贴脸不撒),垂直 -2~+10
                double ang = RAND.nextDouble() * Math.PI * 2;
                double dist = 4 + RAND.nextDouble() * 14;
                double x = mc.player.getX() + Math.cos(ang) * dist;
                double z = mc.player.getZ() + Math.sin(ang) * dist;
                double y = mc.player.getY() - 2 + RAND.nextDouble() * 12;
                mc.world.addParticle(RAND.nextInt(10) == 0
                                ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH,
                        x, y, z, 0, 0, 0);
            }
        });
    }
}
