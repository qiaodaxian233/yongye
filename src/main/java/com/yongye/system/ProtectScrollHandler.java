package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 强化保护卷的获取(m159)。敌对怪被玩家击杀时:
 *   1) 低概率(protectScrollDropChance)直接掉一张保护卷;
 *   2) 给击杀者累计击杀数,达阈值自动兑换 1 张——首张需 protectScrollKillBase(默2000)击杀,
 *      每兑换 1 张后阈值翻倍(2000→4000→8000…)。
 * 阈值超过 int 上限后(约 20 次兑换、累计数十亿击杀)实际不再可达,属可接受上限。
 */
public final class ProtectScrollHandler {
    private ProtectScrollHandler() {}

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableProtectScroll) return;
            if (!(entity instanceof Monster)) return;
            if (!(entity.getWorld() instanceof ServerWorld world)) return;
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;

            // 1) 低概率直接掉落
            if (world.getRandom().nextDouble() < c.protectScrollDropChance) {
                ItemEntity ie = new ItemEntity(world, entity.getX(), entity.getY() + 0.5, entity.getZ(),
                        new ItemStack(ModItems.ENHANCE_PROTECT_SCROLL));
                ie.setToDefaultPickupDelay();
                world.spawnEntity(ie);
            }

            // 2) 杀怪累计兑换(阈值随兑换次数翻倍)
            int kills = killer.getAttachedOrElse(ModAttachments.SCROLL_KILLS, 0) + 1;
            int exchanges = killer.getAttachedOrElse(ModAttachments.SCROLL_EXCHANGES, 0);
            long threshold = (long) c.protectScrollKillBase << Math.min(exchanges, 30); // base × 2^exchanges
            if (threshold > 0 && kills >= threshold) {
                kills -= (int) threshold;
                exchanges++;
                killer.getInventory().offerOrDrop(new ItemStack(ModItems.ENHANCE_PROTECT_SCROLL));
                killer.setAttached(ModAttachments.SCROLL_EXCHANGES, exchanges);
                long next = (long) c.protectScrollKillBase << Math.min(exchanges, 30);
                killer.getWorld().playSound(null, killer.getX(), killer.getY(), killer.getZ(),
                        SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.6f);
                killer.sendMessage(Text.literal("击杀达标!获得 强化保护卷 ×1(下一张需 " + next + " 击杀)")
                        .formatted(Formatting.LIGHT_PURPLE), true);
            }
            killer.setAttached(ModAttachments.SCROLL_KILLS, kills);
        });
    }
}
