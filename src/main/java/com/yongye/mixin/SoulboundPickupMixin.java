package com.yongye.mixin;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModComponents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 夜蚀套装·只有主人捡得起(m265):掉在地上的认主物品,
 * 非主人玩家碰撞直接取消整个拾取流程(物品原地不动留给主人)。
 * 挂 ItemEntity.onPlayerCollision(Entity 声明的拾取入口,yarn 1.21.1 method_5694 已核)。
 */
@Mixin(ItemEntity.class)
public abstract class SoulboundPickupMixin {

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void yongye$ownerOnlyPickup(PlayerEntity player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getWorld().isClient) return;
        if (!YongyeConfig.get().blightArmorSoulbound) return;
        String tag = self.getStack().get(ModComponents.BLIGHT_OWNER);
        if (tag == null) return;
        int cut = tag.indexOf('|');
        String uuid = cut >= 0 ? tag.substring(0, cut) : tag;
        if (uuid.equals(player.getUuid().toString())) return;
        ci.cancel();
    }
}
