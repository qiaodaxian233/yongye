package com.yongye.system;

import com.yongye.YongyeConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 空中回旋斩范围伤害(m258)——作者:「旋转的时候为什么不能给一圈造成伤害」。
 * 七式之四(空中回旋)此前纯视觉;现在客户端触发回旋斩即上报,服务端对身周整圈结算:
 *  - 校验:离地(isOnGround=false 或有下落距离,宽容判定防同步瞬差)+ 主手武器(与蓄力重斩同口径)+ 短冷却(防狂点);
 *  - 伤害 = 攻击力 × spinSlashDamageRatio(默认 0.8,略低于正刀,毕竟是范围)对半径内全部敌对结算,
 *    向外击退(转圈把怪甩开);正常砍中的那只怪会同时吃原版一刀+本圈,属回旋斩应得的爽点。
 */
public final class SpinSlashHandler {
    private SpinSlashHandler() {}

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    public static void perform(ServerPlayerEntity player) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableSpinSlashAoe) return;
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        if (player.isOnGround() && player.fallDistance <= 0) return;      // 必须在空中(宽容:有下落距离也算)
        if (!ChargeSlashHandler.weaponOk(player.getMainHandStack())) return;

        long now = player.server.getTicks();
        long until = COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
        if (now < until) return;                                          // 冷却内静默(狂点保护,不刷提示)
        COOLDOWN_UNTIL.put(player.getUuid(), now + Math.max(1, cfg.spinSlashCooldownTicks));

        double dmg = player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE)
                * Math.max(0.0, cfg.spinSlashDamageRatio);
        if (dmg <= 0) return;
        double radius = Math.max(1.0, cfg.spinSlashRadius);
        DamageSource src = world.getDamageSources().playerAttack(player);

        Box box = player.getBoundingBox().expand(radius);
        Vec3d me = player.getPos();
        int hit = 0;
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player
                        && (e instanceof Monster || e.getAttachedOrElse(com.yongye.registry.ModAttachments.IS_ELITE, false))
                        && e.squaredDistanceTo(player) <= radius * radius)) {
            le.damage(src, (float) dmg);
            Vec3d away = le.getPos().subtract(me);
            double len = Math.max(0.01, Math.sqrt(away.x * away.x + away.z * away.z));
            le.takeKnockback(1.0, -away.x / len, -away.z / len);          // 向外甩开
            hit++;
        }
        // 身周一圈横扫粒子 + 转圈重音(有命中才响重音,挥空只有轻风声)
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK,
                    me.x + Math.cos(a) * radius * 0.7, me.y + 1.0, me.z + Math.sin(a) * radius * 0.7,
                    1, 0, 0, 0, 0);
        }
        world.playSound(null, me.x, me.y, me.z, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                SoundCategory.PLAYERS, 1.0f, hit > 0 ? 0.7f : 1.1f);
    }
}
