package com.yongye.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 视觉·手感设置屏(m317,作者:「震动这些设置、视觉效果的设置,应该直接加到背包旁边一个设置」)。
 * 从背包面板右侧「设置」按钮进入,把散在 Debug 菜单/config 里的**观感类**开关数值集中一屏:
 * 镜头(震动/FOV 冲击/顿帧)、闪光粒子音效、刀光、姿态(全身发力/疾跑姿态)、怪物观感(红眼/紫气)。
 *
 * 机制与 DebugScreen 同源:每个按钮点击 = sendCommand("yongye config set <字段> <值>"),
 * 服务端即时生效并写盘 config/yongye.json(单机同 JVM,客户端渲染下一帧就用新值);零新 API 面。
 * 布局也照抄 DebugScreen 的页签+分区网格(1.21.1 clearAndInit 标准翻页),按钮=YongyeButton 主题样式。
 * 关闭返回父界面(背包),shouldPause=false 方便边调边看效果。
 */
@Environment(EnvType.CLIENT)
public class VisualFxScreen extends Screen {

    // —— 布局参数(按钮 4 列网格,与 DebugScreen 同规格)——
    private static final int COLS = 4;
    private static final int BTN_W = 96;
    private static final int BTN_H = 18;
    private static final int GAP_X = 4;
    private static final int GAP_Y = 3;
    private static final int HEADER_H = 12;

    private final Screen parent;
    private int page = 0;

    private final List<String> headerTexts = new ArrayList<>();
    private final List<Integer> headerYs = new ArrayList<>();

    private record Btn(String label, String cmd) {}
    private record Section(String title, Btn[] btns) {}
    private record Page(String tab, Section[] sections) {}

