package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.item.ChaosBladeItem;
import com.yongye.item.ClassWeaponItem;
import com.yongye.network.CombatFxPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 蓄力重斩(m257)——学 Epic Fight 的「按住派生」设计:按住攻击键蓄力,松开放出前方锥形重斩。
 * 客户端 ChargeSlashManager 负责计时与手感反馈,本类只做服务端结算:
 *  - 校验:主手武器(本模组武器 / 原版剑斧戟且命名空间 minecraft,与刀光口径一致)+ 冷却 + tick 服务端钳制(防包造假);
 *  - 伤害 = 攻击力 × 按蓄力时长在 [multMin, multMax] 线性插值;锥形判定与混沌斩同款(dot≥0.35);
 *  - 演出 = 复用 m255 的剑气月牙推进(WeaponSkillFx.chaosSlash)+ 施放者重震 + 重音。
 * 蓄力上限拉不满也能放(≥minTicks 即可),鼓励节奏取舍——满蓄伤害更高但硬直窗口更长。
 */
public final class ChargeSlashHandler {
    private ChargeSlashHandler() {}

    private static final Map<UUID, Long> COOLDOWN_UNTIL = new HashMap<>();

    public static void perform(ServerPlayerEntity player, int chargeTicks) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableChargeSlash) return;
        if (!(player.getWorld() instanceof ServerWorld world)) return;
        if (!weaponOk(player.getMainHandStack())) return;

        long now = player.server.getTicks();
        long until = COOLDOWN_UNTIL.getOrDefault(player.getUuid(), 0L);
        if (now < until) {
            int left = (int) Math.ceil((until - now) / 20.0);
            player.sendMessage(Text.literal("【蓄力重斩】冷却中 " + left + "s").formatted(Formatting.RED), true);
            return;
        }
        COOLDOWN_UNTIL.put(player.getUuid(), now + Math.max(0, cfg.chargeSlashCooldownTicks));

        // 蓄力强度:服务端自行钳制客户端上报的 tick(防造假),线性插值伤害倍率
        int min = Math.max(1, cfg.chargeSlashMinTicks);
        int max = Math.max(min + 1, cfg.chargeSlashMaxTicks);
        double tNorm = MathHelper.clamp((chargeTicks - min) / (double) (max - min), 0.0, 1.0);
        double mult = cfg.chargeSlashDamageMultMin
                + (cfg.chargeSlashDamageMultMax - cfg.chargeSlashDamageMultMin) * tNorm;
        double dmg = player.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE) * mult;

        double range = Math.max(1.0, cfg.chargeSlashRange);
        Vec3d look = player.getRotationVector().normalize();
        Vec3d eye = player.getEyePos();
        DamageSource src = world.getDamageSources().playerAttack(player);
        int hit = 0;
        Box box = player.getBoundingBox().expand(range);
        for (LivingEntity le : world.getEntitiesByClass(LivingEntity.class, box,
                e -> e.isAlive() && e != player && (e instanceof Monster || e.getAttachedOrElse(com.yongye.registry.ModAttachments.IS_ELITE, false)))) {
            Vec3d to = le.getPos().subtract(eye).normalize();
            if (look.dotProduct(to) < 0.35) continue; // 仅前方扇形(与混沌斩同款)
            le.damage(src, (float) dmg);
            le.takeKnockback(1.2 + tNorm * 1.2, -look.x, -look.z);
            hit++;
        }

        // 演出:剑气月牙推进(m255)+ 重震 + 重音;满蓄音调更低更狠
        WeaponSkillFx.chaosSlash(world, player, range);
        ServerPlayNetworking.send(player, new CombatFxPayload(CombatFxPayload.HEAVY,
                (float) (1.0 + tNorm * 0.8), (float) (2.2 + tNorm * 1.6), false, false));
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.1f, (float) (0.9 - tNorm * 0.3));
        player.sendMessage(Text.literal("蓄力重斩 ×" + String.format("%.1f", mult) + (hit > 0 ? "(命中 " + hit + ")" : "(落空)"))
                .formatted(tNorm >= 0.999 ? Formatting.GOLD : Formatting.YELLOW), true);
    }

    /** 武器判定:与刀光(SlashFxManager.eligible)同一口径的服务端版。 */
    private static boolean weaponOk(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        Item it = st.getItem();
        if (it instanceof ClassWeaponItem || it instanceof ChaosBladeItem) return true;
        if (!(it instanceof SwordItem || it instanceof AxeItem || it instanceof TridentItem)) return false;
        return "minecraft".equals(Registries.ITEM.getId(it).getNamespace());
    }
}
