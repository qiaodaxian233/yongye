package com.yongye.mixin.client;

import com.yongye.client.CombatFxManager;
import com.yongye.client.SoundGate;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 音效并发管理器(m419):拦客户端声音系统唯一起播口 SoundSystem.play(SoundInstance),
 * 交 SoundGate 判限流/预算/ducking,拒了就取消——本地音、服务端 PlaySound 包、粒子附带音
 * 全从这一个口过,一处管全部。延迟播放(play(sound,delay))到点后同样走本方法,天然覆盖。
 * require = 0:方法名/签名不符则静默不挂,只丢并发管理不崩游戏(m248 探针可查存活)。
 */
@Mixin(SoundSystem.class)
public abstract class SoundGateMixin {

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$soundGate(SoundInstance sound, CallbackInfo ci) {
        CombatFxManager.markInjected("SoundGate(音效并发)");
        if (sound == null) return;
        if (!SoundGate.allow(sound)) ci.cancel();
    }
}
