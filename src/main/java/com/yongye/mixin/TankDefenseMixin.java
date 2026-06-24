package com.yongye.mixin;

import com.yongye.YongyeConfig;
import com.yongye.item.PlayerClass;
import com.yongye.system.ClassManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 坦克「真·百分比减伤」:注入 LivingEntity#modifyAppliedDamage 的返回值,
 * 对当前生效的坦克玩家,把最终承受伤害按 tankTrueDamageReduction 比例削减。
 *
 * 安全说明:require = 0 —— 若该方法在本映射里名字/签名不符(沙箱无法编译验证),
 * 此注入器会"找不到目标"而被静默跳过,**不会导致游戏启动崩溃**;届时看日志确认是否挂上即可。
 * 写法:@Inject + RETURN + cancellable + cir.setReturnValue(标准返回值注入)。
 */
@Mixin(LivingEntity.class)
public abstract class TankDefenseMixin {

    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true, require = 0)
    private void yongye$tankDamageReduction(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ServerPlayerEntity p)) return;
        double red = YongyeConfig.get().tankTrueDamageReduction;
        if (red <= 0) return;
        if (!ClassManager.isActive(p, PlayerClass.TANK)) return;
        float reduced = (float) (cir.getReturnValue() * (1.0 - Math.min(0.9, red)));
        cir.setReturnValue(Math.max(0.0f, reduced));
    }
}
