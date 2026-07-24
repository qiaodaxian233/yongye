package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * m304 皮肤 BOSS 格挡系统 + BOSS 攻击平衡(作者:「boss 也要有格挡条,就是有皮肤的那些;攻击也要平衡」)。
 *
 * <p><b>格挡</b>:六只皮肤 BOSS(阿努比斯/浴火凤凰/死亡法师/红蜘蛛/自建龙/佩恩)各带一条
 * 格挡值 = 最大生命 × bossGuardFraction(默 20%)。格挡在时,受到的实体伤害打 bossGuardDamageCut
 * 折(默 -50%),格挡值按**原始伤害**消耗;打空 → <b>破防</b> bossGuardBreakTicks(默 10 秒):
 * 期间伤害全额并 ×bossGuardBreakDamageMult(默 1.25),盾裂音效 + 给破防者金字播报;
 * 窗口结束格挡回满,循环。环境伤害(无攻击者)不吃格挡照常结算。
 * 减伤走坦克真减伤 m208 同款「取消 + 守卫重放」,真实伤害也一并按比例被格挡。
 *
 * <p><b>攻击平衡</b>:皮肤 BOSS 对玩家的单击伤害钳到 玩家最大生命 × bossHitCapFraction(默 35%)
 * ——任何阶段至少三刀才可能打死人,不再出现数值跑飞后的一巴掌带走;0=关。
 *
 * <p><b>同步</b>:走血条名 ‖ 通道(m187 先例)追加「‖G当前/上限/破防剩余tick」段,
 * 由各实体 10t 名字刷新处调 {@link #barSuffix};客户端 BossBarStyleMixin 解析并在血条槽正下方
 * 画 3px 青蓝格挡条(破防=红色呼吸闪,和玩家格挡条同一套视觉语言)。零新网络包。
 *
 * <p>已知取舍:重放的伤害会再次经过其它 ALLOW_DAMAGE 观察者(连击等对 BOSS 的命中计两次)——
 * 与坦克真减伤 m208 同款既有行为,保留伤害来源不破坏击杀归属/处决口径。
 */
public final class BossGuardHandler {
    private BossGuardHandler() {}

    private static final class State {
        float cur;
        float max;
        long brokenUntil;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Set<UUID> REAPPLY = new HashSet<>();      // BOSS 减伤重放守卫
    private static final Set<UUID> CAP_REAPPLY = new HashSet<>();  // 玩家侧单击上限重放守卫

    /** 六只皮肤 BOSS(佩恩是带皮肤的 Husk,按 PainBossHandler 记账识别)。 */
    public static boolean isSkinnedBoss(LivingEntity e) {
        return e instanceof com.yongye.entity.AnubisEntity
                || e instanceof com.yongye.entity.FirePhoenixEntity
                || e instanceof com.yongye.entity.DeathMageEntity
                || e instanceof com.yongye.entity.RedSpiderEntity
                || e instanceof com.yongye.entity.ToroEnderDragonEntity
                || PainBossHandler.isPain(e);
    }

    private static State stateOf(LivingEntity boss) {
        return STATES.computeIfAbsent(boss.getUuid(), k -> {
            State st = new State();
            st.max = (float) Math.max(1.0, boss.getMaxHealth() * YongyeConfig.get().bossGuardFraction);
            st.cur = st.max;
            return st;
        });
    }

    /** 血条名后缀「‖G当前/上限/破防剩余tick」;总开关关闭返回空串。顺带在此处懒恢复破防到点的格挡。 */
    public static String barSuffix(LivingEntity boss) {
        if (!YongyeConfig.get().enableBossGuard) return "";
        State st = stateOf(boss);
        long now = boss.getWorld().getTime();
        long remain = st.brokenUntil > 0 ? Math.max(0, st.brokenUntil - now) : 0;
        if (st.brokenUntil > 0 && remain == 0) {
            st.cur = st.max;
            st.brokenUntil = 0;
        }
        return String.format(Locale.ROOT, "\u2016G%.0f/%.0f/%d", st.cur, st.max, remain);
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.getWorld().isClient) return true;
            YongyeConfig c = YongyeConfig.get();

            // ===== ① 攻击平衡:皮肤 BOSS 对玩家单击伤害上限 =====
            if (entity instanceof ServerPlayerEntity victim && c.bossHitCapFraction > 0
                    && source.getAttacker() instanceof LivingEntity atk && isSkinnedBoss(atk)) {
                if (CAP_REAPPLY.contains(victim.getUuid())) return true; // 钳后重放:放行
                float cap = (float) Math.max(1.0, victim.getMaxHealth() * c.bossHitCapFraction);
                if (amount <= cap) return true;
                CAP_REAPPLY.add(victim.getUuid());
                try {
                    victim.damage(source, cap);
                } finally {
                    CAP_REAPPLY.remove(victim.getUuid());
                }
                return false;
            }

            // ===== ② 格挡条:皮肤 BOSS 受击 =====
            if (!c.enableBossGuard || !isSkinnedBoss(entity)) return true;
            if (REAPPLY.contains(entity.getUuid())) return true; // 减伤重放:放行
            if (source.getAttacker() == null) return true;       // 环境伤害不吃格挡
            State st = stateOf(entity);
            long now = entity.getWorld().getTime();
            if (st.brokenUntil > 0) {
                if (now < st.brokenUntil) {
                    double mult = Math.max(1.0, c.bossGuardBreakDamageMult);
                    if (mult <= 1.0001) return true; // 破防期全额,无额外加成时直接放行
                    REAPPLY.add(entity.getUuid());
                    try {
                        entity.damage(source, (float) (amount * mult));
                    } finally {
                        REAPPLY.remove(entity.getUuid());
                    }
                    return false;
                }
                st.cur = st.max; // 窗口结束,格挡回满
                st.brokenUntil = 0;
            }
            // 格挡生效:伤害打折,格挡值按原始伤害消耗
            float reduced = amount * (float) (1.0 - Math.min(0.95, Math.max(0.0, c.bossGuardDamageCut)));
            st.cur -= amount;
            if (st.cur <= 0) {
                st.cur = 0;
                st.brokenUntil = now + Math.max(20, c.bossGuardBreakTicks);
                if (entity.getWorld() instanceof ServerWorld sw) {
                    sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.2f, 0.55f);
                }
                if (source.getAttacker() instanceof ServerPlayerEntity sp) {
                    sp.sendMessage(Text.literal("破防!" + (Math.max(20, c.bossGuardBreakTicks) / 20)
                            + " 秒内伤害全额结算").formatted(Formatting.GOLD), true);
                }
            }
            REAPPLY.add(entity.getUuid());
            try {
                if (reduced > 0.01f) {
                    entity.damage(source, reduced);
                }
            } finally {
                REAPPLY.remove(entity.getUuid());
            }
            return false;
        });

        // 死亡即清状态(卸载残留仅一条小记录,无实体引用,不泄漏)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> STATES.remove(entity.getUuid()));

        Yongye.LOGGER.info("[夜蚀] 皮肤 BOSS 格挡系统 + 攻击平衡已挂载");
    }
}
