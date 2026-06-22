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
}
