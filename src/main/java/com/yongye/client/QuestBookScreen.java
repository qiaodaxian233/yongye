package com.yongye.client;

import com.yongye.network.ClaimMainQuestPayload;
import com.yongye.network.ClaimTrialPayload;
import com.yongye.network.MainQuestSyncPayload;
import com.yongye.network.RequestMainQuestPayload;
import com.yongye.system.MainQuestLine;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * m328/m332/m333:任务书界面,三页签——「主线」(16 阶段,终点讨伐末影龙)/「试炼」(职业专属三关,
 * 标题按本命职业着味)/「图鉴」(总击杀/精英/BOSS/最高强化/技能总级/永夜层/天数/佩恩/龙/永夜+,
 * 主播开播现成炫耀面板)。数据流照爆率编辑器口径:Request → Sync → onSync 刷新。
 */
@Environment(EnvType.CLIENT)
public class QuestBookScreen extends Screen {

    private static MainQuestSyncPayload DATA;
    private final Screen parent;
    private int page = 0;       // 0 主线 / 1 试炼 / 2 图鉴
    private int selected = 0;

    public QuestBookScreen(Screen parent) {
        super(Text.literal("永夜 · 任务书"));
        this.parent = parent;
        ClientPlayNetworking.send(new RequestMainQuestPayload());
    }

