package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.item.PlayerClass;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;

import java.util.Map;

/**
 * 武僧单独成长系统(m103)——「越吃越肥越能打」。
 *
 * 武僧不用武器、不强化装备:空手作战,靠「吞噬」永夜材料直接强化自身——
 * 右键吃下材料 → 永久 +拳击伤害 +生命上限(按材料稀有度给量)。
 * 拳击/生命加成存 MONK_FIST_BONUS / MONK_HP_BONUS 附件,由 ClassManager 应用为属性。
 *
 * 与其他职业的区别:别的职业把材料拿去合成/强化装备,武僧直接吃。
 */
public final class MonkSystem {
    private MonkSystem() {}

    /** 材料 → {拳击伤害加成, 生命上限加成}(按稀有度递增)。 */
    private static Map<Item, int[]> eatTable() {
        return Map.of(
                ModItems.LIFE_SHARD,            new int[]{1, 2},    // 碎片:小
                ModItems.LIFE_CRYSTAL,          new int[]{2, 6},    // 结晶
                ModItems.LIFE_CORE,             new int[]{5, 16},   // 核心
                ModItems.CATASTROPHE_BLOOD_CORE,new int[]{10, 30},  // 血核
                ModItems.ENDLESS_NIGHT_DUST,    new int[]{6, 12},   // 永夜尘
                ModItems.RIFT_FRAGMENT,         new int[]{8, 20},   // 裂隙碎片
                ModItems.ABYSS_SOUL_CRYSTAL,    new int[]{12, 24},  // 深渊魂晶
                ModItems.ENDING_ESSENCE,        new int[]{20, 50}   // 终焉精华:最强
        );
    }

    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            // 仅服务端、仅武僧、仅主手、仅可吃材料
            if (world.isClient || hand != Hand.MAIN_HAND) return TypedActionResult.pass(stack);
            if (!(player instanceof ServerPlayerEntity p)) return TypedActionResult.pass(stack);
            if (!ClassManager.isActive(p, PlayerClass.MONK)) return TypedActionResult.pass(stack);

            int[] gain = eatTable().get(stack.getItem());
            if (gain == null) return TypedActionResult.pass(stack);

            // 吃下:消耗 1 个,累加拳击/生命
            stack.decrement(1);
            int fist = p.getAttachedOrElse(ModAttachments.MONK_FIST_BONUS, 0) + gain[0];
            int hp   = p.getAttachedOrElse(ModAttachments.MONK_HP_BONUS, 0) + gain[1];
            p.setAttached(ModAttachments.MONK_FIST_BONUS, fist);
            p.setAttached(ModAttachments.MONK_HP_BONUS, hp);

            // 反馈:粒子+音效+提示
            if (world instanceof ServerWorld sw) {
                sw.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                sw.spawnParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            }
            world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_PLAYER_BURP,
                    SoundCategory.PLAYERS, 1.0f, 0.8f);
            p.sendMessage(Text.literal("吞噬!拳击 +" + fist + " · 生命 +" + hp)
                    .formatted(Formatting.GOLD), true);

            return TypedActionResult.success(stack, false);
        });
        Yongye.LOGGER.info("[永夜] 武僧吞噬系统已挂载");
    }
}
