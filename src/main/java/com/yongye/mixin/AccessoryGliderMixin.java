package com.yongye.mixin;

import com.yongye.registry.ModItems;
import com.yongye.system.AccessoryStorage;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让永夜之翼能滑翔(m110 修 m109 崩溃)。
 *
 * 1.21.1 滑翔机制(LivingEntity 源码 tickFallFlying):滑翔=flag(7)位;每 tick 检查胸甲槽
 * isOf(Items.ELYTRA)——只认原版鞘翅。本 mixin 注入 tickFallFlying HEAD,胸甲槽/饰品栏有
 * 永夜之翼时强制维持 flag(7),接管原方法。
 *
 * m109 崩溃根因:getFlag/setFlag 定义在 Entity(非 LivingEntity),@Shadow 运行时严格在
 * @Mixin 目标类(LivingEntity)里找、不沿继承链 → "method_5795 not located in class_1309"。
 * 修:改用 EntityFlagInvoker(@Mixin(Entity)+@Invoker)暴露这两个方法,把 this 转成该接口调用。
 * require=0 兜底。
 */
@Mixin(LivingEntity.class)
public abstract class AccessoryGliderMixin {

    @Inject(method = "tickFallFlying", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$accessoryGlide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity player)) return;

        boolean wing = self.getEquippedStack(EquipmentSlot.CHEST).getItem() == ModItems.NIGHT_WING;
        if (!wing) {
            for (ItemStack s : AccessoryStorage.stacks(player)) {
                if (!s.isEmpty() && s.getItem() == ModItems.NIGHT_WING) { wing = true; break; }
            }
        }
        if (!wing) return; // 没永夜之翼,交回原版逻辑

        EntityFlagInvoker flags = (EntityFlagInvoker) self;
        boolean gliding = flags.yongye$getFlag(7);
        if (gliding && !self.isOnGround() && !self.hasVehicle()
                && !self.hasStatusEffect(StatusEffects.LEVITATION)) {
            if (!self.getWorld().isClient) flags.yongye$setFlag(7, true); // 维持滑翔位
            ci.cancel();
        } else if (gliding && self.isOnGround() && !self.getWorld().isClient) {
            flags.yongye$setFlag(7, false); // 落地关位
            ci.cancel();
        }
    }
}
