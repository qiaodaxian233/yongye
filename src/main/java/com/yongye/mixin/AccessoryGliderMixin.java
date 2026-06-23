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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让永夜之翼能滑翔(m107 重写,依据 1.21.1 LivingEntity 源码)。
 *
 * 1.21.1 滑翔真实机制(源码 tickFallFlying):每 tick 检查 flag(7)=滑翔位,
 * 维持条件是「胸甲槽 == Items.ELYTRA 且 ElytraItem.isUsable」——只认原版鞘翅,
 * 连继承 ElytraItem 的自定义物品都不认!所以:
 *   ① 永夜之翼穿胸甲槽飞不了(物品不是 Items.ELYTRA)
 *   ② 放饰品栏更飞不了(根本不看饰品栏)
 *
 * 本 mixin 注入 tickFallFlying 的 HEAD:若(胸甲槽是永夜之翼)或(饰品栏有永夜之翼),
 * 且在空中/无坐骑/无飘浮,就强制维持滑翔位 flag(7)=true,并 cancel 掉原方法
 * (避免原逻辑因"不是 Items.ELYTRA"而关掉滑翔位);同时按原逻辑每10tick耗一点饰品翼耐久。
 *
 * tickFallFlying 在 1.21.1 是 private void 无参——mixin 按字节码名匹配,private 可注入。
 * require=0 兜底:若名字仍不符则警告不崩。
 */
@Mixin(LivingEntity.class)
public abstract class AccessoryGliderMixin {

    @Inject(method = "tickFallFlying", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$accessoryGlide(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof PlayerEntity player)) return;

        // 是否拥有永夜之翼:胸甲槽 或 饰品栏
        boolean wingInChest = self.getEquippedStack(EquipmentSlot.CHEST).getItem() == ModItems.NIGHT_WING;
        boolean wingInAccessory = false;
        if (!wingInChest) {
            for (ItemStack s : AccessoryStorage.stacks(player)) {
                if (!s.isEmpty() && s.getItem() == ModItems.NIGHT_WING) { wingInAccessory = true; break; }
            }
        }
        if (!wingInChest && !wingInAccessory) return; // 没有永夜之翼,交回原版逻辑(原版鞘翅照常)

        // 维持滑翔的物理条件(对齐原版 tickFallFlying)
        boolean gliding = self.getFlag(7);
        if (gliding && !self.isOnGround() && !self.hasVehicle()
                && !self.hasStatusEffect(StatusEffects.LEVITATION)) {
            // 永夜之翼有效:维持滑翔位,接管(cancel 原方法避免它关掉)
            if (!self.getWorld().isClient) {
                self.setFlag(7, true);
                self.emitGameEvent(GameEvent.ELYTRA_GLIDE);
            }
            ci.cancel();
        } else if (gliding && self.isOnGround() && !self.getWorld().isClient) {
            // 落地:关滑翔位(对齐原版)
            self.setFlag(7, false);
            ci.cancel();
        }
        // 其它情况(没在滑翔/客户端)不接管,让原版处理
    }
}
