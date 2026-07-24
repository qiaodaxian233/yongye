package com.yongye.mixin;

import com.yongye.YongyeConfig;
import com.yongye.item.BlightArmorItem;
import com.yongye.registry.ModComponents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 夜蚀装备·掉落物不可摧毁(m281,作者:「装备无法被破坏」):
 *  ① 免伤——ItemEntity.damage 对灵魂绑定物一律取消:火/岩浆/爆炸/仙人掌全免
 *    (yarn 1.21.1 damage=Entity.method_5643 的覆写,注入按名解析);
 *  ② 永不消失——setNeverDespawn(官方 mapping method_35190 已核)把年龄钉死,5 分钟自然消失不再发生;
 *  ③ 虚空营救——掉到世界底 32 格以下(抢在原版虚空销毁线之前):主人在线=直接塞回主人背包(满则在主人脚下掉出,
 *    掉出的这份照样受本保护),主人不在线/未认主=钉在虚空边缘悬浮(无重力+清速度)等主人来取。
 * 定时清理豁免在 ItemCleanupHandler,怪物拾取拦截在 SoulboundMobLootMixin,精英缴械豁免在 EliteHandler。
 */
@Mixin(ItemEntity.class)
public abstract class SoulboundItemGuardMixin {

    @Shadow public abstract ItemStack getStack();

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void yongye$noDestroy(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getWorld().isClient) return;
        if (!YongyeConfig.get().blightArmorIndestructible) return;
        if (!BlightArmorItem.isSoulbound(getStack())) return;
        cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void yongye$keepAlive(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getWorld().isClient) return;
        if (!YongyeConfig.get().blightArmorIndestructible) return;
        if (!BlightArmorItem.isSoulbound(getStack())) return;
        if (self.getItemAge() >= 0) self.setNeverDespawn();   // 永不消失(只需钉一次)

        // 虚空营救
        if (self.getY() < self.getWorld().getBottomY() - 32 && self.getWorld() instanceof ServerWorld sw) {
            ServerPlayerEntity owner = null;
            String tag = getStack().get(ModComponents.BLIGHT_OWNER);
            if (tag != null) {
                int cut = tag.indexOf('|');
                try {
                    owner = sw.getServer().getPlayerManager()
                            .getPlayer(java.util.UUID.fromString(cut >= 0 ? tag.substring(0, cut) : tag));
                } catch (IllegalArgumentException ignored) {}
            }
            if (owner != null) {
                ItemStack s = getStack().copy();
                self.discard();
                if (!owner.getInventory().insertStack(s) && !s.isEmpty()) owner.dropItem(s, false);
                owner.sendMessage(Text.literal("【夜蚀】" + s.getName().getString() + " 挣脱虚空,回到了你身边")
                        .formatted(Formatting.LIGHT_PURPLE), true);
            } else {
                // 主人不在线/未认主:钉在虚空边缘悬浮等主人(不消失、免伤,来了照常只有主人捡得起)
                self.setNoGravity(true);
                self.setVelocity(Vec3d.ZERO);
                self.setPosition(self.getX(), self.getWorld().getBottomY() + 1.0, self.getZ());
            }
        }
    }
}
