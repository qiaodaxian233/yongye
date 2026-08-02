package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AdjustmentModifier;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.core.util.Vec3f;

import java.util.Optional;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 真·骨骼级拔刀动作(m254)——桥接 KosmX 的 player-animator(2.0.1+1.21.1,JiJ 内置)。
 * 作者点名「直接抄拔刀剑」:SlashBlade-Refabricated 的动作正是这个库驱动;动作文件不抄它的
 * VMD(MMD 社区动作,版权来源存疑),改用库原生 emotecraft 关键帧 JSON 自制七式,效果同级、来源干净。
 *
 * 结构:
 *  - register():玩家构造时挂一个空 ModifierLayer(优先级 1000);由 YongyeClient 在 try/catch(Throwable)
 *    里调——库缺失/版本冲突时本类压根加载不了(NoClassDefFoundError),外层兜住即整体退回程序化姿态,
 *    这也是所有 player-animator 引用**只准写在本类**的原因。
 *  - playFor():按式号取 assets/yongye/player_animations/ 里的动画(注册表键=JSON 的 name 字段),
 *    2t 快融入替换播放;动画末帧全部编到 0 姿态,结束无跳变。返回 false=动画缺失,调用方退回程序化姿态。
 *
 * 调参:动作 JSON 支持 F3+T 资源重载热更新(PlayerAnimationRegistry 挂资源加载回调),改角度不用重启。
 */
public final class SlashAnimManager {
    private SlashAnimManager() {}

    /** 动作层与关联数据的键。 */
    private static final Identifier LAYER_ID = Identifier.of(Yongye.MOD_ID, "slash_anim");
    /** m260:站姿层(战斗待机/格挡姿态,循环动画;优先级低于挥砍层,攻击瞬间自动被盖过)。 */
    private static final Identifier STANCE_ID = Identifier.of(Yongye.MOD_ID, "stance_anim");
    private static int stanceState = 0; // 0=无 1=战斗站姿 2=格挡姿态(本地玩家)

    /** 式号(与 SlashFxManager 的 variant 对齐)→ 动画名:0~3 地面连击 / 4 空中回旋 / 5 疾跑突刺 / 6 潜行居合。 */
    private static final String[] VARIANT_ANIM = {
            "yongye_slash_1", "yongye_slash_2", "yongye_slash_3", "yongye_slash_4",
            "yongye_slash_aerial", "yongye_slash_lunge", "yongye_slash_iai"
    };

