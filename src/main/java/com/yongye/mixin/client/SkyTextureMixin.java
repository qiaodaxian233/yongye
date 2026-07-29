package com.yongye.mixin.client;

import com.yongye.client.YongyeClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 事件限定天象视觉(m352,作者:「血月才红月、酸雨才绿雨,避免贴图常驻导致天天血月」)。
 * 病根=红月/绿雨此前放在 assets/minecraft 常驻覆盖原版贴图,与服务端天象事件完全脱钩;
 * 本笔把贴图挪进 yongye 命名空间,原版贴图回归,这里在渲染点按事件状态**运行时换贴图**:
 *  - renderSky 里 MOON_PHASES 静态字段读取(yarn 1.21.1 已核 field_4098 / method_3257)
 *    → 血月事件中换 yongye:textures/environment/blood_moon_phases.png;
 *  - renderWeather 里 RAIN 静态字段读取(已核 field_20797 / method_22714)
 *    → 酸雨事件中换 yongye:textures/environment/acid_rain.png(雪贴图不动)。
 * 事件状态由 SkyEventPayload 写进 YongyeClient.skyEvent(0无/1血月/2酸雨/3流星);
 * enableEventSkyVisuals 关 = 恒用原版贴图。require=0:映射不符则静默不挂,
 * 退化为原版月亮/雨(玩法层血月/酸雨照常),永不崩。
 */
@Mixin(WorldRenderer.class)
public abstract class SkyTextureMixin {

    private static final Identifier YONGYE$BLOOD_MOON =
            Identifier.of("yongye", "textures/environment/blood_moon_phases.png");
    private static final Identifier YONGYE$ACID_RAIN =
            Identifier.of("yongye", "textures/environment/acid_rain.png");
    // 原版贴图路径(MOON_PHASES/RAIN 字段是 private,不直引;路径与原版常量逐字一致)
    private static final Identifier YONGYE$VANILLA_MOON =
            Identifier.of("minecraft", "textures/environment/moon_phases.png");
    private static final Identifier YONGYE$VANILLA_RAIN =
            Identifier.of("minecraft", "textures/environment/rain.png");

    @Redirect(method = "renderSky",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/render/WorldRenderer;MOON_PHASES:Lnet/minecraft/util/Identifier;"),
            require = 0)
    private Identifier yongye$moonTexture() {
        if (YongyeClient.skyEvent == 1 && com.yongye.YongyeConfig.get().enableEventSkyVisuals) {
            return YONGYE$BLOOD_MOON;
        }
        return YONGYE$VANILLA_MOON;
    }

    @Redirect(method = "renderWeather",
            at = @At(value = "FIELD",
                    target = "Lnet/minecraft/client/render/WorldRenderer;RAIN:Lnet/minecraft/util/Identifier;"),
            require = 0)
    private Identifier yongye$rainTexture() {
        if (YongyeClient.skyEvent == 2 && com.yongye.YongyeConfig.get().enableEventSkyVisuals) {
            return YONGYE$ACID_RAIN;
        }
        return YONGYE$VANILLA_RAIN;
    }
}
