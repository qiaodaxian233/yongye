package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.item.WeaponQuality;
import com.yongye.network.SkillUsePayload;
import com.yongye.network.StatsPayload;
import com.yongye.registry.ModComponents;
import com.yongye.system.EquipmentEnhancer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口:
 *  1. 为每个生物渲染器追加「精英叠层贴图」特性(按名字「精英」自门控)。
 *  2. 接收成长数据;在背包界面加「成长」按钮打开成长面板。
 */
@Environment(EnvType.CLIENT)
public class YongyeClient implements ClientModInitializer {

    private static boolean pendingClassSelect = false;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) ->
                        registrationHelper.register(new EliteSkinFeatureRenderer(entityRenderer)));

        // 接收服务端成长数据
        ClientPlayNetworking.registerGlobalReceiver(StatsPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientStats.update(payload.health(), payload.levels(), payload.className())));

        // 接收服务端天赋状态(供天赋界面渲染)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.TalentSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientTalents.update(payload.points(), payload.classes(), payload.learned())));

        // 开局选职:收到 S2C 后置位,待进入世界且无其它界面时再弹出(避免被登录过场覆盖)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.OpenClassSelectPayload.ID, (payload, context) ->
                context.client().execute(() -> pendingClassSelect = true));

        // 调试菜单:收到 S2C(由 /yongye debug 触发)即打开 DebugScreen。
        // 命令在世界内显式触发,不存在登录过场覆盖问题,直接 setScreen 即可。
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.OpenDebugPayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new DebugScreen())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (pendingClassSelect && client.player != null && client.currentScreen == null) {
                pendingClassSelect = false;
                client.setScreen(new ClassSelectScreen());
            }
        });

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
                // 「饰品」按钮:打开饰品栏(放神器)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("饰品"),
                        b -> ClientPlayNetworking.send(new com.yongye.network.OpenAccessoryPayload()))
                        .dimensions(bx, by - 18, 44, 16).build());
                // 「天赋」按钮:打开天赋界面
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("天赋"),
                        b -> client.setScreen(new TalentScreen(screen)))
                        .dimensions(bx + 46, by - 18, 44, 16).build());
                // 当前本命职业显示(点开成长面板)
                com.yongye.item.PlayerClass pc = com.yongye.item.PlayerClass.byId(ClientStats.className);
                String classLabel = pc != null ? "本命·" + pc.cn : "无职业";
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(classLabel),
                        b -> client.setScreen(new StatsScreen(screen)))
                        .dimensions(bx, by - 36, 44, 16).build());
                // 「强化」按钮:打开武器强化窗口(放装备+材料,按数量一键升级)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("强化"),
                        b -> ClientPlayNetworking.send(new com.yongye.network.OpenEnhancePayload()))
                        .dimensions(bx + 46, by - 36, 44, 16).build());
                // 「兑换」按钮:打开材料兑换界面(10 碎片→结晶→核心→血核)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("兑换"),
                        b -> client.setScreen(new ExchangeScreen(screen)))
                        .dimensions(bx, by - 54, 44, 16).build());
            }
        });
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                com.yongye.registry.ModScreens.ACCESSORY, com.yongye.client.AccessoryScreen::new);
        net.minecraft.client.gui.screen.ingame.HandledScreens.register(
                com.yongye.registry.ModScreens.ENHANCE, com.yongye.client.EnhanceScreen::new);

        // 装备强化:tooltip 显示品质 + 强化等级(任意装备通用,零 mixin)
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            int lvl = stack.getOrDefault(ModComponents.ENHANCE_LEVEL, 0);
            if (lvl > 0) {
                WeaponQuality q = WeaponQuality.forLevel(lvl);
                lines.add(Text.literal("【" + q.cn + "】").formatted(q.color)
                        .append(Text.literal("  稀有度 " + q.grade).formatted(Formatting.GRAY)));
                lines.add(Text.literal("✦ 强化 +" + lvl).formatted(Formatting.AQUA));
            }
            if (stack.getOrDefault(ModComponents.DISARM_PROOF, false)) {
                lines.add(Text.literal("⚔ 无法被夺取").formatted(Formatting.LIGHT_PURPLE));
            }
        });

        // 武器主动技能按键(默认 R / G / V)→ 发包给服务端施放
        KeyBinding[] skillKeys = new KeyBinding[]{
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.yongye.skill1", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.yongye")),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.yongye.skill2", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.yongye")),
                KeyBindingHelper.registerKeyBinding(new KeyBinding(
                        "key.yongye.skill3", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.categories.yongye"))
        };
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (int i = 0; i < skillKeys.length; i++) {
                while (skillKeys[i].wasPressed()) {
                    if (client.player != null) ClientPlayNetworking.send(new SkillUsePayload(i));
                }
            }
        });

        // 职业大招按键(默认 X)→ 发包施放本命职业主动技能
        KeyBinding ultimateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yongye.ultimate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.yongye"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ultimateKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new com.yongye.network.ClassUltimatePayload());
            }
        });

        Yongye.LOGGER.info("[永夜] 客户端:精英皮肤 + 成长面板 + 装备介绍 + 技能按键已注册");
    }
}
