package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.util.Ease;
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

    /** 式号(与 SlashFxManager 的 variant 对齐)→ 动画名:0~3 地面连击 / 4 空中回旋 / 5 疾跑突刺 / 6 潜行居合。 */
    private static final String[] VARIANT_ANIM = {
            "yongye_slash_1", "yongye_slash_2", "yongye_slash_3", "yongye_slash_4",
            "yongye_slash_aerial", "yongye_slash_lunge", "yongye_slash_iai"
    };

    /** 每个客户端玩家构造时自动挂一个空动作层;工厂注册的层同时进 playerAssociatedData,playFor 里按键取回。 */
    public static void register() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER_ID, 1000,
                player -> new ModifierLayer<IAnimation>());
        Yongye.LOGGER.info("[夜蚀] 真·拔刀动作库已桥接(player-animator)");
    }

    /**
     * 为该玩家播放第 variant 式真动作。
     * @return true=已播放(程序化姿态应让位);false=开关关/动画缺失/层未挂(退回程序化姿态,不算错误)。
     */
    public static boolean playFor(AbstractClientPlayerEntity player, int variant) {
        if (!YongyeConfig.get().slashFxAnimLib) return false;
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
