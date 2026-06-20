package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.item.WeaponQuality;
import com.yongye.network.StatsPayload;
import com.yongye.registry.ModComponents;
import com.yongye.system.EquipmentEnhancer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 客户端入口:
 *  1. 为每个生物渲染器追加「精英叠层贴图」特性(按名字「精英」自门控)。
 *  2. 接收成长数据;在背包界面加「成长」按钮打开成长面板。
 */
@Environment(EnvType.CLIENT)
public class YongyeClient implements ClientModInitializer {

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) ->
                        registrationHelper.register(new EliteSkinFeatureRenderer(entityRenderer)));

        // 接收服务端成长数据
        ClientPlayNetworking.registerGlobalReceiver(StatsPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientStats.update(payload.health(), payload.levels())));

        // 背包界面加「成长」按钮 → 打开成长面板
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                int bx = scaledWidth / 2 - 88;
                int by = scaledHeight / 2 - 100;
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("成长"),
                        b -> client.setScreen(new StatsScreen(screen)))
                        .dimensions(bx, by, 44, 16).build());
                // 「装备」按钮:查看手持武器/盔甲的品质介绍
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("装备"), b -> {
                    if (client.player == null) return;
                    ItemStack held = client.player.getMainHandStack();
                    if (!held.isEmpty() && EquipmentEnhancer.isEnhanceable(held.getItem())) {
                        client.setScreen(new WeaponInfoScreen(screen, held));
                    }
                }).dimensions(bx + 46, by, 44, 16).build());
            }
        });

        // 装备强化:tooltip 显示品质 + 强化等级(任意装备通用,零 mixin)
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            int lvl = stack.getOrDefault(ModComponents.ENHANCE_LEVEL, 0);
            if (lvl > 0) {
                WeaponQuality q = WeaponQuality.forLevel(lvl);
                lines.add(Text.literal("【" + q.cn + "】").formatted(q.color)
                        .append(Text.literal("  稀有度 " + q.grade).formatted(Formatting.GRAY)));
                lines.add(Text.literal("✦ 强化 +" + lvl).formatted(Formatting.AQUA));
            }
        });

        Yongye.LOGGER.info("[亡途荒夜] 客户端:精英皮肤 + 成长面板 + 装备介绍已注册");
    }
}
