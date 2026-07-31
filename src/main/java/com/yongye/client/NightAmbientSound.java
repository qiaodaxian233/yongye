package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/**
 * 永夜环境音景(m388,3A 打磨路线图第 18 项,按 m379 评审防疲劳约束):
 * 永夜等级 ≥2 时,每隔一段随机时间在玩家四周<b>远处</b>响一声氛围音
 * (洞穴幽响/低鸣心跳/夜魇远啼/深海低吼四池),越深间隔越短。补 m377 视觉粒子的听觉半边。
 *
 * <p><b>防疲劳约束逐条落地:</b>
 * <ul>
 *   <li>同种音不连播两次:记上一次池号,重抽到同款就顺移一位;</li>
 *   <li>战斗中降概率:检测本地血量下降记 lastCombat,8s 内每次触发点 75% 概率直接吞掉;</li>
 *   <li>重要演出避让:永夜转场(NightfallTransitionFx)/讨伐演出(BossKillFx)进行中跳过本次;</li>
 *   <li>方向距离随机不贴耳:落点=玩家水平 10~18 格随机环带、垂直 ±3,定位播放
 *       (World.playSound 八参签名在树 ChargeSlashHandler 服务端同方法,客户端世界同签名本地播);</li>
 *   <li>音量单独可调:nightAmbientSoundVolume(0~2),0=静音等效关。</li>
 * </ul>
 * 间隔:等级 2~3 = 20~40s;等级 ≥4 = 12~28s(tick 驱动,触发后重掷)。
 * 世界引用变化重置计时;enableNightAmbientSound 与 FxBudget.on() 双门。
 */
public final class NightAmbientSound {
    private NightAmbientSound() {}

    /** 四池:洞穴幽响 / 低鸣心跳(在树) / 夜魇远啼 / 深海低吼。 */
    private static final SoundEvent[] POOL = {
            SoundEvents.AMBIENT_CAVE.value(),
            SoundEvents.ENTITY_WARDEN_HEARTBEAT,
            SoundEvents.ENTITY_PHANTOM_AMBIENT,
            SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT,
    };

    private static final Random RAND = new Random();
    private static Object lastWorldRef = null;
    private static int cooldownTicks = 200;   // 进世界先静 10s
    private static int lastIdx = -1;
    private static float prevHealth = -1;
    private static long lastCombatNanos = 0;

    /** 客户端初始化时挂(YongyeClient 调)。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world != lastWorldRef) {                     // 换世界:重置计时与战斗基线
                lastWorldRef = mc.world;
                cooldownTicks = 200;
                prevHealth = -1;
                lastCombatNanos = 0;                            // m389:上一世界的战斗抑制不带进新维度
                lastIdx = -1;                                   // m389:「同种不连播」参照一并复位
                return;
            }
            if (mc.world == null || mc.player == null) return;

            // 战斗检测:血量下降=挨打了(攻击不算,安全刷怪不吞音景)
            float hp = mc.player.getHealth();
            if (prevHealth >= 0 && hp < prevHealth - 0.01f) lastCombatNanos = System.nanoTime();
            prevHealth = hp;

            if (--cooldownTicks > 0) return;
            int lvl = YongyeClient.nightfallLevel;
            YongyeConfig c = YongyeConfig.get();
            // 触发点:先重掷间隔(无论播不播,防每 tick 重试)
            cooldownTicks = lvl >= 4 ? 240 + RAND.nextInt(320)   // 12~28s
                                     : 400 + RAND.nextInt(400);  // 20~40s
            if (lvl < 2) return;
            if (!c.enableNightAmbientSound || !FxBudget.on()) return;
            float vol = (float) Math.max(0.0, Math.min(2.0, c.nightAmbientSoundVolume));
            if (vol <= 0f) return;
            if (NightfallTransitionFx.isPlaying() || BossKillFx.isShowing()) return;   // 演出避让
            if ((System.nanoTime() - lastCombatNanos) / 1_000_000L < 8000
                    && RAND.nextInt(4) != 0) return;             // 战斗中 75% 吞掉

            // 选池:同种不连播两次
            int idx = RAND.nextInt(POOL.length);
            if (idx == lastIdx) idx = (idx + 1) % POOL.length;
            lastIdx = idx;

            // 远处随机落点:水平 10~18 格环带,垂直 ±3
            double ang = RAND.nextDouble() * Math.PI * 2;
            double dist = 10 + RAND.nextDouble() * 8;
            double x = mc.player.getX() + Math.cos(ang) * dist;
            double z = mc.player.getZ() + Math.sin(ang) * dist;
            double y = mc.player.getY() + RAND.nextDouble() * 6 - 3;
            mc.world.playSound(mc.player, x, y, z, POOL[idx], SoundCategory.AMBIENT,
                    vol, 0.6f + RAND.nextFloat() * 0.3f);
        });
    }
}
