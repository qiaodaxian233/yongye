package com.yongye.mixin;

import com.yongye.system.ForeignDamageFilterHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * m195:怪物「因外模组来源死亡」拦截——直接挂 {@code LivingEntity#onDeath}。
 *
 * <p>为什么非 mixin 不可:Fabric 的 {@code ServerLivingEntityEvents.ALLOW_DEATH} 只在 {@code damage()} 的
 * 致死判定处触发。AvaritiaNeo 无限剑的击杀链是 {@code hurt(MAX); setHealth(0); die();}——
 * 头一句被 ForeignDamageFilterHandler 的 ALLOW_DAMAGE 取消(于是走不到 damage() 的致死判定,ALLOW_DEATH 不触发),
 * 后两句 {@code setHealth(0)+die()} 又完全绕过 {@code damage()}。所有死亡最终都汇流到 {@code onDeath()},
 * 故在这里 HEAD 拦截:外来致死就把血抬回 + 取消死亡。
 *
 * <p>安全:{@code require = 0}——若 onDeath 在本映射里名字/签名不符(沙箱无法编译验证),此注入器会「找不到目标」
 * 而被静默跳过,<b>不会导致启动崩溃</b>;届时看日志是否挂上即可。判定与回血逻辑集中在
 * {@link ForeignDamageFilterHandler#tryBlockForeignDeath}。
 */
@Mixin(LivingEntity.class)
public abstract class MonsterDeathGuardMixin {

    @Inject(method = "onDeath", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$blockForeignMonsterDeath(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (ForeignDamageFilterHandler.tryBlockForeignDeath(self, source)) {
            ci.cancel();   // 已回血,取消这次死亡
        }
    }
}
