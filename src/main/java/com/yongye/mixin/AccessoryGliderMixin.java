package com.yongye.mixin;

import com.yongye.registry.ModItems;
import com.yongye.system.AccessoryStorage;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让「永夜之翼」放在饰品栏也能滑翔(m106 修正 m102)。
 *
 * m102 mixin 了 PlayerEntity#checkGliding —— 该方法名在 1.21.1 不存在,
 * build 警告"Unable to determine descriptor",注入未生效(require=0 未崩但失效)。
 *
 * 1.21.1 yarn 中,实体能否滑翔的判定方法是 LivingEntity#canGlide()(返回 boolean)。
 * 本 mixin 注入它的 RETURN:若玩家饰品栏有永夜之翼,放行滑翔。
 *
 * 【待编译验证】若仍报"Unable to determine descriptor",说明 1.21.1 此方法名仍不符;
 * 届时把 LivingEntity 里"判断能否滑翔/起滑"的实际方法名告诉我(候选:canGlide/
 * checkGliding/wantsToGlide/tickFallFlying 等)。require=0 兜底,警告不影响 build。
 */
@Mixin(LivingEntity.class)
public abstract class AccessoryGliderMixin {

    @Inject(method = "canGlide", at = @At("RETURN"), cancellable = true, require = 0)
    private void yongye$accessoryGlide(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // 已能滑翔,不干预
        if (!((Object) this instanceof PlayerEntity self)) return;
        for (ItemStack s : AccessoryStorage.stacks(self)) {
            if (!s.isEmpty() && s.getItem() == ModItems.NIGHT_WING) {
                cir.setReturnValue(true);
                return;
            }
        }
    }
}
