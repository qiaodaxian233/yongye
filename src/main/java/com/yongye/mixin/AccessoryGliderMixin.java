package com.yongye.mixin;

import com.yongye.registry.ModItems;
import com.yongye.system.AccessoryStorage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让永夜之翼能滑翔(m109 修 @Shadow 目标类)。
 *
 * 1.21.1 滑翔真实机制(LivingEntity 源码 tickFallFlying):滑翔=flag(7)位;
 * 每 tick 检查胸甲槽 isOf(Items.ELYTRA)——只认原版鞘翅,不认继承 ElytraItem 的自定义物品。
 * 本 mixin 注入 tickFallFlying HEAD:胸甲槽/饰品栏有永夜之翼时强制维持 flag(7),接管原方法。
 *
 * 修正历程:getFlag/setFlag 定义在 Entity(LivingEntity 父类),@Shadow 需目标类直接拥有,
 * 故 @Mixin 仍指 LivingEntity 但 mixin 类 extends Entity,@Shadow 方可解析到 Entity 的方法。
 * emitGameEvent(触发滑翔音效)非必需,去掉以省去 RegistryEntry<GameEvent> 类型麻烦。
 * require=0 兜底。
 */
@Mixin(LivingEntity.class)
public abstract class AccessoryGliderMixin extends Entity {

    // mixin 类继承具体父类需有构造转发(永不被实际调用,仅满足编译)
    private AccessoryGliderMixin(net.minecraft.entity.EntityType<?> type, net.minecraft.world.World world) {
        super(type, world);
    }

    @Shadow public abstract boolean getFlag(int index);
    @Shadow public abstract void setFlag(int index, boolean value);

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

        boolean gliding = this.getFlag(7);
        if (gliding && !self.isOnGround() && !self.hasVehicle()
                && !self.hasStatusEffect(StatusEffects.LEVITATION)) {
            if (!self.getWorld().isClient) this.setFlag(7, true); // 维持滑翔位
            ci.cancel();
        } else if (gliding && self.isOnGround() && !self.getWorld().isClient) {
            this.setFlag(7, false); // 落地关位
            ci.cancel();
        }
    }
}
