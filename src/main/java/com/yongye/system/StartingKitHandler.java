package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 开局赠礼:每个玩家首次进入(每人仅一次)发一个下界合金背包。
 *
 * 软依赖说明:Sophisticated Backpacks 是独立 mod,这里**不硬依赖**它——
 * 按字符串 id 在物品注册表里查,查到才发,查不到(未装该 mod / id 不对)就静默跳过、不报错、不崩。
 * 未发成功时不打"已领取"标记,这样玩家日后装上该 mod 再登录仍能补发。
 */
public final class StartingKitHandler {
    private StartingKitHandler() {}

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.player;
            YongyeConfig cfg = YongyeConfig.get();

            // 开局口粮(m143):每人首次进入发 N 个面包;独立开关/标记,放在背包逻辑之前
            // (背包那段在未装 Sophisticated Backpacks 时会 return,放后面会被一起跳过)
            if (cfg.giveStartingFood && cfg.startingFoodCount > 0
                    && !p.getAttachedOrElse(ModAttachments.GOT_STARTING_FOOD, false)) {
                int remain = cfg.startingFoodCount;
                while (remain > 0) {
                    int n = Math.min(remain, 64);
                    p.giveItemStack(new ItemStack(Items.BREAD, n));   // 64 一组,超出自动拆叠
                    remain -= n;
                }
                p.setAttached(ModAttachments.GOT_STARTING_FOOD, true);
            }

            // 开局背包升级(m154):高级磁铁 + 高级喂食,各发一个;独立标记,老玩家下次登录也能补发。
            // 软依赖:按 id 查到才发,查不到(未装 Sophisticated Backpacks / id 写错)静默跳过;两个都没发成功才不打标记。
            if (cfg.giveStartingUpgrades
                    && !p.getAttachedOrElse(ModAttachments.GOT_STARTING_UPGRADES, false)) {
                boolean gaveMagnet  = giveById(p, cfg.startingMagnetUpgradeItem);
                boolean gaveFeeding = giveById(p, cfg.startingFeedingUpgradeItem);
                if (gaveMagnet || gaveFeeding) {
                    p.setAttached(ModAttachments.GOT_STARTING_UPGRADES, true);
                }
            }

            if (!cfg.giveStartingBackpack) return;
            if (p.getAttachedOrElse(ModAttachments.GOT_STARTING_KIT, false)) return; // 每人只发一次

            Identifier id = Identifier.tryParse(cfg.startingBackpackItem);
            Item item = id != null ? Registries.ITEM.get(id) : null;
            if (item == null || item == Items.AIR) {
                // 物品未找到(未装 Sophisticated Backpacks 或 id 不对):静默跳过,不打标记(装上后下次登录可补发)
                return;
            }
            p.giveItemStack(new ItemStack(item));
            p.setAttached(ModAttachments.GOT_STARTING_KIT, true);
        });
        Yongye.LOGGER.info("[永夜] 开局赠礼系统已挂载");
    }

    /** 软依赖按 id 发一个物品:解析 id → 注册表查 → 查到且非空气则发一个并返回 true,否则返回 false(静默,不崩)。 */
    private static boolean giveById(ServerPlayerEntity p, String idStr) {
        Identifier id = Identifier.tryParse(idStr);
        Item item = id != null ? Registries.ITEM.get(id) : null;
        if (item == null || item == Items.AIR) return false;
        p.giveItemStack(new ItemStack(item));
        return true;
    }
}
