package com.yongye.client;

import com.yongye.Yongye;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;

/**
 * 客户端入口:为每个生物渲染器追加「精英叠层贴图」特性。
 * 叠层自身按名字「精英」自门控,所以挂到所有生物渲染器即可,普通怪不受影响。
 */
@Environment(EnvType.CLIENT)
public class YongyeClient implements ClientModInitializer {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) ->
                        registrationHelper.register(new EliteSkinFeatureRenderer(entityRenderer)));

        Yongye.LOGGER.info("[亡途荒夜] 客户端:精英叠层贴图已注册");
    }
}
