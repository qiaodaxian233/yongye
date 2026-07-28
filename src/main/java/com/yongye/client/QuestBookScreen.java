package com.yongye.client;

import com.yongye.network.ClaimMainQuestPayload;
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
 * m328:任务书界面(FTB Quests 风格内建版)。左侧双列 16 阶段按钮(✔已完成/▶当前/🔒未解锁),
 * 右侧当前选中阶段的目标/进度/奖励详情,底部「领取奖励」(只对当前阶段生效,服务端复核)。
 * 数据流照爆率编辑器口径:打开发 RequestMainQuestPayload,服务端回 MainQuestSyncPayload → onSync 刷新。
 */
@Environment(EnvType.CLIENT)
public class QuestBookScreen extends Screen {

    private static MainQuestSyncPayload DATA;   // 最近一次服务端快照
    private final Screen parent;
    private int selected = 0;

    public QuestBookScreen(Screen parent) {
        super(Text.literal("永夜 · 任务书"));
        this.parent = parent;
        ClientPlayNetworking.send(new RequestMainQuestPayload());
    }

    /** 服务端快照到达:刷新界面(若开着)。 */
    public static void onSync(MainQuestSyncPayload payload) {
        DATA = payload;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof QuestBookScreen s) {
            s.selected = Math.min(payload.stage(), MainQuestLine.STAGES.length - 1);
            s.clearAndInit();
        }
    }

    @Override
    protected void init() {
        int stageCount = MainQuestLine.STAGES.length;
        int cur = DATA == null ? 0 : DATA.stage();
        int colW = 104, rowH = 16, gap = 2;
        int x0 = this.width / 2 - (colW * 2 + gap + 130) / 2;  // 左双列 + 右详情 130 宽,整体居中
        int y0 = 40;
        for (int i = 0; i < stageCount; i++) {
            final int idx = i;
            int col = i / 8, row = i % 8;
            String icon = i < cur ? "✔ " : (i == cur ? "▶ " : "□ ");
            ButtonWidget b = new YongyeButton(x0 + col * (colW + gap), y0 + row * (rowH + gap), colW, rowH,
                    Text.literal(icon + (i + 1) + "." + MainQuestLine.STAGES[i].title()),
                    bt -> { this.selected = idx; this.clearAndInit(); });
            b.active = (i != selected);
            addDrawableChild(b);
        }
        // 领取(当前阶段)+ 刷新 + 关闭
        int by = y0 + 8 * (rowH + gap) + 8;
        addDrawableChild(new YongyeButton(x0, by, colW, 18, Text.literal("领取当前奖励"),
                b -> ClientPlayNetworking.send(new ClaimMainQuestPayload())));
        addDrawableChild(ButtonWidget.builder(Text.literal("刷新"), b -> ClientPlayNetworking.send(new RequestMainQuestPayload()))
                .dimensions(x0 + colW + gap, by, 50, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(x0 + colW + gap + 54, by, 50, 18).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        int cur = DATA == null ? 0 : DATA.stage();
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 永夜主线 · " + Math.min(cur + 1, MainQuestLine.STAGES.length) + "/" + MainQuestLine.STAGES.length + " ◆").formatted(Formatting.GOLD),
                this.width / 2, 14, 0xFFFFD700);

        // 右侧详情
        int colW = 104, gap = 2;
        int x0 = this.width / 2 - (colW * 2 + gap + 130) / 2;
        int dx = x0 + colW * 2 + gap + 10, dy = 42;
        var s = MainQuestLine.STAGES[selected];
        String state = selected < cur ? "已完成" : (selected == cur ? (DATA != null && DATA.complete() ? "已达成·可领取!" : "进行中") : "未解锁");
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("【" + s.title() + "】" + state).formatted(Formatting.AQUA), dx, dy, 0xFF55FFFF);
        dy += 14;
        for (String line : wrap("目标:" + s.goal(), 20)) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.WHITE), dx, dy, 0xFFFFFFFF); dy += 11;
        }
        String prog = progress(selected);
        if (prog != null) { ctx.drawTextWithShadow(this.textRenderer, Text.literal(prog).formatted(Formatting.YELLOW), dx, dy, 0xFFFFFF55); dy += 11; }
        dy += 3;
        for (String line : wrap("奖励:" + s.rewardDesc(), 20)) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(line).formatted(Formatting.GREEN), dx, dy, 0xFF55FF55); dy += 11;
        }
    }

    /** 击杀类阶段的进度显示(索引与 MainQuestLine.STAGES 对应)。 */
    private String progress(int idx) {
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

    /** 简易按字符宽换行(中文场景按字数)。 */
    private static java.util.List<String> wrap(String s, int n) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (int i = 0; i < s.length(); i += n) out.add(s.substring(i, Math.min(s.length(), i + n)));
        return out;
    }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
