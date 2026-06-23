package com.yongye.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 怪多时自动削减粒子(m117,减卡顿)。
 *
 * 纯客户端:每次生成粒子前,数一下客户端世界的实体总数,
 * 超过阈值就按比例随机丢弃粒子(实体越多丢得越狠),平滑降低渲染压力。
 * 不彻底关闭——保留部分粒子维持观感;实体少时完全不影响。
 *
 * 注入 ParticleManager#addParticle(返回 Particle 的重载)的 HEAD,
 * 命中丢弃条件则返回 null(不生成该粒子)并 cancel。
 *
 * 【性能】实体计数每若干 tick 缓存一次,不每个粒子都遍历世界(见 yongye$entityCount)。
 * 【待编译验证】ParticleManager#addParticle 重载签名(1.21.1);require=0 兜底。
 */
@Mixin(ParticleManager.class)
public class ParticleReducerMixin {

    // 实体数缓存:避免每个粒子都数一遍世界
    private static int yongye$cachedCount = 0;
    private static long yongye$lastCountTime = 0;

    // 阈值:实体数 ≤ SOFT 不削减;SOFT~HARD 线性增加丢弃率;≥HARD 丢弃 90%
    private static final int SOFT = 120;
    private static final int HARD = 400;

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$reduce(CallbackInfoReturnable<Particle> cir) {
        int count = yongye$entityCount();
        if (count <= SOFT) return; // 怪不多,不削减

        // 计算丢弃概率:SOFT→0%, HARD→90%
        float t = Math.min(1f, (float)(count - SOFT) / (HARD - SOFT));
        float dropChance = t * 0.9f;
        if (Math.random() < dropChance) {
            cir.setReturnValue(null); // 丢弃此粒子
        }
    }

    /** 客户端世界实体总数,每 500ms 缓存一次。 */
    private static int yongye$entityCount() {
        long now = System.currentTimeMillis();
        if (now - yongye$lastCountTime < 500) return yongye$cachedCount;
        yongye$lastCountTime = now;
        MinecraftClient mc = MinecraftClient.getInstance();
        int c = 0;
        if (mc.world != null) {
            for (Entity ignored : mc.world.getEntities()) c++;
        }
        yongye$cachedCount = c;
        return c;
    }
}
