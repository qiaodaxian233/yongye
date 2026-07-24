package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.network.CombatFxPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * BOSS 出场演出(m263):皮肤 BOSS(阿努比斯/浴火凤凰/死亡法师/红蜘蛛/自建末影龙/佩恩)
 * 降临瞬间,给范围内玩家整屏标题 + 镜头重震 + 闪光 + 凋灵吼 + 魂火螺旋腾起。
 * 全部走在树管线:TitleS2CPacket 三件套(CatastropheCoreManager 已编)、
 * CombatFxPayload(m239 已编)、spawnParticles/playSoundToPlayer(多处已编)。零新 API。
 * 注意:实体 age 不持久化,区块重载后 BOSS 再次进入视野会重演一次(压迫感,有意保留)。
 */
public final class BossEntranceFx {
    private BossEntranceFx() {}

    /**
     * 播放出场演出。
     * @param world 服务端世界
     * @param boss  刚登场的 BOSS
     * @param name  标题名(通常 boss.getType().getName() 或字面量)
     * @param color 标题颜色(与其血条同色系)
     */
    public static void play(ServerWorld world, LivingEntity boss, Text name, Formatting color) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableBossEntrance) return;
        double r = Math.max(8, cfg.bossEntranceRange);
        double r2 = r * r;
        Text title = name.copy().formatted(color, Formatting.BOLD);
        Text sub = Text.literal("巨物苏醒 · 它的目光落在了你身上").formatted(Formatting.GRAY);

        for (ServerPlayerEntity sp : world.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getWorld() != world) continue;
            if (sp.squaredDistanceTo(boss) > r2) continue;
            sp.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 45, 15));
            sp.networkHandler.sendPacket(new TitleS2CPacket(title));
            sp.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
            // 镜头重震 + 整屏闪光(flash 客户端不分 kind,m239 管线直接吃)
            ServerPlayNetworking.send(sp, new CombatFxPayload(CombatFxPayload.HEAVY,
                    (float) cfg.bossEntranceShake, 2.4f, true, false, 0));
            sp.playSoundToPlayer(SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.8f, 0.72f);
        }

        // 魂火双螺旋自地面盘升 + 顶端炸开(纯服务端粒子,所有观众可见)
        for (int i = 0; i < 44; i++) {
            double a = i * 0.55;
            double h = i * 0.14;
            double rad = 1.9;
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    boss.getX() + Math.cos(a) * rad, boss.getY() + h, boss.getZ() + Math.sin(a) * rad,
                    2, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    boss.getX() - Math.cos(a) * rad, boss.getY() + h, boss.getZ() - Math.sin(a) * rad,
                    2, 0.05, 0.05, 0.05, 0.0);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION,
                boss.getX(), boss.getY() + Math.max(1.0, boss.getHeight() * 0.6), boss.getZ(),
                3, 0.6, 0.8, 0.6, 0.0);
    }
}
