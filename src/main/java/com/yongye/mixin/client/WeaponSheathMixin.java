package com.yongye.mixin.client;

import com.yongye.client.WeaponBackFeatureRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 疾跑收刀·藏手侧(m247)——{@code WeaponBackFeatureRenderer} 把武器画到背后的同时,
 * 这里把第三人称手上那份取消掉,条件共用 shouldSheath,不会手背两把。
 * 只藏主手臂;副手(盾等)照常。require=0:注入点对不上就静默失效,
 * 最坏是「背上一把手上一把」的纯观感问题,不崩游戏(照 m239/m240 mixin 保险口径)。
 */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class WeaponSheathMixin {

    @Inject(method = "renderItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$sheathHand(LivingEntity entity, ItemStack stack, ModelTransformationMode transformationMode,
                                   Arm arm, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                                   CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayerEntity p
                && arm == p.getMainArm()
                && WeaponBackFeatureRenderer.shouldSheath(p)) {
            ci.cancel();
        }
    }
}
