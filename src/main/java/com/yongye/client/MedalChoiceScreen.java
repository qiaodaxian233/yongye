package com.yongye.client;

import com.yongye.network.ChooseMedalPayload;
import com.yongye.system.HuntMedalHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 猎杀里程碑三选一界面(m366):击杀达到里程碑后弹出,三张勋章卡横排,点选一枚永久加成。
 * 数据串由服务端拼好("id:当前层数:每层pct|×3",pct 用服务端配置,客户端纯解析展示)。
 * 屏蔽 ESC(照选职/难度屏先例)——三张全是纯增益没有错项,一次点击就走;
 * 万一意外关屏,服务端 HUNT_PENDING 仍在:看板金字提醒 + 重新登录自动补弹。
 * 卡面元数据(名字/属性/颜色)直接引用 HuntMedalHandler 静态表(客户端引用 system 包纯静态数据,在树先例同 NightfallManager)。
 */
public class MedalChoiceScreen extends Screen {
    private static final int CW = 104, CH = 96, GAP = 16;

    /** cards[i] = {id, 当前层数, 每层pct}(解析失败的项跳过)。 */
    private final String[][] cards;

    public MedalChoiceScreen(String data) {
        super(Text.literal("猎杀勋章"));
        java.util.List<String[]> list = new java.util.ArrayList<>();
        for (String seg : data.split("\\|")) {
            String[] a = seg.split(":");
            if (a.length == 3 && HuntMedalHandler.indexOf(a[0]) >= 0) list.add(a);
        }
        this.cards = list.toArray(new String[0][]);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }   // 强制选择(纯增益无错项;防错过后忘领)

    private int cardTop() { return this.height / 2 - CH / 2 + 6; }

    private int cardX(int i) {
        int total = cards.length * CW + (cards.length - 1) * GAP;
        return this.width / 2 - total / 2 + i * (CW + GAP);
    }

    private boolean hit(double mx, double my, int x, int y) {
        return mx >= x && mx <= x + CW && my >= y && my <= y + CH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int y = cardTop();
            for (int i = 0; i < cards.length; i++) {
                if (hit(mouseX, mouseY, cardX(i), y)) {
                    ClientPlayNetworking.send(new ChooseMedalPayload(cards[i][0]));
                    MinecraftClient.getInstance().setScreen(null);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = cardTop();
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u2694 \u730e\u6740\u91cc\u7a0b\u7891 \u00b7 \u4e09\u9009\u4e00 \u2694").formatted(Formatting.GOLD),
                cx, top - 40, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u51fb\u6740\u5956\u52b1\uff1a\u70b9\u9009\u4e00\u679a\u52cb\u7ae0\uff0c\u6c38\u4e45\u751f\u6548")
                        .formatted(Formatting.GRAY),
                cx, top - 26, 0xFFAAAAAA);
        for (int i = 0; i < cards.length; i++) {
            drawCard(ctx, i, cardX(i), top, mouseX, mouseY);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext ctx, int i, int x, int y, int mouseX, int mouseY) {
        int idx = HuntMedalHandler.indexOf(cards[i][0]);
        int color = HuntMedalHandler.COLORS[idx];
        boolean hover = hit(mouseX, mouseY, x, y);
        // 底 + 描边(悬停用勋章色亮框,平时暗描边)
        ctx.fill(x, y, x + CW, y + CH, 0xE0101826);
        int b = hover ? color : 0xFF2E4A66;
        ctx.fill(x - 1, y - 1, x + CW + 1, y, b);
        ctx.fill(x - 1, y + CH, x + CW + 1, y + CH + 1, b);
        ctx.fill(x - 1, y, x, y + CH, b);
        ctx.fill(x + CW, y, x + CW + 1, y + CH, b);
        if (hover) ctx.fill(x, y, x + CW, y + 2, color);   // 悬停顶亮条
        int lv = parseInt(cards[i][1]);
        double pct = parseDouble(cards[i][2]);
        // 名字(勋章色)
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u25c6 " + HuntMedalHandler.NAMES[idx] + " \u25c6"), x + CW / 2, y + 12, color);
        // 层数 Lv.N → N+1
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Lv." + lv + " \u2192 Lv." + (lv + 1)), x + CW / 2, y + 30, 0xFFFFFFFF);
        // 属性与数值(总加成 = 每层 × 新层数)
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(HuntMedalHandler.STATS[idx]), x + CW / 2, y + 48, 0xFFB8C4D0);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("+" + HuntMedalHandler.trim(pct) + "% / \u5c42\uff08\u5171 +"
                        + HuntMedalHandler.trim(pct * (lv + 1)) + "%\uff09"), x + CW / 2, y + 60, 0xFF9AA6B2);
        if (hover) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u70b9\u51fb\u9009\u53d6").formatted(Formatting.GOLD), x + CW / 2, y + CH - 14, 0xFFFFD700);
        }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
