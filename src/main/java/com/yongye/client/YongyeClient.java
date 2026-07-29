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
    /** m273 连击 HUD 状态:当前连击数 + 跳动帧(计数增加瞬间放大回落) */
    private static int comboCount = 0;
    private static int comboPopTicks = 0;
    /** m279 连击特效状态:升档冲击环(12→0 外扩淡出)/称号弹字(24→0 上浮)/断连提示(30→0 下沉) */
    private static int comboRingTicks = 0;
    private static int comboRingTier = 0;
    private static int comboTitleTicks = 0;
    private static String comboTitle = "";
    private static int comboBreakTicks = 0;
    private static int comboBreakCount = 0;
    /** m287 濒死心跳计时(客户端本地) */
    private static int lowHpBeatTicks = 0;
    /** 永夜 HUD 状态(由 NightfallSyncPayload 更新):等级 + 阶段名 + 视野压缩强度 */
    public static int nightfallLevel = 0;
    public static String nightfallName = "";
    public static int nightfallVision = 0;
    /** 灾厄核心定位器状态(由 CoreLocatorPayload 更新):是否有目标 + 世界坐标 */
    public static boolean coreHasTarget = false;
    public static double coreTX = 0, coreTY = 0, coreTZ = 0;
    /** m346 技能CD常显HUD:剩余冷却 tick(0..2=R/G/V 3=大招 4=小技能;SkillCdPayload 每10t刷新,本地每t递减保平滑) */
    private static final int[] skillCdLeft = new int[5];
    /** m346 冷却峰值(=本轮总冷却,进度线分母;收包时「变大=新施放」置峰、归零清峰,免下发总CD) */
    private static final int[] skillCdPeak = new int[5];
    /** m346 按键静态引用(注册处赋值):HUD 键位标签走 getBoundKeyLocalizedText,玩家改键跟着变 */
    private static KeyBinding[] skillKeysRef = null;
    private static KeyBinding ultimateKeyRef = null, minorSkillKeyRef = null;

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void onInitializeClient() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) ->
                        registrationHelper.register(new EliteSkinFeatureRenderer(entityRenderer)));

        // m310 所有僵尸红眼+紫光:僵尸/尸壳/溺尸共用僵尸脸位贴图,僵尸村民单独一张(村民头 8x10 眼位不同)
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityType == net.minecraft.entity.EntityType.ZOMBIE
                            || entityType == net.minecraft.entity.EntityType.HUSK
                            || entityType == net.minecraft.entity.EntityType.DROWNED) {
                        registrationHelper.register(new ZombieRedEyesFeatureRenderer(entityRenderer, false));
                    } else if (entityType == net.minecraft.entity.EntityType.ZOMBIE_VILLAGER) {
                        registrationHelper.register(new ZombieRedEyesFeatureRenderer(entityRenderer, true));
                    }
                });

        // m311 全怪紫气分档:普通轻微/精英中等/BOSS高等——挂在所有活体上,渲染器内部自判档位,
        // 非敌对直接零开销返回;渲染驱动=只有看得见的怪才冒,加距离裁剪+概率限流(注释详见类头)
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) ->
                        registrationHelper.register(new MobAuraFeatureRenderer(entityRenderer)));

        // 自定义末影龙 BOSS 的 GeckoLib 渲染器
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.TORO_ENDER_DRAGON,
                com.yongye.client.render.ToroEnderDragonRenderer::new);

        // 【m186】原版末影龙渲染还原默认:m164 曾用 GeckoLib 替身接管原版龙外观,但
        // GeoReplacedEntityRenderer 的替身动画在原版龙身上表现异常(翅膀只扇一下就停,
        // m185 修完朝向后暴露)。按作者决定:末地原版龙恢复原版模型/动画,m183 的属性
        // 加强(10亿血/三命/脱战回血,全在服务端 EndDragonHandler)不受影响照常生效;
        // 自建 BOSS 龙(TORO_ENDER_DRAGON)继续用上面的 GeckoLib 渲染器。

        // 【m167】精英·毒液蜘蛛 / BOSS·红蜘蛛 的 GeckoLib 渲染器
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.VENOM_SPIDER,
                com.yongye.client.render.VenomSpiderRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.RED_SPIDER,
                com.yongye.client.render.RedSpiderRenderer::new);

        // 【m169】BOSS·浴火凤凰 / 【m170】BOSS·死亡法师、精英·巨型螃蟹 的 GeckoLib 渲染器
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.FIRE_PHOENIX,
                com.yongye.client.render.FirePhoenixRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.DEATH_MAGE,
                com.yongye.client.render.DeathMageRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.GIANT_CRAB,
                com.yongye.client.render.GiantCrabRenderer::new);

        // 【m172/m173】BOSS·阿努比斯 / 小怪·阿努比斯恶灵 的 GeckoLib 渲染器
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.ANUBIS,
                com.yongye.client.render.AnubisRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.ANUBIS_WRAITH,
                com.yongye.client.render.AnubisWraithRenderer::new);

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
        // m328 任务书:右键书 → 开界面;进度快照 → 刷新界面
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.OpenQuestBookPayload.ID, (payload, context) ->
                context.client().execute(() -> context.client().setScreen(new QuestBookScreen(null))));
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.MainQuestSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> QuestBookScreen.onSync(payload)));

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

        // m278 格挡值同步:进血条面板画青蓝格挡条(破防红闪+倒计时;HudCompactMixin 绘制)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.GuardSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientStats.guardCur = payload.cur();
                    ClientStats.guardMax = payload.max();
                    ClientStats.guardBroken = payload.broken();
                    ClientStats.guardHolding = payload.holding();
                }));

        // m288/m289 战况看板:收 击杀/下一阶段/倒计时/按天预告 → 左边缘信息块(天数客户端自算)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.HudInfoPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    ClientStats.totalKills = payload.kills();
                    ClientStats.nextStageName = payload.nextName();
                    ClientStats.nextStageSeconds = payload.nextSeconds();
                    ClientStats.dayForecast = payload.dayForecast();
                    ClientStats.dayForecastShort = payload.dayForecastShort();
                }));
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
            var cfg = com.yongye.YongyeConfig.get();
            if (!cfg.enableHudInfoPanel) return;
            var tr = mc.textRenderer;
            boolean cp = cfg.hudInfoCompact;   // m308 紧凑模式(默认开):三行全换短文案
            // 行1:第 N 天 · 击杀 X(天数客户端按昼夜时钟直算,睡觉跳夜也算天——m252 收口同源)
            long day = com.yongye.system.ProgressionManager.gameDay(mc.world) + 1;   // 第 1 天起算
            String l1a = cp ? "第" + day + "天" : "第 " + day + " 天";
            String l1b = (cp ? "·击杀" : " · 击杀 ") + NumFmt.compact(ClientStats.totalKills);
            // 行2:下一阶段:XXX (mm:ss)——空名=已至上限,整行省略;紧凑=去空格+短前缀
            String l2 = "";
            String l2t = "";
            if (!ClientStats.nextStageName.isEmpty()) {
                l2 = cp ? "下阶段:" + ClientStats.nextStageName.replace(" ", "")
                        : "下一阶段:" + ClientStats.nextStageName;
                if (ClientStats.nextStageSeconds >= 0) {
                    l2t = String.format(" %02d:%02d",
                            ClientStats.nextStageSeconds / 60, ClientStats.nextStageSeconds % 60);
                }
            }
            // 行3(m289):按天事件预告;紧凑用服务端短版「N天后:XXX+M」(m308)
            String l3 = cp ? ClientStats.dayForecastShort : ClientStats.dayForecast;
            int w1 = tr.getWidth(l1a + l1b);
            int w2 = l2.isEmpty() ? 0 : tr.getWidth(l2 + l2t);
            int w3 = l3.isEmpty() ? 0 : tr.getWidth(l3);
            int bw = Math.max(w1, Math.max(w2, w3)) + 8;
            int lines = 1 + (l2.isEmpty() ? 0 : 1) + (l3.isEmpty() ? 0 : 1);
            int bh = 2 + lines * 11;
            // m308 位置可挪:hudInfoAnchor 0=左中(m289 原位) 1=左上 2=左下 3=右上 4=右中 5=右下,
            // 再叠 hudInfoOffsetX/Y 微调,最后钳回屏内(乱填偏移也不会飞出屏幕)。
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            int anchor = cfg.hudInfoAnchor;
            int bx = (anchor == 3 || anchor == 4 || anchor == 5) ? sw - bw - 3 : 3;
            int by = switch (anchor) {
                case 1, 3 -> 4;               // 上缘(顶中 BOSS 血条不在角上,角落可用)
                case 2, 5 -> sh - bh - 48;    // 下缘(避开热栏/聊天输入行)
                default   -> sh / 2 - bh - 10; // 左/右中,中线略上(m289 原位)
            };
            bx = Math.max(0, Math.min(sw - bw, bx + cfg.hudInfoOffsetX));
            by = Math.max(0, Math.min(sh - bh, by + cfg.hudInfoOffsetY));
            ctx.fill(bx, by, bx + bw, by + bh, 0x66000000);                          // 半透明底,保证任何背景可读
            ctx.fill(bx, by, bx + bw, by + 1, 0x802E7AD0);                           // 顶描边(面板同蓝系)
            int ty = by + 3;
            ctx.drawTextWithShadow(tr, net.minecraft.text.Text.literal(l1a), bx + 4, ty, 0xFFFFD700);
            ctx.drawTextWithShadow(tr, net.minecraft.text.Text.literal(l1b), bx + 4 + tr.getWidth(l1a), ty, 0xFFFF7070);
            if (!l2.isEmpty()) {
                ty += 11;
                ctx.drawTextWithShadow(tr, net.minecraft.text.Text.literal(l2), bx + 4, ty, 0xFFC08CFF);
                if (!l2t.isEmpty()) {
                    // 最后一分钟倒计时转红,提醒要升层了
                    int tc = ClientStats.nextStageSeconds <= 60 ? 0xFFFF5555 : 0xFF9AA6B2;
                    ctx.drawTextWithShadow(tr, net.minecraft.text.Text.literal(l2t), bx + 4 + tr.getWidth(l2), ty, tc);
                }
            }
            if (!l3.isEmpty()) {
                ty += 11;
                // 明天就来=橙红提醒,平时暖橙
                int fc = l3.contains("明天") ? 0xFFFF6040 : 0xFFFFB050;
                ctx.drawTextWithShadow(tr, net.minecraft.text.Text.literal(l3), bx + 4, ty, fc);
            }
        });

        // 攻击伤害同步:服务端终值(原版 GENERIC_ATTACK_DAMAGE 不下发客户端,成长面板要显示真值)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.AttackSyncPayload.ID, (payload, context) ->
                context.client().execute(() -> ClientStats.attackDamage = payload.atk()));

        // m239 沉浸式战斗手感:收命中/击杀 FX 包 → 置入镜头震动/FOV 顿挫/闪光/确认音
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.CombatFxPayload.ID, (payload, context) ->
                context.client().execute(() -> CombatFxManager.onFx(
                        payload.kind(), payload.shake(), payload.fov(), payload.flash(), payload.sound(),
                        payload.hitstop())));
        ClientTickEvents.END_CLIENT_TICK.register(client -> CombatFxManager.tick());
        // m273 连击计数器:收计数 → HUD 在热栏右上画连击数(变化瞬间弹一下)
        // m279:升档瞬间触发冲击环+称号弹字+升调音效;10 连以上被断触发断连提示
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.ComboPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    boolean fancy = com.yongye.YongyeConfig.get().enableComboFancyFx;
                    int nc = payload.count();
                    if (fancy && nc == 0 && comboCount >= 10) {          // 断连反馈
                        comboBreakTicks = 30;
                        comboBreakCount = comboCount;
                    }
                    if (nc > comboCount) {
                        comboPopTicks = 4;
                        int newTier = nc / 5;
                        if (fancy && newTier > comboCount / 5 && newTier >= 1) {   // 升档瞬间
                            comboRingTicks = 12;
                            comboRingTier = newTier;
                            comboTitleTicks = 24;
                            comboTitle = comboTitleFor(newTier);
                            var mc0 = net.minecraft.client.MinecraftClient.getInstance();
                            if (mc0.player != null) mc0.player.playSound(                 // 经验球叮声随档位升调
                                    net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                                    0.9f, Math.min(2.0f, 0.8f + 0.2f * newTier));
                        }
                    }
                    comboCount = nc;
                }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (comboPopTicks > 0) comboPopTicks--;
            if (comboRingTicks > 0) comboRingTicks--;    // m279 特效计时
            if (comboTitleTicks > 0) comboTitleTicks--;
            if (comboBreakTicks > 0) comboBreakTicks--;
            if (ClientStats.guardBroken > 0) ClientStats.guardBroken--;   // m278:破防倒计时平滑
        });
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            boolean fancy = com.yongye.YongyeConfig.get().enableComboFancyFx;
            // m283:原 y=h-62 正好压在面板右侧标签区(护甲数/20\u002F20/坚守),实机截图字全叠一起;
            // 整块上移到 h-112(加成行 h-100),右侧一列自下而上=面板标签 → 连击块,互不压。
            int x = mc.getWindow().getScaledWidth() / 2 + 108;
            int y = mc.getWindow().getScaledHeight() - 112;
            // m279 断连提示(独立于当前连击数):10 连以上被断,灰字「连击中断 ×N」下沉淡出
            if (fancy && comboBreakTicks > 0 && comboCount < 2) {
                float t = 1f - comboBreakTicks / 30f;
                int a = Math.max(0x20, (int) (0xFF * (1f - t)));
                ctx.drawTextWithShadow(mc.textRenderer,
                        net.minecraft.text.Text.literal("连击中断 ×" + comboBreakCount),
                        x, y + (int) (t * 10), (a << 24) | 0xAAAAAA);
            }
            if (comboCount < 2) return; // 1 连不值得画
            int tier = comboCount / 5;
            // m284:档位色扩到 10 档,10 档以上彩虹流转(见 comboColor)
            int col = comboColor(tier);
            // m279 升档冲击环:档位色双方环(直角+45°)从数字中心外扩淡出
            if (fancy && comboRingTicks > 0) {
                float t = 1f - comboRingTicks / 12f;
                int r = 6 + (int) (t * 26);
                int a = Math.max(0x18, (int) (0xC0 * (1f - t)));
                int ringCol = (a << 24) | (comboColor(comboRingTier) & 0xFFFFFF);   // m284:环色同档位色(含彩虹)
                var m0 = ctx.getMatrices();
                m0.push();
                m0.translate(x + 18, y + 3, 0);
                comboRing(ctx, r, ringCol);
                m0.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(45f));
                comboRing(ctx, Math.max(3, (int) (r * 0.72f)), ringCol);
                m0.pop();
            }
            // m279 称号弹字:升档瞬间在数字上方上浮淡出(凌厉/狂怒/无双/灭世)
            if (fancy && comboTitleTicks > 0 && !comboTitle.isEmpty()) {
                float t = 1f - comboTitleTicks / 24f;
                int a = Math.max(0x20, (int) (0xFF * (1f - t * t)));
                ctx.drawTextWithShadow(mc.textRenderer,
                        net.minecraft.text.Text.literal(comboTitle).formatted(net.minecraft.util.Formatting.BOLD),
                        x + 2, y - 14 - (int) (t * 8), (a << 24) | (col & 0xFFFFFF));
            }
            String main = comboCount + " 连击";
            var m = ctx.getMatrices();
            m.push();
            float scale = 1.15f + comboPopTicks * 0.09f; // 计数跳动瞬间放大回落
            int jx = 0, jy = 0;
            if (fancy && tier >= 3) {                    // m279:3 档起 1px 高频抖动
                long jt = System.currentTimeMillis() / 50;
                jx = (int) (jt % 3) - 1;
                jy = (int) ((jt / 3) % 3) - 1;
            }
            m.translate(x + jx, y + jy, 0);
            m.scale(scale, scale, 1f);
            var mainTxt = net.minecraft.text.Text.literal(main).formatted(net.minecraft.util.Formatting.BOLD);
            if (fancy && tier >= 2) {                    // m279:2 档起伪辉光(低透明同色八向描一圈)
                int glow = 0x38000000 | (col & 0xFFFFFF);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    ctx.drawText(mc.textRenderer, mainTxt, dx, dy, glow, false);
                }
            }
            ctx.drawTextWithShadow(mc.textRenderer, mainTxt, 0, 0, col);
            m.pop();
            if (tier > 0) {
                // 展示按默认档速算(每档 攻+4%/速+3%,封顶 40/30);真实数值在服务端 ComboHandler 按配置算
                String bonus = "攻+" + Math.min(40, tier * 4) + "% 速+" + Math.min(30, tier * 3) + "%";
                ctx.drawTextWithShadow(mc.textRenderer, net.minecraft.text.Text.literal(bonus), x, y + 12, 0xFFB0C4FF);
            }
        });
        // m305 烛之维度淡紫滤镜:身在 yongye:candle 即整屏罩一层淡紫(alpha 走配置)。
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;
            if (mc.world.getRegistryKey() != com.yongye.system.CandleDimension.WORLD_KEY) return;
            int a = Math.max(0, Math.min(160, com.yongye.YongyeConfig.get().candleDimFilterAlpha));
            if (a == 0) return;
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            ctx.fill(0, 0, w, h, (a << 24) | 0xB387FF);
        });
        // m305 门块走 cutout(贴图带透明孔)。待编译验证:BlockRenderLayerMap 仓库首用——报错删下面这行,
        // 门块退回实心渲染,功能不受影响。
        net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(
                com.yongye.registry.ModBlocks.CANDLE_PORTAL, net.minecraft.client.render.RenderLayer.getCutout());
        // m287 濒死危机演出:血量≤阈值 → 屏幕边缘血红渐晕随心跳呼吸(越残越浓越大),配监守者心跳音越残越急。
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            var c = com.yongye.YongyeConfig.get();
            if (!c.enableLowHpFx) return;
            float thr = (float) Math.max(0.01, Math.min(0.9, c.lowHpFxThreshold));
            float frac = mc.player.getHealth() / Math.max(1f, mc.player.getMaxHealth());
            if (mc.player.getHealth() <= 0 || frac > thr) return;
            float sev = 1f - frac / thr;                                             // 0~1 越残越大
            float pulse = 0.5f + 0.5f * (float) Math.sin(
                    System.currentTimeMillis() / (220.0 - 120.0 * sev));             // 呼吸,越残频率越高
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            int reachX = (int) (w * (0.10 + 0.12 * sev));
            int reachY = (int) (h * (0.10 + 0.12 * sev));
            int maxA = (int) ((0x38 + 0x58 * sev) * (0.55 + 0.45 * pulse));
            int steps = 7;                                                           // 分级渐晕(视野压缩同画法)
            for (int i = 0; i < steps; i++) {
                int a = maxA * (steps - i) / steps;
                int col = (a << 24) | 0xB00000;
                int x1 = reachX * i / steps, x2 = reachX * (i + 1) / steps;
                int y1 = reachY * i / steps, y2 = reachY * (i + 1) / steps;
                ctx.fill(x1, 0, x2, h, col);                // 左缘
                ctx.fill(w - x2, 0, w - x1, h, col);        // 右缘
                ctx.fill(0, y1, w, y2, col);                // 上缘
                ctx.fill(0, h - y2, w, h - y1, col);        // 下缘
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var p = client.player;
            if (p == null) { lowHpBeatTicks = 0; return; }
            var c = com.yongye.YongyeConfig.get();
            if (!c.enableLowHpFx) return;
            float thr = (float) Math.max(0.01, Math.min(0.9, c.lowHpFxThreshold));
            float frac = p.getHealth() / Math.max(1f, p.getMaxHealth());
            if (p.getHealth() <= 0 || frac > thr) { lowHpBeatTicks = 0; return; }
            float sev = 1f - frac / thr;
            if (++lowHpBeatTicks >= (int) (26 - 14 * sev)) {                         // 心跳间隔 26t→12t
                lowHpBeatTicks = 0;
                // 【待编译验证】ENTITY_WARDEN_HEARTBEAT(监守者家族 SONIC_BOOM 在树已用,此常量首用;报错换 ENTITY_WARDEN_AMBIENT)
                p.playSound(net.minecraft.sound.SoundEvents.ENTITY_WARDEN_HEARTBEAT,
                        0.5f + 0.5f * sev, 1.0f + 0.3f * sev);
            }
        });
        // m257 蓄力重斩:按住攻击键蓄力检测(手感反馈+松开上报)
        ClientTickEvents.END_CLIENT_TICK.register(ChargeSlashManager::tick);
        // m260 站姿状态机(战斗待机/格挡姿态):库不可用时同一套降级口径
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!SlashFxManager.animLibOk) return;
            try {
                SlashAnimManager.tickStance(client);
            } catch (Throwable t) {
                SlashFxManager.animLibOk = false;
                Yongye.LOGGER.warn("[夜蚀] 站姿动画运行期不可用,已降级", t);
            }
        });
        // 地面魔法阵特效(m246,法师技能包素材)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.MagicFxPayload.ID, (payload, context) ->
                context.client().execute(() -> MagicCircleFxManager.onCircle(
                        payload.color(), payload.x(), payload.y(), payload.z(), payload.radius())));
        MagicCircleFxManager.init();
        // 疾跑收刀:玩家渲染器挂背部武器 feature(m247;藏手侧由 WeaponSheathMixin 负责)
        net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register(
                (entityType, entityRenderer, registrationHelper, context) -> {
                    if (entityRenderer instanceof net.minecraft.client.render.entity.PlayerEntityRenderer per) {
                        registrationHelper.register(new WeaponBackFeatureRenderer(per));
                    }
                });
        // m240 拔刀剑式攻击动画:斩击轨迹(世界渲染)+ 近战命中兜底触发
        // 主触发在 PlayerSlashSwingMixin(doAttack,含挥空);这里的 AttackEntityCallback 是兜底——
        // mixin 若因映射不符没挂上(require=0),命中实体时仍出轨迹;两路在 trySpawn 里 50ms 去重
        SlashFxManager.register();
        // m254 真·骨骼拔刀动作(player-animator,JiJ 内置):所有库引用都隔离在 SlashAnimManager——
        // 库缺失/版本冲突时该类加载即抛 NoClassDefFoundError,这里 Throwable 兜住,整体退回程序化姿态不崩游戏。
        try {
            SlashAnimManager.register();
            SlashFxManager.animLibOk = true;
        } catch (Throwable t) {
            Yongye.LOGGER.warn("[夜蚀] player-animator 桥接失败,拔刀动作退回程序化姿态: {}", t.toString());
        }
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) SlashFxManager.trySpawn(player);
            return net.minecraft.util.ActionResult.PASS;
        });
        // 击杀闪光:整屏淡金色一瞬,快速淡出(纯 ctx.fill,零新绘制 API)
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            int a = CombatFxManager.flashAlpha();
            if (a <= 0) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            int w = mc.getWindow().getScaledWidth();
            int h = mc.getWindow().getScaledHeight();
            ctx.fill(0, 0, w, h, (a << 24) | 0xFFF2D8);
        });

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
            // m266:原先画在屏幕中上 y=4,会被 BOSS 血条(y=12 起往下叠)整个压住;
            // 改到玩家血条面板(HudCompactMixin,面板顶 = h-55)正上方,BOSS 再多也挡不到。
            // m278:面板画格挡条时整块加高上移 6px,这里连锁上移不打架。
            int y = mc.getWindow().getScaledHeight() - 66 - (ClientStats.guardBarShown ? 6 : 0);
            ctx.drawTextWithShadow(mc.textRenderer, t, x, y, 0xFFFF5555);
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
            // m266:原 y=30 会被 2 条以上 BOSS 血条压住;改到底部状态区。
            // m283:action bar 抬到 h-86 后,箭头再上移到 h-108(距离字 h-100),三层互不压:
            //   阶段名 h-66/-72 → action bar h-86 → 箭头块 h-100~h-113。不再随格挡条位移。
            int cy = mc.getWindow().getScaledHeight() - 108;

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
        // m345:复杂度热点拆分——按钮装配整体抽到 addInventoryButtons(纯搬移零逻辑变更)
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                addInventoryButtons(client, screen, scaledWidth, scaledHeight);
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

        // m322:无直接配方物品的「获取:」提示(集中式 SourceHints;itemSourceTooltips 可关)
        ItemTooltipCallback.EVENT.register((stack, ctx, type, lines) -> {
            if (!com.yongye.YongyeConfig.get().itemSourceTooltips) return;
            String src = SourceHints.of(stack.getItem());
            if (src != null) lines.add(Text.literal("获取:" + src).formatted(Formatting.DARK_GRAY));
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
        skillKeysRef = skillKeys;   // m346:CD HUD 取键位标签用(改键跟变)
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
        ultimateKeyRef = ultimateKey;   // m346:CD HUD 取键位标签用
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (ultimateKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new com.yongye.network.ClassUltimatePayload());
            }
        });

        // 职业小技能按键(m232,默认 C)→ 发包施放本命职业小技能(独立冷却,不占大招CD)
        KeyBinding minorSkillKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.yongye.minorskill", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.yongye"));
        minorSkillKeyRef = minorSkillKey;   // m346:CD HUD 取键位标签用
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (minorSkillKey.wasPressed()) {
                if (client.player != null) ClientPlayNetworking.send(new com.yongye.network.ClassMinorSkillPayload());
            }
        });

        // m346 技能CD常显HUD:收包 → 5 槽剩余冷却入缓存;「变大=新施放」置峰值(进度线分母),归零清峰
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.SkillCdPayload.ID, (payload, context) ->
                context.client().execute(() -> {
                    int[] vals = {payload.slash(), payload.devour(), payload.finality(), payload.ultimate(), payload.minor()};
                    for (int i = 0; i < 5; i++) {
                        if (vals[i] > skillCdLeft[i]) skillCdPeak[i] = vals[i];
                        else if (vals[i] == 0) skillCdPeak[i] = 0;
                        skillCdLeft[i] = vals[i];
                    }
                }));
        // m347 装备详情技能等级同步:收包写进 WeaponInfoScreen 静态字段(面板下一帧刷新)
        ClientPlayNetworking.registerGlobalReceiver(com.yongye.network.WeaponSkillLvPayload.ID, (payload, context) ->
                context.client().execute(() -> WeaponInfoScreen.onSync(payload)));
        // 本地每 tick 递减:两包(10t)之间秒数平滑走,不跳格
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (int i = 0; i < 5; i++) if (skillCdLeft[i] > 0) skillCdLeft[i]--;
        });
        // 渲染:血条面板左沿外右对齐一列(右缘 w/2-100,底行 h-50 向上堆,与连击块/看板/阶段名/核心箭头全不压)。
        // 持武器出 R/G/V 三行(未解锁深灰;混沌/龙魂免解锁同 m331),有职业出大招/小技能两行;
        // 就绪=金键绿字,冷却=灰名橙秒+底部 1px 蓝色恢复进度线;键位标签 getBoundKeyLocalizedText(改键跟变)。
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            var cfg = com.yongye.YongyeConfig.get();
            if (!cfg.enableSkillCdHud) return;
            if (skillKeysRef == null || ultimateKeyRef == null || minorSkillKeyRef == null) return;
            var tr = mc.textRenderer;
            ItemStack held = mc.player.getMainHandStack();
            boolean weapon = EquipmentEnhancer.isWeapon(held);
            boolean hasClass = !ClientStats.className.isEmpty();
            if (!weapon && !hasClass) return;

            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            int right = w / 2 - 100 + cfg.skillCdHudOffsetX;   // 右缘:血条面板左沿外
            int y = h - 50 + cfg.skillCdHudOffsetY;            // 底行,逐行向上堆

            // 职业招在下(离热栏近),武器技能在上——自下而上:小技能 C → 大招 X → V → G → R
            if (hasClass) {
                y = drawSkillCdRow(ctx, tr, right, y, minorSkillKeyRef, "小技能", 4, true);
                y = drawSkillCdRow(ctx, tr, right, y, ultimateKeyRef, "大招", 3, true);
            }
            if (weapon) {
                int lvl = held.getOrDefault(ModComponents.ENHANCE_LEVEL, 0);
                boolean freeUnlock = held.getItem() == com.yongye.registry.ModItems.CHAOS_BLADE
                        || held.getItem() == com.yongye.registry.ModItems.DRAGON_BLADE;   // m331 免解锁口径
                com.yongye.item.WeaponSkill[] sk = com.yongye.item.WeaponSkill.values();
                for (int i = sk.length - 1; i >= 0; i--) {
                    boolean unlocked = freeUnlock || sk[i].isUnlocked(lvl);
                    y = drawSkillCdRow(ctx, tr, right, y, skillKeysRef[i], sk[i].cn, i, unlocked);
                }
            }
        });

        // 【m206】全物品标识：悬停任意物品（原版+模组）tooltip 末尾加一行，可在配置关闭/改字

        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, tooltipContext, tooltipType, lines) -> {

            com.yongye.YongyeConfig cfg = com.yongye.YongyeConfig.get();

            if (cfg.enableItemWatermark && cfg.itemWatermarkText != null && !cfg.itemWatermarkText.isBlank()) {

                lines.add(net.minecraft.text.Text.literal(cfg.itemWatermarkText)

                        .formatted(net.minecraft.util.Formatting.GOLD));

            }

        });


        // 【m223】肝帝渲染器(玩家模型+模组皮肤)
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.GANDI, GanDiRenderer::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                com.yongye.registry.ModEntities.WARLOCK_CLONE, WarlockCloneRenderer::new); // m262 术士分身

        // 【m211】武器随强化等级动态染色:≤起始级(默认100)=纯黑白,越高越鲜艳,封顶级(默认2500)=正红;
        // 色相路径 200°(冰蓝)→360°(正红),刻意绕开绿/黄(作者点名不要)。贴图是 m210 黑白版,
        // 乘法染色下白→染成该色、黑保持黑,天然「有色金属」质感。ENHANCE_LEVEL 是数据组件,自动同步客户端。
        {
            java.util.List<net.minecraft.item.Item> tintItems =
                    new java.util.ArrayList<>(com.yongye.registry.ModItems.classWeapons().values());
            tintItems.add(com.yongye.registry.ModItems.CHAOS_BLADE);
            net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                    (stack, tintIndex) -> weaponTintColor(stack),
                    tintItems.toArray(new net.minecraft.item.ItemConvertible[0]));
        }

        Yongye.LOGGER.info("[夜蚀] 客户端:精英皮肤 + 成长面板 + 装备介绍 + 技能按键 + 武器等级染色已注册");
    }

    /** m211/m213:强化等级 → 染色(0xAARRGGBB,**必须带满 alpha**:1.21 起物品染色按 ARGB 解释,
     *  高 8 位是透明度,返回纯 RGB(高位=0)会把整件物品渲染成全透明=隐形,m212 后实机就是这么翻车的)。
     *  等级阶梯是指数型(100/250/500/1000/2500),用对数插值让各品质档的色变均匀。 */
    static int weaponTintColor(ItemStack stack) { // 包内可见:m240 斩击轨迹取同一套武器色
        com.yongye.YongyeConfig cfg = com.yongye.YongyeConfig.get();
        if (!cfg.weaponTintEnabled) return 0xFFFFFFFF;
        int lvl = stack.getOrDefault(ModComponents.ENHANCE_LEVEL, 0);
        int start = Math.max(1, cfg.weaponTintStartLevel);
        int end = Math.max(start + 1, cfg.weaponTintEndLevel);
        if (lvl <= start) return 0xFFFFFFFF; // 0~起始级:不透明纯白=保持黑白
        double t = (Math.log(Math.min(lvl, end)) - Math.log(start)) / (Math.log(end) - Math.log(start));
        float hue = (float) (((200.0 + 160.0 * t) % 360.0) / 360.0); // 冰蓝→蓝紫→品红→正红,无绿无黄
        float sat = (float) Math.min(1.0, t * 1.25);                  // 饱和度略快拉满 =「越来越鲜艳」
        return 0xFF000000 | hsvToRgb(hue, sat, 1.0f);                 // 补满 alpha,防隐形
    }

    /** HSV→RGB(入参均 0~1,返回 0xRRGGBB)。自实现,不依赖 MathHelper.hsvToRgb 的映射名。 */
    private static int hsvToRgb(float h, float s, float v) {
        int sector = ((int) (h * 6.0f)) % 6;
        float f = h * 6.0f - (float) Math.floor(h * 6.0f);
        float p = v * (1 - s), q = v * (1 - f * s), u = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (sector) {
            case 0 -> { r = v; g = u; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = u; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = u; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255.0f) << 16) | ((int) (g * 255.0f) << 8) | (int) (b * 255.0f);
    }

    /** m279/m284:升档称号,5 连一档共十阶——凌厉/迅猛/狂怒/无双/修罗/鬼神/灭世/弑神/超凡入圣/万象俱灭。 */
    private static String comboTitleFor(int tier) {
        return switch (Math.min(tier, 10)) {
            case 1 -> "凌厉";  case 2 -> "迅猛";  case 3 -> "狂怒";  case 4 -> "无双";
            case 5 -> "修罗";  case 6 -> "鬼神";  case 7 -> "灭世";  case 8 -> "弑神";
            case 9 -> "超凡入圣"; default -> "万象俱灭";
        };
    }

    /** m284:档位色十档(白→黄→金→橙红→亮紫→青→天蓝→品红→血红→白金),50 连(10 档)以上彩虹流转。 */
    private static int comboColor(int tier) {
        if (tier >= 10) {   // 彩虹流转:色相随时间循环,复用在树 hsvToRgb
            float hue = (System.currentTimeMillis() % 1800L) / 1800.0f;
            return 0xFF000000 | hsvToRgb(hue, 1.0f, 1.0f);
        }
        return switch (tier) {
            case 0 -> 0xFFFFFFFF; case 1 -> 0xFFFFFF55; case 2 -> 0xFFFFAA00;
            case 3 -> 0xFFFF5533; case 4 -> 0xFFFF55FF; case 5 -> 0xFF55FFEE;
            case 6 -> 0xFF55AAFF; case 7 -> 0xFFFF3399; case 8 -> 0xFFFF3020;
            default -> 0xFFFFF0C0;
        };
    }

    /** m346:技能CD HUD 单行绘制(右对齐;返回上一行 y)。就绪=金键绿字,冷却=灰键灰名橙秒+蓝色恢复进度线,
     *  未解锁=整行深灰。键位标签走 getBoundKeyLocalizedText(yarn method_16007 已核),玩家改键即时跟变。 */
    private static int drawSkillCdRow(net.minecraft.client.gui.DrawContext ctx, net.minecraft.client.font.TextRenderer tr,
                                      int right, int y, KeyBinding key, String name, int slot, boolean unlocked) {
        int left = skillCdLeft[slot];
        String keyLabel = "[" + key.getBoundKeyLocalizedText().getString() + "]";
        String status;
        int keyColor, nameColor, statusColor;
        if (!unlocked) {
            keyColor = 0xFF555555; nameColor = 0xFF555555; status = " 未解锁"; statusColor = 0xFF555555;
        } else if (left <= 0) {
            keyColor = 0xFFFFD700; nameColor = 0xFF55FF55; status = " 就绪"; statusColor = 0xFF55FF55;
        } else {
            keyColor = 0xFF888888; nameColor = 0xFF9AA6B2; status = " " + ((left + 19) / 20) + "s"; statusColor = 0xFFFFA040;
        }
        int wKey = tr.getWidth(keyLabel), wName = tr.getWidth(name);
        int total = wKey + 2 + wName + tr.getWidth(status);
        int x = right - total;
        ctx.drawTextWithShadow(tr, Text.literal(keyLabel), x, y, keyColor);
        ctx.drawTextWithShadow(tr, Text.literal(name), x + wKey + 2, y, nameColor);
        ctx.drawTextWithShadow(tr, Text.literal(status), x + wKey + 2 + wName, y, statusColor);
        // 冷却恢复进度线:行底 1px,底槽暗蓝、已恢复比例亮蓝从左涨满(峰值=本轮总CD,收包时记录)
        if (unlocked && left > 0 && skillCdPeak[slot] > 0) {
            float frac = 1f - (float) left / skillCdPeak[slot];
            int lw = (int) (total * Math.max(0f, Math.min(1f, frac)));
            ctx.fill(x, y + 9, x + total, y + 10, 0x40203040);
            if (lw > 0) ctx.fill(x, y + 9, x + lw, y + 10, 0xFF3AA0FF);
        }
        return y - 11;
    }

    /** m279:以当前矩阵原点为中心画 1px 方环(冲击环用;fill 为在树画法,无新 API)。 */
    private static void comboRing(net.minecraft.client.gui.DrawContext ctx, int r, int color) {
        ctx.fill(-r, -r, r, -r + 1, color);          // 上边
        ctx.fill(-r, r - 1, r, r, color);            // 下边
        ctx.fill(-r, -r + 1, -r + 1, r - 1, color);  // 左边
        ctx.fill(r - 1, -r + 1, r, r - 1, color);    // 右边
    }

    /** m345:背包左侧双列 11 钮装配(从 onInitializeClient 抽出,降复杂度热点;内容与抽出前逐行一致)。 */
    private static void addInventoryButtons(net.minecraft.client.MinecraftClient client,
                                            net.minecraft.client.gui.screen.Screen screen,
                                            int scaledWidth, int scaledHeight) {
                // 原版背包面板:宽 176、高 166,居中。左缘 = 屏宽/2 - 88,上缘 = 屏高/2 - 83。
                int guiLeft = scaledWidth / 2 - 88;
                int guiTop = scaledHeight / 2 - 83;
                int bw = 54, bh = 16, pitch = 19;        // 按钮宽/高/行距
                // m329:作者点名右侧与他模组 UI 打架且难看——11 钮全部收拢到面板**左侧双列**
                int bxIn  = guiLeft - bw - 4;             // 内列(贴面板)
                int bxOut = guiLeft - bw * 2 - 8;         // 外列
                int by = guiTop + 5;
                int rIn = 0, rOut = 0;

                // —— 内列(6):成长/装备/饰品/天赋/强化/兑换 ——
                Screens.getButtons(screen).add(new YongyeButton(bxIn, by + pitch * rIn++, bw, bh,
                        Text.literal("成长"), b -> client.setScreen(new StatsScreen(screen))));
                Screens.getButtons(screen).add(new YongyeButton(bxIn, by + pitch * rIn++, bw, bh,
                        Text.literal("装备"), b -> {
                            if (client.player == null) return;
                            ItemStack held = client.player.getMainHandStack();
                            if (!held.isEmpty() && EquipmentEnhancer.isEnhanceable(held.getItem())) {
                                client.setScreen(new WeaponInfoScreen(screen, held));
                            }
                        }));
                Screens.getButtons(screen).add(new YongyeButton(bxIn, by + pitch * rIn++, bw, bh,
                        Text.literal("饰品"), b -> ClientPlayNetworking.send(new com.yongye.network.OpenAccessoryPayload())));
                Screens.getButtons(screen).add(new YongyeButton(bxIn, by + pitch * rIn++, bw, bh,
                        Text.literal("天赋"), b -> client.setScreen(new TalentScreen(screen))));
                Screens.getButtons(screen).add(new YongyeButton(bxIn, by + pitch * rIn++, bw, bh,
                        Text.literal("强化"), b -> client.setScreen(new EnhanceSelectScreen(screen))));
                Screens.getButtons(screen).add(new YongyeButton(bxIn, by + pitch * rIn++, bw, bh,
                        Text.literal("兑换"), b -> client.setScreen(new ExchangeScreen(screen))));

                // —— 外列(6):学书/合书/任务/设置/转移/本命 ——
                Screens.getButtons(screen).add(new YongyeButton(bxOut, by + pitch * rOut++, bw, bh,
                        Text.literal("学书"), b -> ClientPlayNetworking.send(new com.yongye.network.UseAllBooksPayload())));
                Screens.getButtons(screen).add(new YongyeButton(bxOut, by + pitch * rOut++, bw, bh,
                        Text.literal("合书"), b -> ClientPlayNetworking.send(new com.yongye.network.MergeBooksPayload())));
                Screens.getButtons(screen).add(new YongyeButton(bxOut, by + pitch * rOut++, bw, bh,
                        Text.literal("任务"), b -> client.setScreen(new QuestBookScreen(screen))));
                Screens.getButtons(screen).add(new YongyeButton(bxOut, by + pitch * rOut++, bw, bh,
                        Text.literal("设置"), b -> client.setScreen(new VisualFxScreen(screen))));
                // 转移:强化转移(主手来源→副手目标;m340,与 /yongye transfer 同款)
                Screens.getButtons(screen).add(new YongyeButton(bxOut, by + pitch * rOut++, bw, bh,
                        Text.literal("转移"), b -> {
                            if (client.player != null) client.player.networkHandler.sendCommand("yongye transfer");
                        }));
                com.yongye.item.PlayerClass pc = com.yongye.item.PlayerClass.byId(ClientStats.className);
                String classLabel = pc != null ? "本命·" + pc.cn : "无职业";
                Screens.getButtons(screen).add(new YongyeButton(bxOut, by + pitch * rOut++, bw, bh,
                        Text.literal(classLabel), b -> client.setScreen(new StatsScreen(screen))));
    }
}
