package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 战斗日志简版(m415,路线图第 24 项):最近 承伤来源 / 暴击 / 处决 / 新中的负面状态,
 * 右缘热栏上方一列小字,新条目顶入、8s 淡出,方便调平衡。enableCombatLog 默认关。
 * 三路来源:承伤=CombatLogPayload(服务端限流);输出=DamageNumberPayload 就地取材
 * (**只记暴击/处决**,普通命中不进——DoD 口径,防刷屏);状态=每 tick 自扫新增 HARMFUL
 * 效果(getCategory 口径照 ArtifactManager 在树写法)。行数走 combatLogLines(数值页可拖)。
 */
public final class CombatLogHud {
    private CombatLogHud() {}

    private static final long LIFE_MS = 8000, FADE_MS = 900;

    private record Line(String text, int rgb, long bornMs) {}
    private static final ArrayDeque<Line> LINES = new ArrayDeque<>();
    private static final Set<String> lastEffects = new HashSet<>();

    private static void push(String text, int rgb) {
        LINES.addFirst(new Line(text, rgb, System.nanoTime() / 1_000_000L));
        int cap = Math.max(3, Math.min(12, YongyeConfig.get().combatLogLines));
        while (LINES.size() > cap) LINES.removeLast();
    }

    /** 承伤(收包)。 */
    public static void incoming(String source, float amount) {
        if (!YongyeConfig.get().enableCombatLog) return;
        push("← " + source + " -" + NumFmt.compact(Math.round(Math.max(1, amount))), 0xFF7A6A);
    }

    /** 输出(DamageNumberPayload 就地取材;只收暴击/处决)。 */
    public static void outgoing(int targetId, float amount, int kind) {
        if (!YongyeConfig.get().enableCombatLog || kind < 2) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = "目标";
        if (mc.world != null) {
            var e = mc.world.getEntityById(targetId);
            if (e != null) name = e.getName().getString();
        }
        boolean exec = kind == 3;
        push("→ " + name + " " + NumFmt.compact(Math.round(amount)) + (exec ? " 斩" : " 暴"),
                exec ? 0xD42B3A : 0xFF9040);
    }

    public static void register() {
        // 状态自扫:新出现的 HARMFUL 效果记一条(口径照 ArtifactManager m189)
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.player == null) { lastEffects.clear(); return; }
            if (!YongyeConfig.get().enableCombatLog) { lastEffects.clear(); return; }
            Set<String> now = new HashSet<>();
            for (StatusEffectInstance inst : mc.player.getStatusEffects()) {
                if (inst.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL) continue;
                String key = inst.getEffectType().value().getName().getString(); // 去重键=名称(同链下方展示已在树,不赌新API)
                now.add(key);
                if (!lastEffects.contains(key)) {
                    push("☠ " + inst.getEffectType().value().getName().getString()
                            + (inst.getAmplifier() > 0 ? " " + (inst.getAmplifier() + 1) : "")
                            + " " + Math.max(1, inst.getDuration() / 20) + "s", 0xC08CFF);
                }
            }
            lastEffects.clear();
            lastEffects.addAll(now);
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (LINES.isEmpty()) return;
            YongyeConfig c = YongyeConfig.get();
            if (!c.enableCombatLog) { LINES.clear(); return; }
            MinecraftClient mc = MinecraftClient.getInstance();
            var tr = mc.textRenderer;
            int w = mc.getWindow().getScaledWidth(), h = mc.getWindow().getScaledHeight();
            long now = System.nanoTime() / 1_000_000L;
            int y = h - 96 - FxBudget.safeY();                   // 右缘热栏上方,与技能CD方块(居中)错层(m418 吃安全边距)
            int ceil = h / 2 + 70;                               // m418 天花板:小屏高(GUI缩放大)时不爬进右缘拾取卡区(h/2-48 起往下)
            var it = LINES.iterator();
            while (it.hasNext()) {
                Line l = it.next();
                long age = now - l.bornMs;
                if (age >= LIFE_MS) { it.remove(); continue; }
                if (y < ceil) break;                             // 顶到天花板:老条目本帧不画(仍随寿命自灭)
                int a = age > LIFE_MS - FADE_MS
                        ? Math.max(8, (int) (200 * (LIFE_MS - age) / (double) FADE_MS)) : 200;
                ctx.drawTextWithShadow(tr, Text.literal(l.text),
                        w - 6 - FxBudget.safeX() - tr.getWidth(l.text), y, (a << 24) | l.rgb);
                y -= 10;
            }
        });
    }
}
