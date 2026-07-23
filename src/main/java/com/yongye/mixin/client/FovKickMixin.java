package com.yongye.mixin.client;

import com.yongye.client.CombatFxManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 沉浸式战斗手感(m239):FOV 顿挫。
 * 命中/击杀瞬间视野轻微拉近(负偏移)再快速回弹,配合镜头微震形成"打中了"的体感。
 * 挂 GameRenderer.getFov 返回值上加偏移;无战斗时偏移恒 0 直接早退。
 * require = 0:方法名/签名不符则静默不挂,只丢顿挫效果不崩游戏。
 */
@Mixin(GameRenderer.class)
public abstract class FovKickMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void yongye$fovKick(Camera camera, float tickDelta, boolean changingFov,
                                CallbackInfoReturnable<Double> cir) {
        double off = CombatFxManager.fovOffset();
        if (off == 0.0) return;
        cir.setReturnValue(cir.getReturnValue() + off);
    }
}
