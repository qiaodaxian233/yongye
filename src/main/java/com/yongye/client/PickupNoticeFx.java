package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 拾取通知卡(m386,3A 打磨路线图第 15 项):稀有+物品<b>真正进包</b>时,屏幕右侧
 * 滑入一张品质色通知卡(物品图标 + 名字 ×N + 品质左条),2.5s 后淡出。
 * 定级口径复用 {@link LootBeamManager#tierOf}(评审点名:别再写一套)。
 *
 * <p><b>评审五约束的实现口径——背包差分,不挂拾取事件:</b>
 * 每 10 客户端 tick 对背包(主 36 格+副手)里 tier&gt;0 的物品做计数快照,与上一快照比对,
 * 某物品计数<b>增加</b>才发卡。由此天然满足:
 * ①满背包没真进包=计数没变=不提示;②吸到脚边被别的玩家截胡=自己计数没变=不提示;
 * ③10 tick(0.5s)窗口内同物品多次拾取=一次差分合并为 ×N;
 * ④任务奖励/命令发放也会提示——「获得即播报」语义,符合直觉;
 * ⑤世界引用变化(进世界/重登/换维度)快照清空,首轮只记账不发卡,登录不刷屏。
 *
 * <p>队列≤5 张:满了按「品质低者先挤、同品质旧者先挤」淘汰(神器/职业武器金档最后被挤,
 * 评审优先级要求);渲染=右缘滑入 150ms ease-out、驻留、末 300ms 淡出,竖向堆叠;
 * nanoTime 驱动到点必消;enablePickupNotice 与 FxBudget.on() 双门。
 * 扫描成本:每 0.5s 37 个槽位读一遍 tierOf,可忽略。零新 API 面
 * (getInventory/getStack/drawItem/drawText 全在树)。
 */
public final class PickupNoticeFx {
    private PickupNoticeFx() {}

    private static final int SCAN_INTERVAL = 10;    // 客户端 tick;兼作合并窗口(0.5s)
    private static final int MAX_CARDS = 5;
    private static final long SHOW_MS = 2500, SLIDE_MS = 150, FADE_MS = 300;
    private static final int CARD_W = 120, CARD_H = 24, GAP = 4;
    /** 品质左条色(与掉落光柱同表:1 蓝 2 紫 3 金)。 */
    private static final int[] TIER_RGB = {0x3B9CFF, 0xB44CFF, 0xFFC332};

    private static final class Card {
        final ItemStack icon; final String name; final int count; final int tier; final long bornNanos;
        Card(Item item, int count, int tier) {
            this.icon = new ItemStack(item);
            this.name = this.icon.getName().getString();
            this.count = count; this.tier = tier; this.bornNanos = System.nanoTime();
        }
    }

    private static final List<Card> CARDS = new ArrayList<>();
    private static Map<Item, Integer> baseline = null;   // null=未知(首轮只记账)
    private static Object lastWorldRef = null;
    private static int scanCd = 0;

    /** 客户端初始化时挂(YongyeClient 调)。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (mc.world != lastWorldRef) {              // 进世界/重登/换维度:快照清空,首轮不发卡
                lastWorldRef = mc.world;
                baseline = null;
                CARDS.clear();
                return;
            }
            if (--scanCd > 0) return;
            scanCd = SCAN_INTERVAL;
            if (mc.player == null) return;
            if (!YongyeConfig.get().enablePickupNotice || !FxBudget.on()) { baseline = null; return; }

            Map<Item, Integer> now = snapshot(mc.player.getInventory());
            if (baseline != null) {
                for (Map.Entry<Item, Integer> en : now.entrySet()) {
                    int gained = en.getValue() - baseline.getOrDefault(en.getKey(), 0);
                    if (gained > 0) enqueue(new Card(en.getKey(), gained,
                            LootBeamManager.tierOf(new ItemStack(en.getKey()))));
                }
            }
            baseline = now;
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (CARDS.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.options.hudHidden) return;
            long now = System.nanoTime();
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            int y = h / 2 - 48;                          // 右缘中偏上,躲开热栏与看板区

            Iterator<Card> it = CARDS.iterator();
            while (it.hasNext()) {
                Card cd = it.next();
                long age = (now - cd.bornNanos) / 1_000_000L;
                if (age >= SHOW_MS) { it.remove(); continue; }

                float slide = age < SLIDE_MS ? 1f - easeOut(age / (float) SLIDE_MS) : 0f; // 1→0 右缘滑入
                float fade = age > SHOW_MS - FADE_MS ? (SHOW_MS - age) / (float) FADE_MS : 1f;
                int a = Math.max(8, (int) (255 * fade));
                int x = w - CARD_W - 6 + (int) (slide * (CARD_W + 8));

                int rgb = TIER_RGB[Math.max(0, Math.min(2, cd.tier - 1))];
                // 卡底(暗玻璃)+ 品质左条 + 顶高光(m142 HUD 手法)
                ctx.fill(x, y, x + CARD_W, y + CARD_H, (Math.min(a, 205) << 24) | 0x10161F);
                ctx.fill(x, y, x + 3, y + CARD_H, (a << 24) | rgb);
                ctx.fill(x + 3, y + 1, x + CARD_W - 1, y + 2, (Math.min(a, 60) << 24) | 0xBFE6FF);
                // 图标 + 名字 ×N
                ctx.drawItem(cd.icon, x + 6, y + 4);
                String label = cd.count > 1 ? cd.name + " ×" + cd.count : cd.name;
                String shown = mc.textRenderer.trimToWidth(label, CARD_W - 32); // yarn 已核 method_27523
                ctx.drawTextWithShadow(mc.textRenderer, Text.literal(shown),
                        x + 26, y + (CARD_H - 8) / 2, (a << 24) | rgb);
                y += CARD_H + GAP;
            }
        });
    }

    /** 背包快照:主 36 格 + 副手,只统计 tier>0 的稀有+物品。 */
    private static Map<Item, Integer> snapshot(PlayerInventory inv) {
        Map<Item, Integer> m = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            if (LootBeamManager.tierOf(st) <= 0) continue;
            m.merge(st.getItem(), st.getCount(), Integer::sum);
        }
        return m;
    }

    /** 入队:满 5 张按「品质低者先挤、同品质旧者先挤」淘汰(高品质优先保留,评审口径)。 */
    private static void enqueue(Card card) {
        while (CARDS.size() >= MAX_CARDS) {
            int worst = 0;
            for (int i = 1; i < CARDS.size(); i++) {
                Card a = CARDS.get(i), b = CARDS.get(worst);
                if (a.tier < b.tier || (a.tier == b.tier && a.bornNanos < b.bornNanos)) worst = i;
            }
            if (CARDS.get(worst).tier > card.tier) return;   // 队里全比新卡高级:新卡直接不进
            CARDS.remove(worst);
        }
        CARDS.add(card);
    }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }
}
