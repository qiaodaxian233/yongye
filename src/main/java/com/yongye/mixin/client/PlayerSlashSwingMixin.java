package com.yongye.mixin.client;

import com.yongye.client.SlashFxManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拔刀剑式攻击动画(m240):斩击轨迹的主触发点。
 * 钩本地攻击入口 MinecraftClient.doAttack 末尾——打实体和挥空都出斩击(拔刀的精髓就在挥空也有刀光),
 * 对方块=开始挖掘,不出。不用 @Shadow:直接 (MinecraftClient)(Object)this 走公共字段
 * (player/crosshairTarget 都是 public,javac 编译期就能校验,少一个运行时解析点)。
 * 「刚刚真的挥了一下」用 handSwinging && handSwingTicks<0 判(swingHand 置 -1,下 tick 才递增),
 * 冷却中/未挥手的 doAttack 早退分支天然不触发。
 * require = 0:方法名/签名与运行时映射不符则静默不挂——此时 AttackEntityCallback 兜底仍保证命中出轨迹。
 */
@Mixin(MinecraftClient.class)
public abstract class PlayerSlashSwingMixin {

    @Inject(method = "doAttack", at = @At("RETURN"), require = 0)
    private void yongye$slashOnAttack(CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.player == null) return;
        HitResult hit = mc.crosshairTarget;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) return; // 挖方块不出刀光
        if (!mc.player.handSwinging || mc.player.handSwingTicks >= 0) return; // 本次调用没真挥手(如攻击冷却)
        SlashFxManager.trySpawn(mc.player);
    }
}
