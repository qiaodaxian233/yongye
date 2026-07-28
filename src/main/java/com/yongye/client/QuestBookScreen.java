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

        if (page == 0) {
            int cur = DATA == null ? 0 : DATA.stage();
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
        } else if (page == 1) {
            int cur = DATA == null ? 0 : DATA.trialStage();
            String cls = ClientStats.className;
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
            detail(ctx, x0 + colW * 2 + gap + 10, 46, MainQuestLine.STAGES, selected,
                    DATA == null ? 0 : DATA.stage(), DATA != null && DATA.complete(), mainProgress(selected));
        } else if (page == 1) {
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

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
