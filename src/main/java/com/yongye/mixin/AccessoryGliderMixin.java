package com.yongye.mixin;

import com.yongye.registry.ModItems;
import com.yongye.system.AccessoryStorage;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让「永夜之翼」放在饰品栏也能滑翔(m102)。
 *
 * 原理:玩家每 tick 的滑翔检查(checkGliding)会看胸甲槽是否有可滑翔物品。
 * 本 mixin 注入该检查,若饰品栏里有永夜之翼,就让检查通过(返回 true),
 * 从而即使胸甲槽没穿鞘翅也能滑翔。
 *
 * 【待编译验证·高】1.21.1 玩家滑翔检查的方法名不确定,可能是:
 *   checkGliding / checkFallFlying / 等。本 mixin 用 require=0 兜底——
 *   若方法名不符,注入器静默跳过、不崩游戏;届时看日志/实测是否生效,
 *   报错则把 LivingEntity/PlayerEntity 里滑翔检查的实际方法名告诉我,改 method= 即可。
 */
@Mixin(PlayerEntity.class)
public abstract class AccessoryGliderMixin {

    @Inject(method = "checkGliding", at = @At("RETURN"), cancellable = true, require = 0)
    private void yongye$accessoryGlide(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return; // 已能滑翔(穿了鞘翅),不干预
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient) {
            // 客户端也要判,否则视觉不同步;直接读饰品附件
        }
        for (ItemStack s : AccessoryStorage.stacks(self)) {
            if (!s.isEmpty() && s.getItem() == ModItems.NIGHT_WING) {
                cir.setReturnValue(true); // 饰品栏有永夜之翼 → 允许滑翔
                return;
            }
        }
    }
}
