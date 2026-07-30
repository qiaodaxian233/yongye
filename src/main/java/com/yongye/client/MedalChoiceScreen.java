package com.yongye.client;

import com.yongye.network.ChooseMedalPayload;
import com.yongye.system.HuntMedalHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
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
    private static final int CW = 112, CH = 140, GAP = 16;

    /** 卡面图标,与 HuntMedalHandler.IDS 同序:猛攻/体魄/迅捷/坚壁/疾手/不屈(m369 卡面美化)。 */
    private static final ItemStack[] ICONS = {
            new ItemStack(Items.NETHERITE_SWORD), new ItemStack(Items.GOLDEN_APPLE),
            new ItemStack(Items.FEATHER), new ItemStack(Items.IRON_CHESTPLATE),
            new ItemStack(Items.CLOCK), new ItemStack(Items.ANVIL)
    };

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
        // 标题横幅:暗色渐变底 + 两侧金色饰线(m369)
        ctx.fillGradient(cx - 130, top - 54, cx + 130, top - 20, 0x90101826, 0x00101826);
        ctx.fill(cx - 124, top - 35, cx - 74, top - 34, 0x60FFD700);
        ctx.fill(cx + 74, top - 35, cx + 124, top - 34, 0x60FFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u2694 \u730e\u6740\u91cc\u7a0b\u7891 \u00b7 \u4e09\u9009\u4e00 \u2694").formatted(Formatting.GOLD),
                cx, top - 46, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u51fb\u6740\u5956\u52b1\uff1a\u70b9\u9009\u4e00\u679a\u52cb\u7ae0\uff0c\u6c38\u4e45\u751f\u6548")
                        .formatted(Formatting.GRAY),
                cx, top - 30, 0xFFAAAAAA);
        for (int i = 0; i < cards.length; i++) {
            drawCard(ctx, i, cardX(i), top, mouseX, mouseY);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext ctx, int i, int x, int y, int mouseX, int mouseY) {
        int idx = HuntMedalHandler.indexOf(cards[i][0]);
        int color = HuntMedalHandler.COLORS[idx];
        boolean hover = hit(mouseX, mouseY, x, y);
        int yy = hover ? y - 3 : y;                       // 悬停整卡上浮 3px
        int ccx = x + CW / 2;
        // 卡底 + 勋章色顶部色条 + 由上而下的勋章色渐变罩(悬停加浓)
        ctx.fill(x, yy, x + CW, yy + CH, 0xF00E1622);
        ctx.fill(x, yy, x + CW, yy + 3, color);
        ctx.fillGradient(x, yy + 3, x + CW, yy + CH, tint(color, hover ? 0x55 : 0x2E), 0x00000000);
        // 描边:悬停勋章色亮框,平时暗描边
        int b = hover ? color : 0xFF2E4A66;
        ctx.fill(x - 1, yy - 1, x + CW + 1, yy, b);
        ctx.fill(x - 1, yy + CH, x + CW + 1, yy + CH + 1, b);
        ctx.fill(x - 1, yy, x, yy + CH, b);
        ctx.fill(x + CW, yy, x + CW + 1, yy + CH, b);
        // 图标区:淡色光晕底 + 2 倍物品图标
        ctx.fill(ccx - 20, yy + 10, ccx + 20, yy + 48, tint(color, 0x22));
        ctx.getMatrices().push();
        ctx.getMatrices().translate(ccx - 16, yy + 13, 0);
        ctx.getMatrices().scale(2.0f, 2.0f, 1.0f);
        ctx.drawItem(ICONS[idx], 0, 0);
        ctx.getMatrices().pop();
        int lv = parseInt(cards[i][1]);
        double pct = parseDouble(cards[i][2]);
        // 名字(勋章色)+ 饰线分隔
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(HuntMedalHandler.NAMES[idx]), ccx, yy + 54, color);
        ctx.fill(ccx - 28, yy + 66, ccx + 28, yy + 67, tint(color, 0x80));
        // 层数 Lv.N → N+1(新层数金色)
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Lv." + lv + " ").formatted(Formatting.GRAY)
                        .append(Text.literal("\u2192 ").formatted(Formatting.WHITE))
                        .append(Text.literal("Lv." + (lv + 1)).formatted(Formatting.GOLD)),
                ccx, yy + 73, 0xFFFFFFFF);
        // 属性与数值(总加成 = 每层 × 新层数)
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(HuntMedalHandler.STATS[idx]), ccx, yy + 90, 0xFFB8C4D0);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("+" + HuntMedalHandler.trim(pct) + "% / \u5c42\uff08\u5171 +"
                        + HuntMedalHandler.trim(pct * (lv + 1)) + "%\uff09"), ccx, yy + 103, 0xFF9AA6B2);
        if (hover) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u25b6 \u70b9\u51fb\u9009\u53d6 \u25c0").formatted(Formatting.GOLD),
                    ccx, yy + CH - 16, 0xFFFFD700);
        }
    }

    /** 勋章色 + 指定透明度(卡面渐变/光晕/饰线用)。 */
    private static int tint(int color, int alpha) { return (alpha << 24) | (color & 0xFFFFFF); }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    private static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
