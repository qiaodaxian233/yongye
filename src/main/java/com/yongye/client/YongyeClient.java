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
    private static boolean pendingDifficulty = false;
    /** 永夜 HUD 状态(由 NightfallSyncPayload 更新):等级 + 阶段名 + 视野压缩强度 */
    public static int nightfallLevel = 0;
    public static String nightfallName = "";
    public static int nightfallVision = 0;
    /** 灾厄核心定位器状态(由 CoreLocatorPayload 更新):是否有目标 + 世界坐标 */
    public static boolean coreHasTarget = false;
    public static double coreTX = 0, coreTY = 0, coreTZ = 0;

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

        // 开局难度:收到 S2C 后置位,待进入世界且无其它界面时再弹出(同选职机制)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.OpenDifficultyPayload.ID, (payload, context) ->
                context.client().execute(() -> pendingDifficulty = true));

        // 调试菜单:收到 S2C(由 /yongye debug 触发)即打开 DebugScreen。
        // 命令在世界内显式触发,不存在登录过场覆盖问题,直接 setScreen 即可。
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.OpenDebugPayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new DebugScreen())));

        // 守护界面:收到 S2C(右键守护书触发)即打开 WardScreen。
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.OpenWardPayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new WardScreen())));

        // 永夜同步:更新 HUD 状态
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.NightfallSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    nightfallLevel = payload.level();
                    nightfallName = payload.name();
                    nightfallVision = payload.vision();
                }));

        // MP 同步:职业资源条(每10tick)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.MpSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientStats.mp = payload.mp()));

        // 灾厄核心定位器同步:更新方向箭头目标
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.CoreLocatorPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    coreHasTarget = payload.has();
                    coreTX = payload.x(); coreTY = payload.y(); coreTZ = payload.z();
                }));

        // 爆率编辑器:收到当前配置值 → 填进编辑器输入框
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.ConfigValuesPayload.ID, (payload, context) ->
                context.client().execute(() -> DropRateConfigScreen.onValues(payload.data())));

        // 永夜暗角:恒定亮度的边缘压暗,替代会"一闪一闪"的原版黑暗效果。
        // 纯静态绘制(不含任何时间/帧变量)→ 亮度固定、绝不闪;vision 越大越暗越收窄。
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (nightfallVision <= 0) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            int v = Math.min(nightfallVision, 6);
            int reachX = (int) (w * (0.08 + 0.025 * v));   // 暗角从左右边缘向内延伸的深度
            int reachY = (int) (h * (0.08 + 0.025 * v));   // 上下同理
            int edgeAlpha = Math.min(0x40 + v * 0x12, 0xC0); // 最外圈不透明度(封顶防全黑)
            int steps = 12;
            for (int i = 0; i < steps; i++) {
                float f = 1f - (float) i / steps;          // 1=最外圈 → 0=内圈
                int a = (int) (edgeAlpha * f * f);         // 平方衰减:边缘骤暗、向中心平滑透明
                if (a <= 2) continue;
                int col = (a << 24);                        // 纯黑 + alpha
                int x1 = reachX * i / steps, x2 = reachX * (i + 1) / steps;
                int y1 = reachY * i / steps, y2 = reachY * (i + 1) / steps;
                ctx.fill(x1, 0, x2, h, col);                // 左缘
                ctx.fill(w - x2, 0, w - x1, h, col);        // 右缘
                ctx.fill(0, y1, w, y2, col);                // 上缘
                ctx.fill(0, h - y2, w, h - y1, col);        // 下缘
            }
        });

        // 永夜 HUD:开启永夜(等级≥1)时,在屏幕中上显示当前阶段
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (nightfallLevel < 1 || nightfallName.isEmpty()) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            net.minecraft.text.Text t = net.minecraft.text.Text.literal(nightfallName)
                    .formatted(net.minecraft.util.Formatting.DARK_RED, net.minecraft.util.Formatting.BOLD);
            int w = mc.textRenderer.getWidth(t);
            int x = (mc.getWindow().getScaledWidth() - w) / 2;
            ctx.drawTextWithShadow(mc.textRenderer, t, x, 4, 0xFFFF5555);
        });

        // 灾厄核心方向箭头 HUD:有目标核心时,在屏幕中上画一个指向它的旋转箭头 + 距离(像 boss 指示)。
        // 箭头朝向用"玩家当前视角 + 同步来的核心坐标"逐帧计算,所以转视角时箭头平滑旋转。
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (!coreHasTarget) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            double dx = coreTX - mc.player.getX();
            double dz = coreTZ - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            // 玩家水平朝向单位向量(MC yaw:0=+Z/南,90=-X/西)
            float yawRad = (float) Math.toRadians(mc.player.getYaw());
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);
            // 相对方位角:0=正前,+向右,-向左
            double dot = fx * dx + fz * dz;
            double cross = fx * dz - fz * dx;
            double bearingDeg = Math.toDegrees(Math.atan2(cross, dot));

            int cx = mc.getWindow().getScaledWidth() / 2;
            int cy = 30; // 在永夜阶段名(y=4)下方;若与 boss 血条重叠可调

            // 旋转箭头(▲ 默认指上=正前;按方位角绕 Z 旋转)
            net.minecraft.text.Text arrow = net.minecraft.text.Text.literal("▲")
                    .formatted(net.minecraft.util.Formatting.BOLD);
            int aw = mc.textRenderer.getWidth(arrow);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().multiply(
                    net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees((float) bearingDeg));
            ctx.drawText(mc.textRenderer, arrow, -aw / 2, -4, 0xFFFF3030, false);
            ctx.getMatrices().pop();

            // 距离文字(不旋转,箭头下方)
            net.minecraft.text.Text dt = net.minecraft.text.Text.literal("灾厄核心 " + (int) dist + " 格")
                    .formatted(net.minecraft.util.Formatting.GOLD);
            int dw = mc.textRenderer.getWidth(dt);
            ctx.drawTextWithShadow(mc.textRenderer, dt, cx - dw / 2, cy + 8, 0xFFFFAA33);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 每 tick 刷新血量速率采样(供血量 HUD 显示实时回血/掉血)
            HealthRateTracker.tick();
            if (pendingDifficulty && client.player != null && client.currentScreen == null) {
                pendingDifficulty = false;
                client.setScreen(new DifficultyScreen());
            }
            if (pendingClassSelect && client.player != null && client.currentScreen == null) {
                pendingClassSelect = false;
                client.setScreen(new ClassSelectScreen());
            }
        });

        // 背包界面:把功能按钮竖排放在背包面板**左侧的空白竖条**(用户指定位置)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                // 原版背包面板:宽 176、高 166,居中。左缘 = 屏宽/2 - 88,上缘 = 屏高/2 - 83。
                int guiLeft = scaledWidth / 2 - 88;
                int guiTop = scaledHeight / 2 - 83;
                int bw = 54, bh = 16, pitch = 19;       // 按钮宽/高/行距
                int bx = guiLeft - bw - 4;               // 放在面板左侧,留 4px 缝
                int by = guiTop + 5;                     // 从面板顶部略下开始竖排
                int row = 0;

                // 成长
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("成长"),
                        b -> client.setScreen(new StatsScreen(screen)))
                        .dimensions(bx, by + pitch * row++, bw, bh).build());
                // 装备:查看手持武器/盔甲的品质介绍
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("装备"), b -> {
                    if (client.player == null) return;
                    ItemStack held = client.player.getMainHandStack();
                    if (!held.isEmpty() && EquipmentEnhancer.isEnhanceable(held.getItem())) {
                        client.setScreen(new WeaponInfoScreen(screen, held));
                    }
                }).dimensions(bx, by + pitch * row++, bw, bh).build());
                // 饰品:打开饰品栏(放神器)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("饰品"),
                        b -> ClientPlayNetworking.send(new com.yongye.network.OpenAccessoryPayload()))
                        .dimensions(bx, by + pitch * row++, bw, bh).build());
                // 天赋:打开天赋界面
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("天赋"),
                        b -> client.setScreen(new TalentScreen(screen)))
                        .dimensions(bx, by + pitch * row++, bw, bh).build());
                // 强化:打开 Ward 式强化窗口(点装备=用背包全部材料一键强化)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("强化"),
                        b -> client.setScreen(new EnhanceSelectScreen(screen)))
                        .dimensions(bx, by + pitch * row++, bw, bh).build());
                // 兑换:打开材料兑换界面(10 碎片→结晶→核心→血核)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("兑换"),
                        b -> client.setScreen(new ExchangeScreen(screen)))
                        .dimensions(bx, by + pitch * row++, bw, bh).build());
                // 学书:一键把背包所有技能书/血量书学掉
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("学书"),
                        b -> ClientPlayNetworking.send(new com.yongye.network.UseAllBooksPayload()))
                        .dimensions(bx, by + pitch * row++, bw, bh).build());
                // 当前本命职业(点开成长面板)
                com.yongye.item.PlayerClass pc = com.yongye.item.PlayerClass.byId(ClientStats.className);
                String classLabel = pc != null ? "本命·" + pc.cn : "无职业";
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(classLabel),
                        b -> client.setScreen(new StatsScreen(screen)))
                        .dimensions(bx, by + pitch * row++, bw, bh).build());
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