    public static void onSync(MainQuestSyncPayload payload) {
        DATA = payload;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof QuestBookScreen s) {
            if (s.page == 0) s.selected = Math.min(payload.stage(), MainQuestLine.STAGES.length - 1);
            s.clearAndInit();
        }
    }

    @Override
    protected void init() {
        int colW = 104, rowH = 16, gap = 2;
        int totalW = colW * 2 + gap + 130;
        int x0 = this.width / 2 - totalW / 2;
        // —— 页签 ——
        String[] tabs = {"主线", "试炼", "图鉴"};
        int tabW = (totalW - 2 * 2) / 3;
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            ButtonWidget tab = ButtonWidget.builder(Text.literal(tabs[i]), b -> {
                this.page = idx;
                this.selected = idx == 1 ? Math.min(DATA == null ? 0 : DATA.trialStage(), MainQuestLine.TRIALS.length - 1)
                        : Math.min(DATA == null ? 0 : DATA.stage(), MainQuestLine.STAGES.length - 1);
                this.clearAndInit();
            }).dimensions(x0 + i * (tabW + 2), 24, tabW, 14).build();
            tab.active = (i != page);
            addDrawableChild(tab);
        }
        int y0 = 44;
        boolean nodeMap = com.yongye.YongyeConfig.get().enableQuestNodeMap;   // m350 节点地图(关=旧双列列表)

        if (page == 0) {
            int cur = DATA == null ? 0 : DATA.stage();
            if (nodeMap) {
                // m350:16 阶段由 render 里的蛇形节点地图绘制+mouseClicked 命中,这里只放操作钮
                int by = y0 + 146;
                addDrawableChild(new YongyeButton(x0, by, colW, 18, Text.literal("领取当前奖励"),
                        b -> ClientPlayNetworking.send(new ClaimMainQuestPayload())));
                bottomBtns(x0 + colW + gap, by);
            } else {
                for (int i = 0; i < MainQuestLine.STAGES.length; i++) {
                    final int idx = i;
                    int col = i / 8, row = i % 8;
                    String icon = i < cur ? "✔ " : (i == cur ? "▶ " : "□ ");
                    ButtonWidget b = new YongyeButton(x0 + col * (colW + gap), y0 + row * (rowH + gap), colW, rowH,
                            Text.literal(icon + (i + 1) + "." + MainQuestLine.STAGES[i].title()),
                            bt -> { this.selected = idx; this.clearAndInit(); });
                    b.active = (i != selected);
                    addDrawableChild(b);
                }
                int by = y0 + 8 * (rowH + gap) + 8;
                addDrawableChild(new YongyeButton(x0, by, colW, 18, Text.literal("领取当前奖励"),
                        b -> ClientPlayNetworking.send(new ClaimMainQuestPayload())));
                bottomBtns(x0 + colW + gap, by);
            }
        } else if (page == 1) {
            int cur = DATA == null ? 0 : DATA.trialStage();
            String cls = ClientStats.className;
            if (nodeMap) {
                // m350:3 关横向节点链由 render 绘制,这里只放操作钮
                int by = y0 + 70;
                addDrawableChild(new YongyeButton(x0, by, colW, 18, Text.literal("领取试炼奖励"),
                        b -> ClientPlayNetworking.send(new ClaimTrialPayload())));
                bottomBtns(x0 + colW + gap, by);
            } else {
                for (int i = 0; i < MainQuestLine.TRIALS.length; i++) {
                    final int idx = i;
                    String icon = i < cur ? "✔ " : (i == cur ? "▶ " : "□ ");
                    ButtonWidget b = new YongyeButton(x0, y0 + i * (rowH + gap) * 2, colW + 40, rowH + 6,
                            Text.literal(icon + "第" + (i + 1) + "关·" + MainQuestLine.trialTitle(cls, i)),
                            bt -> { this.selected = idx; this.clearAndInit(); });
                    b.active = (i != selected);
                    addDrawableChild(b);
                }
                int by = y0 + 3 * (rowH + gap) * 2 + 8;
                addDrawableChild(new YongyeButton(x0, by, colW, 18, Text.literal("领取试炼奖励"),
                        b -> ClientPlayNetworking.send(new ClaimTrialPayload())));
                bottomBtns(x0 + colW + gap, by);
            }
        } else {
            bottomBtns(x0, y0 + 150);
        }
    }

    private void bottomBtns(int x, int y) {
        addDrawableChild(ButtonWidget.builder(Text.literal("刷新"), b -> ClientPlayNetworking.send(new RequestMainQuestPayload()))
                .dimensions(x, y, 50, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(x + 54, y, 50, 18).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        int colW = 104, gap = 2;
        int totalW = colW * 2 + gap + 130;
        int x0 = this.width / 2 - totalW / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 永夜任务书 ◆").formatted(Formatting.GOLD), this.width / 2, 10, 0xFFFFD700);

        if (page == 0) {
            if (com.yongye.YongyeConfig.get().enableQuestNodeMap) {
                drawMainNodeMap(ctx, x0, 44, mouseX, mouseY);   // m350 蛇形节点地图
            }
            detail(ctx, x0 + colW * 2 + gap + 10, 46, MainQuestLine.STAGES, selected,
                    DATA == null ? 0 : DATA.stage(), DATA != null && DATA.complete(), mainProgress(selected));
        } else if (page == 1) {
            if (com.yongye.YongyeConfig.get().enableQuestNodeMap) {
                drawTrialNodeMap(ctx, x0, 44, mouseX, mouseY);  // m350 三关横向节点链
            }
            detail(ctx, x0 + colW + 50, 46, MainQuestLine.TRIALS, selected,
                    DATA == null ? 0 : DATA.trialStage(), DATA != null && DATA.trialComplete(), trialProgress(selected));
        } else {
            int dy = 46;
            for (String line : atlasLines()) {
                ctx.drawTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.WHITE), x0, dy, 0xFFFFFFFF);
                dy += 13;
            }
        }
    }

    private void detail(DrawContext ctx, int dx, int dy, MainQuestLine.Stage[] arr, int sel, int cur, boolean curComplete, String prog) {
        var s = arr[Math.max(0, Math.min(arr.length - 1, sel))];
        String state = sel < cur ? "已完成" : (sel == cur ? (curComplete ? "已达成·可领取!" : "进行中") : "未解锁");
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("【" + s.title() + "】" + state).formatted(Formatting.AQUA), dx, dy, 0xFF55FFFF);
        dy += 14;
        for (String line : wrap("目标:" + s.goal(), 20)) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.WHITE), dx, dy, 0xFFFFFFFF); dy += 11;
        }
        if (prog != null) { ctx.drawTextWithShadow(this.textRenderer, Text.literal(prog).formatted(Formatting.YELLOW), dx, dy, 0xFFFFFF55); dy += 11; }
        dy += 3;
        for (String line : wrap("奖励:" + s.rewardDesc(), 20)) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.GREEN), dx, dy, 0xFF55FF55); dy += 11;
        }
    }

    private String mainProgress(int idx) {
        if (DATA == null) return null;
        return switch (idx) {
            case 3 -> "进度:" + Math.min(DATA.kills(), 20) + "/20";
            case 5 -> "进度:" + Math.min(DATA.eliteKills(), 3) + "/3";
            case 7 -> "进度:" + Math.min(DATA.kills(), 100) + "/100";
            case 9 -> "进度:" + Math.min(DATA.bossKills(), 1) + "/1";
            case 12 -> "进度:" + Math.min(DATA.kills(), 1000) + "/1000";
            case 13 -> "佩恩:" + (DATA.painSlain() ? "已讨伐" : "未讨伐");
            case 15 -> "末影龙:" + (DATA.dragonSlain() ? "已讨伐" : "未讨伐");
            default -> null;
        };
    }

    private String trialProgress(int idx) {
        if (DATA == null) return null;
        return switch (idx) {
            case 0 -> "进度:" + Math.min(DATA.kills(), 300) + "/300";
            case 1 -> "精英 " + Math.min(DATA.eliteKills(), 15) + "/15 · 技能 V" + Math.min(DATA.totalSkill(), 300) + "/300";
            case 2 -> "强化 +" + Math.min(DATA.maxEnhance(), 3000) + "/3000 · BOSS " + Math.min(DATA.bossKills(), 3) + "/3";
            default -> null;
        };
    }

    /** m333 图鉴页:统计一览(主播开播现成炫耀面板)。 */
    private java.util.List<String> atlasLines() {
        java.util.List<String> l = new java.util.ArrayList<>();
        if (DATA == null) { l.add("加载中…点「刷新」"); return l; }
        l.add("—— 讨伐图鉴 ——");
        l.add("总击杀:" + DATA.kills() + "    精英:" + DATA.eliteKills() + "    BOSS:" + DATA.bossKills());
        l.add("佩恩:" + (DATA.painSlain() ? "✔ 已讨伐" : "未讨伐") + "    末影龙:" + (DATA.dragonSlain() ? "✔ 已讨伐" : "未讨伐"));
        l.add("");
        l.add("—— 成长统计 ——");
        l.add("最高强化:+" + DATA.maxEnhance() + "    技能总级:V" + DATA.totalSkill());
        l.add("永夜层数:" + DATA.nightfall() + "    生存天数:" + DATA.day());
        l.add("");
        l.add("—— 征程 ——");
        l.add("主线:" + Math.min(DATA.stage(), MainQuestLine.STAGES.length) + "/" + MainQuestLine.STAGES.length
                + "    试炼:" + Math.min(DATA.trialStage(), MainQuestLine.TRIALS.length) + "/" + MainQuestLine.TRIALS.length);
        l.add("永夜+(二周目):" + (DATA.ngPlus() ? "☽ 已开启" : "未开启(讨伐末影龙解锁)"));
        return l;
    }

    private static java.util.List<String> wrap(String s, int n) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < s.length(); i += n) out.add(s.substring(i, Math.min(s.length(), i + n)));
        return out;
    }

    // ================= m350 节点地图(FTB Quests 观感,clean-room 纯自写:节点链+连线+状态色) =================
    /** 节点边长 / 网格间距(中心距)。 */
    private static final int NODE = 24, PITCH = 34;

    /** 主线蛇形布局:4×4,偶数行左→右、奇数行右→左(路径视觉上连成一条 S 形)。返回节点左上角。 */
    private int[] nodeXY(int i, int x0, int y0) {
        int row = i / 4;
        int col = (row % 2 == 0) ? (i % 4) : (3 - i % 4);
        return new int[]{x0 + col * PITCH, y0 + row * PITCH};
    }

    /** 试炼横向布局:3 节点一排。 */
    private int[] trialNodeXY(int i, int x0, int y0) {
        return new int[]{x0 + i * (PITCH + 10), y0 + 8};
    }

    /** 当前节点金色呼吸边框(System.currentTimeMillis 驱动,纯观感)。 */
    private static int goldPulse() {
        int a = 160 + (int) (95 * Math.abs(Math.sin(System.currentTimeMillis() / 280.0)));
        return (a << 24) | 0xFFC830;
    }

    /** 轴对齐 2px 连线(节点中心间;蛇形网格保证相邻节点必共行或共列)。 */
    private static void link(DrawContext ctx, int cx1, int cy1, int cx2, int cy2, int color) {
        if (cy1 == cy2) ctx.fill(Math.min(cx1, cx2), cy1 - 1, Math.max(cx1, cx2), cy1 + 1, color);
        else ctx.fill(cx1 - 1, Math.min(cy1, cy2), cx1 + 1, Math.max(cy1, cy2), color);
    }

    /** 单个节点:完成=墨绿底绿框✔ / 当前=深蓝底金色呼吸框 / 未解锁=暗紫底灰框灰号;选中另加青色外圈。 */
    private void drawNode(DrawContext ctx, int x, int y, int i, int cur, boolean isSel) {
        boolean done = i < cur, curNode = i == cur;
        int border = done ? 0xFF55C060 : curNode ? goldPulse() : 0xFF3A3A4C;
        int bg = done ? 0xE0182E14 : curNode ? 0xE0102A48 : 0xE0161226;
        ctx.fill(x, y, x + NODE, y + NODE, border);
        ctx.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, bg);
        ctx.fill(x + 1, y + 1, x + NODE - 1, y + 3, 0x30FFFFFF);   // 顶部玻璃高光(照 YongyeButton 手法)
        String glyph = done ? "✔" : String.valueOf(i + 1);
        int tc = done ? 0xFF80FF90 : curNode ? 0xFFFFD700 : 0xFF777788;
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(glyph), x + NODE / 2, y + 8, tc);
        if (isSel) {   // 选中青圈(1px 方环,照 comboRing 画法)
            int c = 0xFF55FFFF;
            ctx.fill(x - 2, y - 2, x + NODE + 2, y - 1, c);
            ctx.fill(x - 2, y + NODE + 1, x + NODE + 2, y + NODE + 2, c);
            ctx.fill(x - 2, y - 1, x - 1, y + NODE + 1, c);
            ctx.fill(x + NODE + 1, y - 1, x + NODE + 2, y + NODE + 1, c);
        }
    }

    /** 悬停浮条:小黑底一行「N.标题 · 状态」。 */
    private void hoverTip(DrawContext ctx, int mouseX, int mouseY, String text) {
        int w = this.textRenderer.getWidth(text);
        int tx = Math.min(mouseX + 8, this.width - w - 8), ty = Math.max(4, mouseY - 12);
        ctx.fill(tx - 3, ty - 2, tx + w + 3, ty + 10, 0xE0000000);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal(text), tx, ty, 0xFFFFE080);
    }

    private static String stateOf(int i, int cur) { return i < cur ? "已完成" : (i == cur ? "进行中" : "未解锁"); }

    /** 主线 16 阶段蛇形节点地图:连线先画(压节点底下),走过的线段金色、未到暗灰;地图下方进度小字。 */
    private void drawMainNodeMap(DrawContext ctx, int x0, int y0, int mouseX, int mouseY) {
        int cur = DATA == null ? 0 : DATA.stage();
        int n = MainQuestLine.STAGES.length;
        for (int i = 0; i < n - 1; i++) {
            int[] a = nodeXY(i, x0, y0), b = nodeXY(i + 1, x0, y0);
            link(ctx, a[0] + NODE / 2, a[1] + NODE / 2, b[0] + NODE / 2, b[1] + NODE / 2,
                    (i + 1) <= cur ? 0xFFCC9933 : 0xFF34344A);
        }
        int hover = -1;
        for (int i = 0; i < n; i++) {
            int[] p = nodeXY(i, x0, y0);
            drawNode(ctx, p[0], p[1], i, cur, i == selected);
            if (mouseX >= p[0] && mouseX < p[0] + NODE && mouseY >= p[1] && mouseY < p[1] + NODE) hover = i;
        }
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("主线进度 " + Math.min(cur, n) + "/" + n + " · 点节点看详情").formatted(Formatting.GRAY),
                x0, y0 + 3 * PITCH + NODE + 6, 0xFF9AA6B2);
        if (hover >= 0) hoverTip(ctx, mouseX, mouseY,
                (hover + 1) + "." + MainQuestLine.STAGES[hover].title() + " · " + stateOf(hover, cur));
    }

    /** 试炼 3 关横向节点链(标题按本命职业着味,悬停显示)。 */
    private void drawTrialNodeMap(DrawContext ctx, int x0, int y0, int mouseX, int mouseY) {
        int cur = DATA == null ? 0 : DATA.trialStage();
        int n = MainQuestLine.TRIALS.length;
        for (int i = 0; i < n - 1; i++) {
            int[] a = trialNodeXY(i, x0, y0), b = trialNodeXY(i + 1, x0, y0);
            link(ctx, a[0] + NODE / 2, a[1] + NODE / 2, b[0] + NODE / 2, b[1] + NODE / 2,
                    (i + 1) <= cur ? 0xFFCC9933 : 0xFF34344A);
        }
        int hover = -1;
        for (int i = 0; i < n; i++) {
            int[] p = trialNodeXY(i, x0, y0);
            drawNode(ctx, p[0], p[1], i, cur, i == selected);
            if (mouseX >= p[0] && mouseX < p[0] + NODE && mouseY >= p[1] && mouseY < p[1] + NODE) hover = i;
        }
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal("试炼进度 " + Math.min(cur, n) + "/" + n).formatted(Formatting.GRAY),
                x0, y0 + 8 + NODE + 8, 0xFF9AA6B2);
        if (hover >= 0) hoverTip(ctx, mouseX, mouseY, "第" + (hover + 1) + "关·"
                + MainQuestLine.trialTitle(ClientStats.className, hover) + " · " + stateOf(hover, cur));
    }

    /** m350:节点命中(仅节点地图模式的主线/试炼页;未命中回落按钮默认处理)。 */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && page <= 1 && com.yongye.YongyeConfig.get().enableQuestNodeMap) {
            int colW = 104, gap = 2;
            int x0 = this.width / 2 - (colW * 2 + gap + 130) / 2;
            int n = page == 0 ? MainQuestLine.STAGES.length : MainQuestLine.TRIALS.length;
            for (int i = 0; i < n; i++) {
                int[] p = page == 0 ? nodeXY(i, x0, 44) : trialNodeXY(i, x0, 44);
                if (mx >= p[0] && mx < p[0] + NODE && my >= p[1] && my < p[1] + NODE) {
                    this.selected = i;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