    /** 每个客户端玩家构造时自动挂一个空动作层;工厂注册的层同时进 playerAssociatedData,playFor 里按键取回。 */
    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000,
                player -> {
                    ModifierLayer<IAnimation> layer = new ModifierLayer<>();
                    layer.addModifier(aimAdjust(player), 0);   // m437 俯仰跟随(必须排在关键帧播放器之前=idx 0)
                    return layer;
                });
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(STANCE_ID, 900,
                player -> {
                    ModifierLayer<IAnimation> layer = new ModifierLayer<>();
                    layer.addModifier(aimAdjust(player), 0);   // m437:站姿同样跟随俯仰(抬头挺身/低头压刀)
                    return layer;
                }); // m260 站姿层(900<1000,挥砍盖站姿)
        Yongye.LOGGER.info("[夜蚀] 真·拔刀动作库已桥接(player-animator)");
    }

    /**
     * m437 俯仰跟随(学 Celestisynth 的 CSAnimator,MIT,已挂名 THIRD_PARTY_NOTICES)。
     *
     * <p><b>解决什么</b>:m254 起七式是**烘焙好的关键帧**,不管你抬头看天还是低头劈地,挥出来的角度
     * 一模一样——朝天挥砍时刀却平着扫,是「动作和瞄准脱节」。Celestisynth 的做法是在关键帧播放器
     * <b>之上</b>再叠一层程序化的 {@code AdjustmentModifier},按玩家实时 pitch 逐骨骼补角度:动作文件保持通用,
     * 运行时才长出「朝你看的方向出刀」的观感。库作者自己的 javadoc 示例就是这个用法(adjusting the
     * vertical angle of a custom attack animation),等于官方认证的正解。
     *
     * <p><b>本实现的分配</b>(比抄来的更克制,只补三处、系数按夜蚀的七式调):
     * 持械臂吃满 pitch(刀跟着视线走)、副手臂吃四成(平衡不僵)、躯干吃三成反向(抬头挺胸/低头压身),
     * <b>头部一律不补</b>——原版 head 已经跟着视线转了,再补会变成缩脖子/仰过头。
     * 腿与其余骨骼返回 {@code Optional.empty()} 走原动画(库约定:空=该部位不调整)。
     *
     * <p>幅度走配置 slashFxAimFollow(0=关,回 m254 纯关键帧;默 1.0),越界钳 [0,2]。
     * 修饰器是<b>惰性求值</b>的——lambda 每帧被调用,读的都是当帧 pitch,不需要自己 tick。
     */
    private static AdjustmentModifier aimAdjust(AbstractClientPlayerEntity player) {
        return new AdjustmentModifier(partName -> {
            float k = (float) Math.max(0.0, Math.min(2.0, YongyeConfig.get().slashFxAimFollow));
            if (k <= 0f) return Optional.empty();
            // pitch:上负下正(原版口径);折半防幅度过冲,再乘配置系数
            float pitch = (float) Math.toRadians(player.getPitch() * 0.5f) * k;
            boolean rightHanded = player.getMainArm() == net.minecraft.util.Arm.RIGHT;
            String mainArm = rightHanded ? "rightArm" : "leftArm";
            String offArm  = rightHanded ? "leftArm" : "rightArm";
            float rx;
            if (partName.equals(mainArm))      rx = pitch;          // 持械臂:满量跟随
            else if (partName.equals(offArm))  rx = pitch * 0.4f;   // 副手:四成,不僵
            else if (partName.equals("torso") || partName.equals("body")) rx = -pitch * 0.3f; // 躯干反向:抬头挺身
            else return Optional.empty();                            // 头/腿/其余:不动(原版 head 本就跟视线)
            return Optional.of(new AdjustmentModifier.PartModifier(
                    new Vec3f(rx, 0f, 0f), Vec3f.ZERO));
        });
    }

    /**
     * m260 站姿状态机(每客户端 tick,只管本地玩家;Epic Fight 感的核心=拿武器有架势):
     *  格挡姿态(按住右键+持可格挡武器,法杖除外)> 战斗站姿(手持可出刀光武器)> 无。
     *  状态切换用 4t 快融;循环动画只动 臂/躯干/头,腿不碰=行走跑步照常。
     */
    public static void tickStance(net.minecraft.client.MinecraftClient mc) {
        YongyeConfig cfg = YongyeConfig.get();
        var p = mc.player;
        int want = 0;
        // m396:第一人称一律无架势——playerAnimator 循环姿态会泄漏进第一人称视角,把武器
        // 横持在脸前(作者截图:巨阙 35 单位长模型=「整屏大刀」;龙魂/混沌刃染色紫/红对上两图)。
        // 架势本是给第三人称看的 Epic Fight 观感,自己第一人称吃不到收益只吃遮挡;
        // 门放在 want 计算前=切 F5 即 4t 融入恢复、切回第一人称即 4t 融出,原开关语义不变。
        boolean firstPerson = mc.options.getPerspective().isFirstPerson();
        if (!firstPerson && p != null && mc.currentScreen == null && SlashFxManager.weaponEligible(p)) {
            boolean staff = p.getMainHandStack().getItem() instanceof com.yongye.item.ClassWeaponItem cwi
                    && cwi.playerClass == com.yongye.item.PlayerClass.WARLOCK;
            if (cfg.slashFxGuardPose && cfg.enableWeaponGuard && !staff && mc.options.useKey.isPressed()) want = 2;
            else if (cfg.slashFxBattleStance && !p.isUsingItem()) want = 1;
        }
        if (want == stanceState) return;
        stanceState = want;
        if (p == null) return;
        var data = PlayerAnimationAccess.getPlayerAssociatedData(p).get(STANCE_ID);
        if (!(data instanceof ModifierLayer)) return;
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) data;
        String name = want == 2 ? "yongye_guard_pose" : want == 1 ? "yongye_battle_idle" : null;
        var playable = name == null ? null : PlayerAnimationRegistry.getAnimation(Identifier.of(Yongye.MOD_ID, name));
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(4, Ease.INOUTSINE),
                playable == null ? null : playable.playAnimation(), true);
    }

    /**
     * 为该玩家播放第 variant 式真动作。
     * @return true=已播放(程序化姿态应让位);false=开关关/动画缺失/层未挂(退回程序化姿态,不算错误)。
     */
    public static boolean playFor(AbstractClientPlayerEntity player, int variant) {
        if (!YongyeConfig.get().slashFxAnimLib) return false;
        // m396:第一人称不播真动作(骨骼动画会带动第一人称手臂/武器满屏挥)——返回 false
        // 走程序化姿态,程序化只改第三人称模型角度,第一人称保持原版挥手,观感干净。
        if (net.minecraft.client.MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) return false;
        if (variant < 0 || variant >= VARIANT_ANIM.length) return false;
        var playable = PlayerAnimationRegistry.getAnimation(Identifier.of(Yongye.MOD_ID, VARIANT_ANIM[variant]));
        if (playable == null) return false;                       // JSON 没装上/名字对不上,退回程序化姿态
        var data = PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER_ID);
        if (!(data instanceof ModifierLayer)) return false;
        @SuppressWarnings("unchecked")
        ModifierLayer<IAnimation> layer = (ModifierLayer<IAnimation>) data;
        // 2t 快融入:连击中途换式不生硬;fadeFromNothing=true 让第一刀也有融入
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(2, Ease.INOUTSINE),
                playable.playAnimation(), true);
        return true;
    }
}
