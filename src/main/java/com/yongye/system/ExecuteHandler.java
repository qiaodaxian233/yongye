package com.yongye.system;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

/**
 * 处决斩杀(m270,战斗帅方针)——玩家近战把敌对怪打进「斩杀线」(默认剩 12% 血以内)时,
 * 这一刀直接升格为处决:魂柱冲天+暴击粒子雨+利刃终结音,怪当场毙命。
 * 设计要点:
 *  - 只吃「玩家直接近战」(source.getSource()==getAttacker(),弓弩/魔法/召唤物不触发——帅点在贴身收头);
 *  - 只处决敌对怪(HostileEntity),且最大血量 < executeBossHpExempt(5 万,m263 后全部 BOSS 25 万起)
 *    + 显式豁免凋灵——BOSS 该一刀一刀磨,处决只清小怪爽;
 *  - 实现:ALLOW_DAMAGE 里预判「这刀落下后仍活着但已进斩杀线」→ 取消原伤害,清无敌帧后
 *    补一记玩家名义的致死伤害(与坦克折减重放同款嵌套模式,REENTRY 旗标防自递归)——
 *    走真实伤害管线:掉落/经验/击杀归属/m239 击杀打击感(闪光+确认音)全部自然触发,零重复演出;
 *  - 若这刀本来就打死(预判血量 ≤0),不抢戏,交还原版结算。
 */
public final class ExecuteHandler {
    private ExecuteHandler() {}

    /** 嵌套补刀防自递归。 */
    private static boolean REENTRY = false;

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (REENTRY) return true;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableExecute || amount <= 0) return true;
            if (!(entity instanceof HostileEntity mob) || entity instanceof WitherEntity) return true;
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return true;
            if (source.getSource() != p) return true;               // 只认直接近战
            if (!(p.getWorld() instanceof ServerWorld sw)) return true;
            float max = mob.getMaxHealth();
            if (max >= cfg.executeBossHpExempt) return true;        // BOSS/超巨体豁免
            float predicted = mob.getHealth() - amount;
            if (predicted <= 0) return true;                        // 本来就致死:不抢戏
            if (predicted > max * Math.max(0.0, cfg.executeThresholdFraction)) return true;

            // —— 进入斩杀线:这一刀升格为处决 —— //
            Vec3d pos = mob.getPos();
            for (int i = 0; i < 10; i++) {                          // 魂柱冲天
                sw.spawnParticles(ParticleTypes.SOUL, pos.x, pos.y + 0.25 * i, pos.z, 2, 0.15, 0.05, 0.15, 0.01);
            }
            sw.spawnParticles(ParticleTypes.CRIT, pos.x, pos.y + mob.getHeight() * 0.6, pos.z, 24, 0.5, 0.5, 0.5, 0.35);
            sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, pos.x, pos.y + mob.getHeight() * 0.6, pos.z, 1, 0, 0, 0, 0);
            sw.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0f, 0.7f);
            sw.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.6f, 1.5f);
            p.sendMessage(Text.literal("处决!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);

            REENTRY = true;
            try {
                mob.timeUntilRegen = 0;                             // 清无敌帧,保证补刀吃满
                mob.damage(sw.getDamageSources().playerAttack(p), 1.0E7f);
            } finally {
                REENTRY = false;
            }
            return false;                                           // 原伤害取消,处决刀已代劳
        });
        com.yongye.Yongye.LOGGER.info("[夜蚀] 处决斩杀已挂载(近战打进斩杀线一刀终结)");
    }
}