    private static final Page[] PAGES = new Page[]{
            new Page("镜头·特效", new Section[]{
                    new Section("特效质量总档(m381 统一预算闸:量/寿命/距离/分段整体降级)", new Btn[]{
                            new Btn("质量·全关0", "yongye config set fxQuality 0"),
                            new Btn("质量·低1", "yongye config set fxQuality 1"),
                            new Btn("质量·中2", "yongye config set fxQuality 2"),
                            new Btn("质量·高3(默认)", "yongye config set fxQuality 3"),
                    }),
                    new Section("镜头(打击震动 / BOSS 登场震 / FOV 冲击 / 打击顿帧)", new Btn[]{
                            new Btn("震动·关", "yongye config set combatFxShakeScale 0"),
                            new Btn("震动·轻0.5", "yongye config set combatFxShakeScale 0.5"),
                            new Btn("震动·默认1", "yongye config set combatFxShakeScale 1.0"),
                            new Btn("震动·重1.5", "yongye config set combatFxShakeScale 1.5"),
                            new Btn("登场震·关", "yongye config set bossEntranceShake 0"),
                            new Btn("登场震·默认1.4", "yongye config set bossEntranceShake 1.4"),
                            new Btn("登场震·强2.2", "yongye config set bossEntranceShake 2.2"),
                            new Btn("FOV冲击·关", "yongye config set combatFxFovKick 0"),
                            new Btn("FOV冲击·默认1", "yongye config set combatFxFovKick 1.0"),
                            new Btn("FOV冲击·强1.5", "yongye config set combatFxFovKick 1.5"),
                            new Btn("顿帧·关", "yongye config set combatFxHitstopScale 0"),
                            new Btn("顿帧·默认1", "yongye config set combatFxHitstopScale 1.0"),
                            new Btn("顿帧·强1.5", "yongye config set combatFxHitstopScale 1.5"),
                    }),
                    new Section("闪光 / 粒子 / 音效", new Btn[]{
                            new Btn("战斗粒子·开", "yongye config set combatFxParticles true"),
                            new Btn("战斗粒子·关", "yongye config set combatFxParticles false"),
                            new Btn("夜尘·开", "yongye config set enableNightAmbientParticles true"),
                            new Btn("夜尘·关", "yongye config set enableNightAmbientParticles false"),
                            new Btn("夜尘·淡0.5", "yongye config set nightAmbientDensity 0.5"),
                            new Btn("夜尘·默认1", "yongye config set nightAmbientDensity 1.0"),
                            new Btn("夜尘·浓1.5", "yongye config set nightAmbientDensity 1.5"),
                            new Btn("击杀闪光·开", "yongye config set combatFxKillFlash true"),
                            new Btn("击杀闪光·关", "yongye config set combatFxKillFlash false"),
                            new Btn("击杀音效·开", "yongye config set combatFxKillSound true"),
                            new Btn("击杀音效·关", "yongye config set combatFxKillSound false"),
                            new Btn("伤害飘字·开", "yongye config set enableDamageNumbers true"),
                            new Btn("伤害飘字·关", "yongye config set enableDamageNumbers false"),
                            new Btn("飘字·小0.7", "yongye config set damageNumberScale 0.7"),
                            new Btn("飘字·默认1", "yongye config set damageNumberScale 1.0"),
                            new Btn("飘字·大1.4", "yongye config set damageNumberScale 1.4"),
                            new Btn("受击方向·开", "yongye config set enableHurtDirectionFx true"),
                            new Btn("受击方向·关", "yongye config set enableHurtDirectionFx false"),
                            new Btn("掉落光柱·开", "yongye config set enableLootBeam true"),
                            new Btn("掉落光柱·关", "yongye config set enableLootBeam false"),
                            new Btn("永夜转场·开", "yongye config set enableNightfallTransition true"),
                            new Btn("永夜转场·关", "yongye config set enableNightfallTransition false"),
                            new Btn("转场·柔0.5", "yongye config set transitionIntensity 0.5"),
                            new Btn("转场·默认1", "yongye config set transitionIntensity 1.0"),
                            new Btn("转场·重1.5", "yongye config set transitionIntensity 1.5"),
                            new Btn("弱闪光·开", "yongye config set reduceScreenFlash true"),
                            new Btn("弱闪光·关", "yongye config set reduceScreenFlash false"),
                            new Btn("多杀弹字·开", "yongye config set enableMultiKillFx true"),
                            new Btn("多杀弹字·关", "yongye config set enableMultiKillFx false"),
                            new Btn("命中音分层·开", "yongye config set enableCombatHitSound true"),
                            new Btn("命中音分层·关", "yongye config set enableCombatHitSound false"),
                    }),
                    new Section("刀光(斩击轨迹)", new Btn[]{
                            new Btn("刀光·开", "yongye config set enableSlashFx true"),
                            new Btn("刀光·关", "yongye config set enableSlashFx false"),
                            new Btn("贴图刀光·开", "yongye config set slashFxTextured true"),
                            new Btn("贴图刀光·关", "yongye config set slashFxTextured false"),
                            new Btn("大小0.75", "yongye config set slashFxSize 0.75"),
                            new Btn("大小·默认1", "yongye config set slashFxSize 1.0"),
                            new Btn("大小1.3", "yongye config set slashFxSize 1.3"),
                            new Btn("亮度0.5", "yongye config set slashFxAlpha 0.5"),
                            new Btn("亮度·默认0.75", "yongye config set slashFxAlpha 0.75"),
                            new Btn("亮度1.0", "yongye config set slashFxAlpha 1.0"),
                    }),
            }),
            new Page("姿态·怪物", new Section[]{
                    new Section("战斗姿态(拔刀七式 / 全身发力)", new Btn[]{
                            new Btn("拔刀姿态·开", "yongye config set slashFxPose true"),
                            new Btn("拔刀姿态·关", "yongye config set slashFxPose false"),
                            new Btn("全身发力·开", "yongye config set slashFxBends true"),
                            new Btn("全身发力·关", "yongye config set slashFxBends false"),
                            new Btn("姿态幅度1.0", "yongye config set slashFxPoseScale 1.0"),
                            new Btn("幅度·默认1.35", "yongye config set slashFxPoseScale 1.35"),
                            new Btn("姿态幅度1.8", "yongye config set slashFxPoseScale 1.8"),
                    }),
                    new Section("疾跑姿态(m316 MoBends 式:拧身前扑+泵臂)", new Btn[]{
                            new Btn("跑步姿态·开", "yongye config set sprintPose true"),
                            new Btn("跑步姿态·关", "yongye config set sprintPose false"),
                            new Btn("跑姿·收敛0.7", "yongye config set sprintPoseScale 0.7"),
                            new Btn("跑姿·默认1.0", "yongye config set sprintPoseScale 1.0"),
                            new Btn("跑姿·夸张1.4", "yongye config set sprintPoseScale 1.4"),
                            new Btn("跑时武器·拖刀", "yongye config set sprintWeaponStyle 2"),
                            new Btn("跑时武器·背后", "yongye config set sprintWeaponStyle 1"),
                            new Btn("跑时武器·原版", "yongye config set sprintWeaponStyle 0"),
                    }),
                    new Section("怪物观感(红眼 / 紫气)", new Btn[]{
                            new Btn("怪物红眼·开", "yongye config set zombieRedEyes true"),
                            new Btn("怪物红眼·关", "yongye config set zombieRedEyes false"),
                            new Btn("怪物紫气·开", "yongye config set zombiePurpleAura true"),
                            new Btn("怪物紫气·关", "yongye config set zombiePurpleAura false"),
                            new Btn("紫气·淡0.5", "yongye config set mobAuraScale 0.5"),
                            new Btn("紫气·默认1", "yongye config set mobAuraScale 1.0"),
                            new Btn("紫气·浓2", "yongye config set mobAuraScale 2.0"),
                    }),
            }),
            // m358 界面·HUD 页(作者:「技能CD能不能在设置里调整」)——即点即改,HUD 每帧读配置立即生效
            new Page("界面·HUD", new Section[]{
                    new Section("UI 动效(界面淡入 / 按钮悬停过渡·按压下沉·入场上浮)", new Btn[]{
                            new Btn("UI动效·开", "yongye config set enableUiFx true"),
                            new Btn("UI动效·关", "yongye config set enableUiFx false"),
                    }),
                    new Section("技能CD常显(R/G/V·大招·小技能,m353 玻璃芯片)", new Btn[]{
                            new Btn("CD显示·开", "yongye config set enableSkillCdHud true"),
                            new Btn("CD显示·关", "yongye config set enableSkillCdHud false"),
                            new Btn("水平·左移40", "yongye config set skillCdHudOffsetX -40"),
                            new Btn("水平·左移20", "yongye config set skillCdHudOffsetX -20"),
                            new Btn("水平·默认", "yongye config set skillCdHudOffsetX 0"),
                            new Btn("水平·右移20", "yongye config set skillCdHudOffsetX 20"),
                            new Btn("水平·右移40", "yongye config set skillCdHudOffsetX 40"),
                            new Btn("垂直·上移40", "yongye config set skillCdHudOffsetY -40"),
                            new Btn("垂直·上移20", "yongye config set skillCdHudOffsetY -20"),
                            new Btn("垂直·默认", "yongye config set skillCdHudOffsetY 0"),
                            new Btn("垂直·下移20", "yongye config set skillCdHudOffsetY 20"),
                    }),
                    new Section("战况看板(第N天·击杀·预告)停靠位", new Btn[]{
                            new Btn("看板·开", "yongye config set enableHudInfoPanel true"),
                            new Btn("看板·关", "yongye config set enableHudInfoPanel false"),
                            new Btn("主线行·开", "yongye config set enableMainQuestHud true"),
                            new Btn("主线行·关", "yongye config set enableMainQuestHud false"),
                            new Btn("停·左上", "yongye config set hudInfoAnchor 1"),
                            new Btn("停·左中", "yongye config set hudInfoAnchor 0"),
                            new Btn("停·左下", "yongye config set hudInfoAnchor 2"),
                            new Btn("停·右上", "yongye config set hudInfoAnchor 3"),
                            new Btn("停·右中", "yongye config set hudInfoAnchor 4"),
                            new Btn("停·右下", "yongye config set hudInfoAnchor 5"),
                    }),
                    new Section("BOSS血条大小(m365 整体缩放,即点即改)", new Btn[]{
                            new Btn("血条·特小0.5", "yongye config set bossBarScale 0.5"),
                            new Btn("血条·小0.6", "yongye config set bossBarScale 0.6"),
                            new Btn("血条·默认0.7", "yongye config set bossBarScale 0.7"),
                            new Btn("血条·较大0.85", "yongye config set bossBarScale 0.85"),
                            new Btn("血条·原大1.0", "yongye config set bossBarScale 1.0"),
                    }),
                    new Section("天象视觉(m352 事件限定红月/绿雨)", new Btn[]{
                            new Btn("事件天象·开", "yongye config set enableEventSkyVisuals true"),
                            new Btn("事件天象·关", "yongye config set enableEventSkyVisuals false"),
                    }),
            }),
    };

