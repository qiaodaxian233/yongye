package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.entity.AnubisEntity;
import com.yongye.entity.DeathMageEntity;
import com.yongye.entity.FirePhoenixEntity;
import com.yongye.entity.RedSpiderEntity;
import com.yongye.entity.ToroEnderDragonEntity;
import com.yongye.network.CombatFxPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
import net.minecraft.util.Identifier;

/**
 * BOSS 阶段转换·半血狂暴(m274,学 Epic Fight「阶段感」):
 * 五只皮肤 BOSS + 佩恩(按名字「佩恩·天道」识别)血量跌破 bossRageThreshold(默 50%)时,
 * 一次性进入狂暴——攻击 +35%、移速 +25%(可配),全场血红演出:
 * 大字「狂暴」标题 + 怒焰螺旋 + 重震闪光 + 龙吼。已狂暴打怪物命令标签(yongye_raged),
 * 存档/卸重挂都不会二次触发;凤凰浴火重生回血后不会再触发(标签还在,阶段只走一次)。
 *
 * <p>触发挂 ALLOW_DAMAGE 观察者:用「扣完这刀后的血量」判越线,永远放行不改伤害。
 * 演出写法整段复用 BossEntranceFx(m263 已编);
 * 待编译验证:Entity#addCommandTag / getCommandTags(yarn 命令标签标准名,原版 /tag 同源,低险)。
 */
public final class BossRageHandler {
    private BossRageHandler() {}

    private static final String RAGE_TAG = "yongye_raged";
    private static final Identifier ID_ATK = Identifier.of(Yongye.MOD_ID, "boss_rage_atk");
    private static final Identifier ID_SPD = Identifier.of(Yongye.MOD_ID, "boss_rage_spd");

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableBossRage || amount <= 0) return true;
            if (!isRageBoss(entity)) return true;
            if (entity.getCommandTags().contains(RAGE_TAG)) return true;
            float after = entity.getHealth() - amount;
            if (after <= 0) return true; // 直接打死了,不演
            if (after > entity.getMaxHealth() * c.bossRageThreshold) return true;

            entity.addCommandTag(RAGE_TAG);
            buff(entity, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATK, c.bossRageAtkPct);
            buff(entity, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SPD, c.bossRageSpeedPct);
            if (entity.getWorld() instanceof ServerWorld world) playRageFx(world, entity);
            return true;
        });
        Yongye.LOGGER.info("[夜蚀] BOSS 半血狂暴已挂载");
    }

    /** 五只皮肤 BOSS 按类识别;佩恩是挂名 Husk,按名字字面量识别(PainBossHandler 同口径)。 */
    private static boolean isRageBoss(LivingEntity e) {
        if (e instanceof AnubisEntity || e instanceof ToroEnderDragonEntity
                || e instanceof FirePhoenixEntity || e instanceof DeathMageEntity
                || e instanceof RedSpiderEntity) return true;
        return e.hasCustomName() && "佩恩·天道".equals(e.getCustomName().getString());
    }

    private static void buff(LivingEntity e,
                             net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                             Identifier id, double pct) {
        EntityAttributeInstance inst = e.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (pct > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    id, pct, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** 狂暴演出:血红大字 + 怒焰螺旋 + 重震闪光 + 龙吼(整段照 BossEntranceFx 口径)。 */
    private static void playRageFx(ServerWorld world, LivingEntity boss) {
        double r2 = 48 * 48;
        Text title = Text.literal("狂 暴").formatted(Formatting.DARK_RED, Formatting.BOLD);
        Text sub = boss.getName().copy().formatted(Formatting.RED)
                .append(Text.literal(" 被激怒了 · 它不再留手").formatted(Formatting.GRAY));

        for (ServerPlayerEntity sp : world.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getWorld() != world) continue;
            if (sp.squaredDistanceTo(boss) > r2) continue;
            sp.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 35, 12));
            sp.networkHandler.sendPacket(new TitleS2CPacket(title));
            sp.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
            ServerPlayNetworking.send(sp, new CombatFxPayload(CombatFxPayload.HEAVY, 1.6f, 2.6f, true, false, 0));
            sp.playSoundToPlayer(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 0.9f, 0.8f);
        }

        // 怒焰双螺旋自地面盘升(火焰+愤怒粒子)+ 顶端爆开
        for (int i = 0; i < 40; i++) {
            double a = i * 0.55;
            double h = i * 0.13;
            double rad = Math.max(1.6, boss.getWidth() * 0.8);
            world.spawnParticles(ParticleTypes.FLAME,
                    boss.getX() + Math.cos(a) * rad, boss.getY() + h, boss.getZ() + Math.sin(a) * rad,
                    2, 0.05, 0.05, 0.05, 0.0);
            world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                    boss.getX() - Math.cos(a) * rad, boss.getY() + h, boss.getZ() - Math.sin(a) * rad,
                    1, 0.05, 0.05, 0.05, 0.0);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION,
                boss.getX(), boss.getY() + Math.max(1.0, boss.getHeight() * 0.6), boss.getZ(),
                4, 0.7, 0.9, 0.7, 0.0);
        world.spawnParticles(ParticleTypes.LAVA,
                boss.getX(), boss.getY() + boss.getHeight() * 0.5, boss.getZ(),
                24, boss.getWidth() * 0.6, boss.getHeight() * 0.4, boss.getWidth() * 0.6, 0.0);
    }
}
