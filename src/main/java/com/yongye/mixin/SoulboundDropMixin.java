package com.yongye.mixin;

import com.yongye.YongyeConfig;
import com.yongye.item.BlightArmorItem;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModComponents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 夜蚀套装·死亡不掉落(m265):玩家死亡掉背包(dropInventory)之前,
 * 把带 BLIGHT_OWNER 组件 / 夜蚀盔甲从背包截走,存进 SOULBOUND_STASH 附件
 * (persistent + copyOnDeath,Fabric 附件持久化走 RegistryOps 可安全序列化 ItemStack);
 * 重生时在 Yongye 的 AFTER_RESPAWN 里原样归还。
 * 挂 LivingEntity.dropInventory(yarn 1.21.1 官方 mapping method_16078 已核)+ instanceof 门,
 * keepInventory 开着时该方法不会被调,天然兼容。
 */
@Mixin(LivingEntity.class)
public abstract class SoulboundDropMixin {

    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void yongye$stashSoulbound(CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayerEntity player)) return;
        if (!YongyeConfig.get().blightArmorSoulbound) return;
        List<ItemStack> kept = new ArrayList<>();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.get(ModComponents.BLIGHT_OWNER) == null && !(s.getItem() instanceof BlightArmorItem)) continue;
            kept.add(s.copy());
            inv.setStack(i, ItemStack.EMPTY);
        }
        if (kept.isEmpty()) return;
        List<ItemStack> merged = new ArrayList<>(
                player.getAttachedOrElse(ModAttachments.SOULBOUND_STASH, new ArrayList<>()));
        merged.addAll(kept);
        player.setAttached(ModAttachments.SOULBOUND_STASH, merged);
    }
}
