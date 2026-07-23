package com.yongye.mixin.client;

import com.yongye.client.CombatFxManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 沉浸式战斗手感(m239):镜头微震。
 * 在 Camera.update 末尾追加一次 setRotation(当前角 + 随机小偏移),
 * 偏移量来自 {@link CombatFxManager}(命中/击杀置入、每 tick 衰减),无战斗时恒 0 直接早退。
 * require = 0:目标方法名/签名若与运行时映射不符则本 mixin 静默不挂(只丢震屏效果,不崩游戏)。
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow public abstract float getYaw();
    @Shadow public abstract float getPitch();

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void yongye$shake(BlockView area, Entity focusedEntity, boolean thirdPerson,
                              boolean inverseView, float tickDelta, CallbackInfo ci) {
        float dy = CombatFxManager.shakeYaw();
        float dp = CombatFxManager.shakePitch();
        if (dy == 0f && dp == 0f) return;
        this.setRotation(this.getYaw() + dy, this.getPitch() + dp);
    }
}
