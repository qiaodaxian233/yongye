package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModComponents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
 * <p><b>m389 评审修补——快照键从裸 Item 升级为「物品+组件指纹」:</b>
 * 旧版用 Map&lt;Item,Integer&gt; 且卡片 new ItemStack(item) 重建,会把自定义名/强化/品质组件洗掉
 * (两把同基础不同强化的武器被合并、卡片显示默认名、tierOf 按裸栈定级失真)。现改为:
 * 键=物品 id+显示名+Rarity+强化等级(影响显示/定级的组件;<b>刻意不含耐久 DAMAGE</b>,
 * 否则用工具掉耐久=换指纹会被误报成拾取);快照同时保存该指纹的代表性 {@code ItemStack}
 * 样本(copyWithCount(1),yarn 已核 method_46651),卡片图标/名字/定级全取自真实组件。
 * 合并只发生在「物品与相关组件相同」的栈之间。另设<b>每物品总量闸</b>:强化/砧上改名等
 * 纯组件变化(旧指纹-1 新指纹+1,总量不变)不发卡,只有该物品总数真涨才播报。
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
        Card(ItemStack source, int count, int tier) {
            this.icon = source.copyWithCount(1);           // m389:保留真实组件(自定义名/强化/品质)
            this.name = source.getName().getString();
            this.count = count; this.tier = tier; this.bornNanos = System.nanoTime();
        }
    }

    /** m389:同指纹物品的快照条目——计数 + 代表性样本栈(发卡直接用真实组件)。 */
    private static final class Snap {
        final ItemStack sample; int count;
        Snap(ItemStack sample) { this.sample = sample; }
    }

    private static final List<Card> CARDS = new ArrayList<>();
    private static Map<String, Snap> baseline = null;    // null=未知(首轮只记账);键=指纹 keyOf
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

            Map<String, Snap> now = snapshot(mc.player.getInventory());
            if (baseline != null) {
                // 每物品总量闸:强化/改名等纯组件变化(旧指纹-1 新指纹+1)总量不变=不发卡
                Map<Item, Integer> itemGain = new HashMap<>();
                for (Snap s : now.values()) itemGain.merge(s.sample.getItem(), s.count, Integer::sum);
                for (Snap s : baseline.values()) itemGain.merge(s.sample.getItem(), -s.count, Integer::sum);

                for (Map.Entry<String, Snap> en : now.entrySet()) {
                    Snap s = en.getValue();
                    Snap b = baseline.get(en.getKey());
                    int gained = s.count - (b == null ? 0 : b.count);
                    if (gained <= 0) continue;
                    int allow = itemGain.getOrDefault(s.sample.getItem(), 0);
                    if (allow <= 0) continue;                        // 总量没涨=组件变换,不是新获得
                    int shown = Math.min(gained, allow);
                    itemGain.put(s.sample.getItem(), allow - shown); // 同物品多指纹间不重复计入
                    enqueue(new Card(s.sample, shown, LootBeamManager.tierOf(s.sample)));
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

    /** 背包快照:主 36 格 + 副手,只统计 tier>0 的稀有+物品;按组件指纹聚合并保存样本栈(m389)。 */
    private static Map<String, Snap> snapshot(PlayerInventory inv) {
        Map<String, Snap> m = new HashMap<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack st = inv.getStack(i);
            if (st.isEmpty()) continue;
            if (LootBeamManager.tierOf(st) <= 0) continue;
            String key = keyOf(st);
            Snap s = m.get(key);
            if (s == null) m.put(key, s = new Snap(st.copyWithCount(1)));
            s.count += st.getCount();
        }
        return m;
    }

    /** m389 组件指纹:物品 id + 显示名 + 稀有度 + 强化等级——覆盖影响「显示与定级」的组件;
     *  刻意不含耐久(DAMAGE):计入的话用工具掉耐久=换指纹,会把耐久变化误报成拾取。 */
    private static String keyOf(ItemStack st) {
        return Registries.ITEM.getId(st.getItem()) + "|" + st.getName().getString()
                + "|" + st.getRarity().name() + "|" + st.getOrDefault(ModComponents.ENHANCE_LEVEL, 0);
    }

    /** 入队:满 5 张按「品质低者先挤、同品质旧者先挤」淘汰(高品质优先保留,评审口径)。 */
    private static void enqueue(Card card) {
        while (CARDS.size() >= MAX_CARDS) {
            int worst = 0;
            for (int i = 1; i < CARDS.size(); i++) {
                Card a = CARDS.get(i), b = CARDS.get(worst);
                if (a.tier < b.tier || (a.tier == b.tier && a.bornNanos < b.bornNanos)) worst = i;
            }
            if (CARDS.get(worst).tier > card.tier) { FxStats.dropped(FxStats.CARD); return; }   // 队里全比新卡高级:新卡直接不进
            CARDS.remove(worst);
            FxStats.dropped(FxStats.CARD);
        }
        CARDS.add(card);
        FxStats.used(FxStats.CARD);
    }

    /** m411 调试面板探针:当前在队卡片数。 */
    static int liveCount() { return CARDS.size(); }

    private static float easeOut(float t) { return 1f - (1f - t) * (1f - t); }
}
