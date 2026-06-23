package com.yongye.mixin;

import com.yongye.registry.ModItems;
import com.yongye.system.AccessoryStorage;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.event.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让永夜之翼能滑翔(m108 修编译:protected 方法用 @Shadow)。
 *
 * 1.21.1 滑翔真实机制(LivingEntity 源码 tickFallFlying):滑翔=flag(7)位;
 * 每 tick 检查胸甲槽 isOf(Items.ELYTRA)——只认原版鞘翅,不认继承 ElytraItem 的自定义物品。
 * 本 mixin 注入 tickFallFlying HEAD:胸甲槽/饰品栏有永夜之翼时强制维持 flag(7),接管原方法。
 *
 * getFlag/setFlag/emitGameEvent 是 Entity 的 protected 方法,
 * mixin 中需用 @Shadow 声明后以 this 调用(直接 (LivingEntity)this 外部调会报 protected 不可访问)。
 * require=0 兜底。
 */
@Mixin(LivingEntity.class)
public abstract class AccessoryGliderMixin {

    @Shadow protected abstract boolean getFlag(int index);
    @Shadow protected abstract void setFlag(int index, boolean value);
    @Shadow public abstract void emitGameEvent(GameEvent event);

    @Inject(method = "tickFallFlying", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$accessoryGlide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity player)) return;

        // 是否拥有永夜之翼:胸甲槽 或 饰品栏
        boolean wing = self.getEquippedStack(EquipmentSlot.CHEST).getItem() == ModItems.NIGHT_WING;
        if (!wing) {
            for (ItemStack s : AccessoryStorage.stacks(player)) {
                if (!s.isEmpty() && s.getItem() == ModItems.NIGHT_WING) { wing = true; break; }
            }
        }
        if (!wing) return; // 没有永夜之翼,交回原版逻辑(原版鞘翅照常)

        boolean gliding = this.getFlag(7);
        if (gliding && !self.isOnGround() && !self.hasVehicle()
                && !self.hasStatusEffect(StatusEffects.LEVITATION)) {
            // 永夜之翼有效:维持滑翔位,接管(cancel 原方法避免它因非原版鞘翅关掉)
            if (!self.getWorld().isClient) {
                this.setFlag(7, true);
                this.emitGameEvent(GameEvent.ELYTRA_GLIDE);
            }
            ci.cancel();
        } else if (gliding && self.isOnGround() && !self.getWorld().isClient) {
            this.setFlag(7, false); // 落地关滑翔位
            ci.cancel();
        }
    }
}