    public VisualFxScreen(Screen parent) {
        super(Text.literal("夜蚀 · 视觉设置"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        headerTexts.clear();
        headerYs.clear();

        int gridW = COLS * BTN_W + (COLS - 1) * GAP_X;
        int x0 = (this.width - gridW) / 2;

        // —— 顶部页签行(与 DebugScreen 同机制:切页=clearAndInit 重建)——
        int tabY = 26;
        int tabH = 16;
        int tabGap = 2;
        int tabW = (gridW - (PAGES.length - 1) * tabGap) / PAGES.length;
        for (int i = 0; i < PAGES.length; i++) {
            final int idx = i;
            int tx = x0 + i * (tabW + tabGap);
            ButtonWidget tab = ButtonWidget.builder(Text.literal(PAGES[i].tab()), b -> {
                this.page = idx;
                this.clearAndInit();
            }).dimensions(tx, tabY, tabW, tabH).build();
            tab.active = (i != page);
            addDrawableChild(tab);
        }

        // —— 当前页内容 ——
        int y = tabY + tabH + 8;
        for (Section s : PAGES[page].sections()) {
            y = section(x0, y, s.title(), s.btns());
        }

        // 返回背包(底部居中)
        addDrawableChild(ButtonWidget.builder(Text.literal("返回背包"), b -> close())
                .dimensions(this.width / 2 - 50, Math.min(this.height - 24, y + 6), 100, 20).build());
    }

    /** 摆一个分组:登记标题(render 时绘制)+ 按钮(超过 COLS 个自动换行);返回下一组起始 y。 */
    private int section(int x0, int y, String title, Btn[] btns) {
        headerTexts.add(title);
        headerYs.add(y);
        int by = y + HEADER_H;
        for (int i = 0; i < btns.length; i++) {
            int col = i % COLS;
            int rowIdx = i / COLS;
            int bx = x0 + col * (BTN_W + GAP_X);
            int byy = by + rowIdx * (BTN_H + GAP_Y);
            final String cmd = btns[i].cmd();
            addDrawableChild(new YongyeButton(bx, byy, BTN_W, BTN_H,
                    Text.literal(btns[i].label()), b -> run(cmd)));
        }
        int rows = (btns.length + COLS - 1) / COLS;
        return by + rows * (BTN_H + GAP_Y);
    }

    /** 替玩家执行一条命令(不含前导斜杠;服务端 config set 即时生效并写盘)。 */
    private void run(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendCommand(command);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 视觉设置 · " + PAGES[page].tab() + " ◆").formatted(Formatting.GOLD),
                this.width / 2, 12, 0xFFFFD700);

        int gridW = COLS * BTN_W + (COLS - 1) * GAP_X;
        int x0 = (this.width - gridW) / 2;
        for (int i = 0; i < headerTexts.size(); i++) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal(headerTexts.get(i)).formatted(Formatting.AQUA),
                    x0, headerYs.get(i), 0xFF55FFFF);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);   // 返回背包
    }

    /** 不暂停游戏:边调边看效果(震动/刀光/跑姿即点即看)。 */
    @Override
    public boolean shouldPause() {
        return false;
    }
}
