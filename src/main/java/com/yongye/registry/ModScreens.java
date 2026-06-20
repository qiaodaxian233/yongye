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

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 饰品容器已注册");
    }
}
