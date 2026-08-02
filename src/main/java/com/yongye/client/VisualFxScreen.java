package com.yongye.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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

    // ===== m404 数值微调页(作者:「UI 的那些调整都可以输入数值或者滑动条来调整」)=====
    // 每项 = 滑动条(拖动即调,松手/按键生效)+ 输入框(回车=精确设置);min/max=滑条量程,dec=小数位(0=整数)。
    private record Sld(String label, String key, double min, double max, int dec) {}
    private record SldSection(String title, Sld[] slds) {}
    private static final SldSection[] SLIDER_SECTIONS = new SldSection[]{
            new SldSection("HUD 位置与透明(技能CD / 战况看板 / BOSS血条)", new Sld[]{
                    new Sld("CD横移", "skillCdHudOffsetX", -300, 300, 0),
                    new Sld("CD纵移", "skillCdHudOffsetY", -300, 300, 0),
                    new Sld("CD透明", "skillCdHudAlpha", 40, 255, 0),
                    new Sld("看板横移", "hudInfoOffsetX", -300, 300, 0),
                    new Sld("看板纵移", "hudInfoOffsetY", -300, 300, 0),
                    new Sld("血条缩放", "bossBarScale", 0.3, 1.5, 2),
                    new Sld("安全边距X", "hudSafeMarginX", 0, 80, 0),
                    new Sld("安全边距Y", "hudSafeMarginY", 0, 80, 0),
            }),
            new SldSection("镜头与特效强度", new Sld[]{
                    new Sld("打击震动", "combatFxShakeScale", 0, 2, 2),
                    new Sld("登场震", "bossEntranceShake", 0, 3, 2),
                    new Sld("FOV冲击", "combatFxFovKick", 0, 2, 2),
                    new Sld("顿帧", "combatFxHitstopScale", 0, 2, 2),
                    new Sld("转场强度", "transitionIntensity", 0, 2, 2),
                    new Sld("飘字大小", "damageNumberScale", 0.5, 2, 2),
                    new Sld("暴击字号", "damageNumberCritScale", 1, 3, 2),
                    new Sld("起手光晕", "skillCastFxIntensity", 0, 2, 2),
                    new Sld("日志行数", "combatLogLines", 3, 12, 0),
                    new Sld("夜尘密度", "nightAmbientDensity", 0, 2, 2),
                    new Sld("夜声音量", "nightAmbientSoundVolume", 0, 1, 2),
            }),
            new SldSection("刀光·姿态·背挂", new Sld[]{
                    new Sld("刀光大小", "slashFxSize", 0.5, 2, 2),
                    new Sld("姿态幅度", "slashFxPoseScale", 0.5, 2, 2),
                    new Sld("背挂角度", "weaponBackAngleDeg", -180, 180, 0),
                    new Sld("背挂大小", "weaponBackScale", 0.3, 1.5, 2),
                    new Sld("背挂下移", "weaponBackDownOff", 0, 1, 2),
                    new Sld("背挂贴背", "weaponBackBackOff", 0, 1, 2),
            }),
    };
    /** 数值微调页的输入框(key↔控件,回车应用/滑条推值时回写)。 */
    private final List<Object[]> numFields = new ArrayList<>();   // [Sld, TextFieldWidget]

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
                            new Btn("低刺激整档·开", "yongye config set lowStimulusMode true"),
                            new Btn("低刺激整档·关", "yongye config set lowStimulusMode false"),
                            new Btn("色盲记号·开", "yongye config set enableColorblindMarks true"),
                            new Btn("色盲记号·关", "yongye config set enableColorblindMarks false"),
                            new Btn("战斗日志·开", "yongye config set enableCombatLog true"),
                            new Btn("战斗日志·关", "yongye config set enableCombatLog false"),
                            new Btn("天赋脉冲·开", "yongye config set enableTalentPulseFx true"),
                            new Btn("天赋脉冲·关", "yongye config set enableTalentPulseFx false"),
                            new Btn("强化演出·开", "yongye config set enableEnhanceFx true"),
                            new Btn("强化演出·关", "yongye config set enableEnhanceFx false"),
                            new Btn("主菜单动效·开", "yongye config set enableTitleFx true"),
                            new Btn("主菜单动效·关", "yongye config set enableTitleFx false"),
                            new Btn("起手光晕·开", "yongye config set enableSkillCastFx true"),
                            new Btn("起手光晕·关", "yongye config set enableSkillCastFx false"),
                            new Btn("飘字合并·开", "yongye config set enableDamageNumberMerge true"),
                            new Btn("飘字合并·关", "yongye config set enableDamageNumberMerge false"),
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
                            new Btn("死亡转场·开", "yongye config set enableDeathTransition true"),
                            new Btn("死亡转场·关", "yongye config set enableDeathTransition false"),
                            new Btn("怪物血条·开", "yongye config set enableMobHealthBar true"),
                            new Btn("怪物血条·关", "yongye config set enableMobHealthBar false"),
                            new Btn("拾取通知·开", "yongye config set enablePickupNotice true"),
                            new Btn("拾取通知·关", "yongye config set enablePickupNotice false"),
                            new Btn("讨伐演出·开", "yongye config set enableBossKillFx true"),
                            new Btn("讨伐演出·关", "yongye config set enableBossKillFx false"),
                            new Btn("夜声·开", "yongye config set enableNightAmbientSound true"),
                            new Btn("夜声·关", "yongye config set enableNightAmbientSound false"),
                            new Btn("夜声·轻0.3", "yongye config set nightAmbientSoundVolume 0.3"),
                            new Btn("夜声·默认0.6", "yongye config set nightAmbientSoundVolume 0.6"),
                            new Btn("夜声·响1.0", "yongye config set nightAmbientSoundVolume 1.0"),
                            new Btn("音效并发·开", "yongye config set enableSoundConcurrency true"),
                            new Btn("音效并发·关", "yongye config set enableSoundConcurrency false"),
                            new Btn("同类限流·严1", "yongye config set soundSameIdMaxPerWindow 1"),
                            new Btn("同类限流·默认2", "yongye config set soundSameIdMaxPerWindow 2"),
                            new Btn("同类限流·宽4", "yongye config set soundSameIdMaxPerWindow 4"),
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
                    new Section("武器架势(m260 站姿;m396 起仅第三人称生效,第一人称恒原版)", new Btn[]{
                            new Btn("战斗站姿·开", "yongye config set slashFxBattleStance true"),
                            new Btn("战斗站姿·关", "yongye config set slashFxBattleStance false"),
                            new Btn("格挡姿态·开", "yongye config set slashFxGuardPose true"),
                            new Btn("格挡姿态·关", "yongye config set slashFxGuardPose false"),
                            new Btn("真动作库·开", "yongye config set slashFxAnimLib true"),
                            new Btn("真动作库·关", "yongye config set slashFxAnimLib false"),
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
                    new Section("背挂微调(跑时武器=背后时;m394 截图回修,即点即看)", new Btn[]{
                            new Btn("角·斜挎-80", "yongye config set weaponBackAngleDeg -80"),
                            new Btn("角·陡挎-55", "yongye config set weaponBackAngleDeg -55"),
                            new Btn("角·旧-125", "yongye config set weaponBackAngleDeg -125"),
                            new Btn("大小·0.6", "yongye config set weaponBackScale 0.6"),
                            new Btn("大小·0.75", "yongye config set weaponBackScale 0.75"),
                            new Btn("大小·0.9", "yongye config set weaponBackScale 0.9"),
                            new Btn("位·偏上0.18", "yongye config set weaponBackDownOff 0.18"),
                            new Btn("位·居中0.30", "yongye config set weaponBackDownOff 0.30"),
                            new Btn("位·偏腰0.42", "yongye config set weaponBackDownOff 0.42"),
                            new Btn("贴背·近0.16", "yongye config set weaponBackBackOff 0.16"),
                            new Btn("贴背·中0.22", "yongye config set weaponBackBackOff 0.22"),
                            new Btn("贴背·远0.30", "yongye config set weaponBackBackOff 0.30"),
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
                            new Btn("页签过渡·开", "yongye config set enableTabSwitchFx true"),
                            new Btn("页签过渡·关", "yongye config set enableTabSwitchFx false"),
                            new Btn("HUD微动·开", "yongye config set enableHudMicroFx true"),
                            new Btn("HUD微动·关", "yongye config set enableHudMicroFx false"),
                    }),
                    new Section("技能CD(m405 默认方块:仅冷却中显示,面板上方居中;偏移/透明在数值微调页)", new Btn[]{
                            new Btn("CD显示·开", "yongye config set enableSkillCdHud true"),
                            new Btn("CD显示·关", "yongye config set enableSkillCdHud false"),
                            new Btn("样式·方块CD", "yongye config set skillCdHudStyle 0"),
                            new Btn("样式·文字列表", "yongye config set skillCdHudStyle 1"),
                            new Btn("样式·玻璃芯片", "yongye config set skillCdHudStyle 2"),
                            new Btn("停·右下(默认)", "yongye config set skillCdHudAnchor 0"),
                            new Btn("停·左下", "yongye config set skillCdHudAnchor 1"),
                            new Btn("停·右中", "yongye config set skillCdHudAnchor 2"),
                            new Btn("停·左中", "yongye config set skillCdHudAnchor 3"),
                            new Btn("停·右上", "yongye config set skillCdHudAnchor 4"),
                            new Btn("停·左上", "yongye config set skillCdHudAnchor 5"),
                            new Btn("停·面板左(旧)", "yongye config set skillCdHudAnchor 6"),
                            new Btn("透明·淡", "yongye config set skillCdHudAlpha 120"),
                            new Btn("透明·中(默认)", "yongye config set skillCdHudAlpha 165"),
                            new Btn("透明·浓", "yongye config set skillCdHudAlpha 220"),
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
        numFields.clear();

        int gridW = COLS * BTN_W + (COLS - 1) * GAP_X;
        int x0 = (this.width - gridW) / 2;

        // —— 顶部页签行(与 DebugScreen 同机制:切页=clearAndInit 重建;末位=m404 数值微调页)——
        int tabY = 26;
        int tabH = 16;
        int tabGap = 2;
        int tabCount = PAGES.length + 1;
        int tabW = (gridW - (tabCount - 1) * tabGap) / tabCount;
        for (int i = 0; i < tabCount; i++) {
            final int idx = i;
            int tx = x0 + i * (tabW + tabGap);
            String tabName = i < PAGES.length ? PAGES[i].tab() : "数值微调";
            ButtonWidget tab = ButtonWidget.builder(Text.literal(tabName), b -> {
                TabSwitchFx.trigger(this, idx - this.page);  // m391 页签过渡
                this.page = idx;
                this.clearAndInit();
            }).dimensions(tx, tabY, tabW, tabH).build();
            tab.active = (i != page);
            addDrawableChild(tab);
        }

        // —— 当前页内容 ——
        int y = tabY + tabH + 8;
        if (page < PAGES.length) {
            for (Section s : PAGES[page].sections()) {
                y = section(x0, y, s.title(), s.btns());
            }
        } else {
            y = buildSliderPage(x0, y, gridW);   // m404 数值微调页
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

    // ==================== m404 数值微调页 ====================

    /** 摆滑条页:每行两对「滑条+输入框」;返回下一组起始 y。 */
    private int buildSliderPage(int x0, int y, int gridW) {
        headerTexts.add("拖动滑条即调即看;右侧框输入数值后按回车 = 精确设置(自动钳到量程)");
        headerYs.add(y);
        y += HEADER_H;
        int pairW = (gridW - 8) / 2;          // 一行两对
        int fieldW = 52;
        int sliderW = pairW - fieldW - 2;
        int rowH = BTN_H + GAP_Y;
        for (SldSection sec : SLIDER_SECTIONS) {
            headerTexts.add(sec.title());
            headerYs.add(y);
            int by = y + HEADER_H;
            Sld[] slds = sec.slds();
            for (int i = 0; i < slds.length; i++) {
                int col = i % 2;
                int px = x0 + col * (pairW + 8);
                int py = by + (i / 2) * rowH;
                Sld s = slds[i];
                double cur = cfgGet(s.key());
                addDrawableChild(new CfgSlider(px, py, sliderW, BTN_H, s, cur));
                TextFieldWidget tf = new TextFieldWidget(this.textRenderer,
                        px + sliderW + 2, py + 1, fieldW, BTN_H - 2, Text.literal(s.key()));
                tf.setMaxLength(10);
                tf.setText(fmtVal(cur, s.dec()));
                addDrawableChild(tf);
                numFields.add(new Object[]{s, tf});
            }
            y = by + ((slds.length + 1) / 2) * rowH;
        }
        return y;
    }

    /** 数值格式化:dec=0 整数,否则固定小数位(Locale.ROOT 防区域逗号)。 */
    private static String fmtVal(double v, int dec) {
        return dec == 0 ? String.valueOf(Math.round(v))
                : String.format(java.util.Locale.ROOT, "%." + dec + "f", v);
    }

    /** 反射读客户端配置当前值(单机/局域网同 JVM=config set 改的就是这个对象,读回即最新)。 */
    private static double cfgGet(String key) {
        try {
            return ((Number) com.yongye.YongyeConfig.class.getField(key)
                    .get(com.yongye.YongyeConfig.get())).doubleValue();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 本地即时回写(int/double 分型),随后照旧发 config set 走服务端权威+写盘;双写同值幂等。 */
    private void cfgApply(String key, String val) {
        try {
            var f = com.yongye.YongyeConfig.class.getField(key);
            if (f.getType() == int.class) f.setInt(com.yongye.YongyeConfig.get(), (int) Math.round(Double.parseDouble(val)));
            else if (f.getType() == double.class) f.setDouble(com.yongye.YongyeConfig.get(), Double.parseDouble(val));
        } catch (Exception ignored) {}
        run("yongye config set " + key + " " + val);
    }

    /** 回车应用聚焦的输入框:钳量程→格式化→应用→重建页面刷新滑条位置。 */
    private boolean applyFocusedField() {
        for (Object[] pair : numFields) {
            TextFieldWidget tf = (TextFieldWidget) pair[1];
            if (!tf.isFocused()) continue;
            Sld s = (Sld) pair[0];
            try {
                double v = Double.parseDouble(tf.getText().trim());
                v = Math.max(s.min(), Math.min(s.max(), v));
                cfgApply(s.key(), fmtVal(v, s.dec()));
                this.clearAndInit();   // 刷新:滑条跳到新位置,输入框显示钳后值
            } catch (NumberFormatException e) {
                tf.setText(fmtVal(cfgGet(s.key()), s.dec()));   // 乱输=回显当前值
            }
            return true;
        }
        return false;
    }

    /** 回车(主键盘 257 / 小键盘 335)且焦点在数值框 → 应用;其余照旧。 */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && applyFocusedField()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** m404 配置滑条:标签内嵌当前值;拖动实时刷标签,松手/键盘步进才发 config set(防拖动刷命令);
     *  推值后回写右侧输入框保持同步。SliderWidget 构造器与 applyValue/updateMessage 已核 yarn 1.21.1。 */
    private class CfgSlider extends SliderWidget {
        private final Sld s;
        private String lastSent;

        CfgSlider(int x, int y, int w, int h, Sld s, double cur) {
            super(x, y, w, h, Text.literal(""),
                    (Math.max(s.min(), Math.min(s.max(), cur)) - s.min()) / (s.max() - s.min()));
            this.s = s;
            this.lastSent = fmtVal(cur, s.dec());
            updateMessage();
        }

        private String fmt() {
            return fmtVal(s.min() + this.value * (s.max() - s.min()), s.dec());
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(s.label() + " " + fmt()));
        }

        @Override
        protected void applyValue() { /* 拖动中只刷标签(updateMessage),落值在 push() */ }

        private void push() {
            String v = fmt();
            if (v.equals(lastSent)) return;
            lastSent = v;
            cfgApply(s.key(), v);
            for (Object[] pair : numFields) {           // 回写配对输入框
                if (pair[0] == s) ((TextFieldWidget) pair[1]).setText(v);
            }
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            push();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            boolean r = super.keyPressed(keyCode, scanCode, modifiers);
            if (r) push();
            return r;
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 视觉设置 · " + (page < PAGES.length ? PAGES[page].tab() : "数值微调") + " ◆").formatted(Formatting.GOLD),
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
