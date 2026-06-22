package com.yongye.registry;

import com.yongye.Yongye;
import com.yongye.screen.AccessoryScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/** 自定义容器类型注册。 */
public final class ModScreens {
    private ModScreens() {}

    public static final ScreenHandlerType<AccessoryScreenHandler> ACCESSORY =
            Registry.register(Registries.SCREEN_HANDLER,
                    Identifier.of(Yongye.MOD_ID, "accessory"),
                    new ScreenHandlerType<>(AccessoryScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<com.yongye.screen.EnhanceScreenHandler> ENHANCE =
            Registry.register(Registries.SCREEN_HANDLER,
                    Identifier.of(Yongye.MOD_ID, "enhance"),
                    new ScreenHandlerType<>(com.yongye.screen.EnhanceScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static void init() {
        Yongye.LOGGER.info("[永夜] 饰品/强化容器已注册");
    }
}
