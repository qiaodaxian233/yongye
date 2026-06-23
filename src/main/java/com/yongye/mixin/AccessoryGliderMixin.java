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
 * 让永夜之翼能滑翔(m111 性能优化版)。
 *
 * 1.21.1 滑翔机制(LivingEntity#tickFallFlying):滑翔=flag(7)位;只认胸甲槽 Items.ELYTRA。
 * 本 mixin 让永夜之翼(胸甲槽或饰品栏)也能维持滑翔。
 *
 * 【性能优化】tickFallFlying 是所有 LivingEntity 每 tick 调用的热点方法。
 * 用层层廉价早退,把昂贵的饰品栏 NBT 读取(AccessoryStorage.stacks)推到最后,
 * 仅当「玩家 + 已在滑翔位 + 在空中 + 服务端」时才读——绝大多数 tick(走路/站立/怪物)
 * 在前几步就 return,不碰 NBT。避免对每个实体每 tick 反序列化饰品栏导致的卡顿。
 */
@Mixin(LivingEntity.class)
public abstract class AccessoryGliderMixin {

    @Inject(method = "tickFallFlying", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$accessoryGlide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        // 廉价早退 1:非玩家(怪物/动物)直接走原版,不进任何额外逻辑
        if (!(self instanceof PlayerEntity player)) return;
        // 廉价早退 2:仅服务端处理(避免客户端重复读 NBT)
        if (self.getWorld().isClient) return;

        EntityFlagInvoker flags = (EntityFlagInvoker) self;
        boolean gliding = flags.yongye$getFlag(7);
        // 廉价早退 3:没在滑翔状态就不管(走路/站立的玩家在此 return,不读饰品栏)
        if (!gliding) return;

        // 此时:服务端 + 玩家 + 已在滑翔位。才检查是否拥有永夜之翼。
        boolean wing = self.getEquippedStack(EquipmentSlot.CHEST).getItem() == ModItems.NIGHT_WING;
        if (!wing) {
            for (ItemStack s : AccessoryStorage.stacks(player)) {  // 仅滑翔中的玩家才读 NBT
                if (!s.isEmpty() && s.getItem() == ModItems.NIGHT_WING) { wing = true; break; }
            }
        }
        if (!wing) return; // 没永夜之翼,交回原版(穿原版鞘翅照常)

        // 永夜之翼有效:在空中无坐骑无飘浮→维持滑翔位;落地→关位。均接管原方法。
        if (!self.isOnGround() && !self.hasVehicle()
                && !self.hasStatusEffect(StatusEffects.LEVITATION)) {
            flags.yongye$setFlag(7, true);
            ci.cancel();
        } else if (self.isOnGround()) {
            flags.yongye$setFlag(7, false);
            ci.cancel();
        }
    }
}
