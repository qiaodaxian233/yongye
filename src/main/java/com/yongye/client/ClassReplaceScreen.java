package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.item.PlayerClass;
import com.yongye.network.ClassReplacePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * 职业替换界面:已满 2 职业时右键新职业书弹出。
 * 顶部文字标明「将学习」的新职业;下方并排两张当前职业卡(本命/第二),点哪张就丢弃哪张并换上新职业。
 * ESC 取消(不替换、不扣书)。卡图沿用 ClassSelectScreen 的 drawTexture 9 参签名(原尺寸 106×132)。
 */
public class ClassReplaceScreen extends Screen {
    // 卡尺寸与 ClassSelectScreen 一致(原尺寸绘制,避免缩放签名风险)
    private static final int CW = 106, CH = 132, GAP = 50;

    private final PlayerClass newClass;
    private final PlayerClass slot0, slot1;

    public ClassReplaceScreen(String newId, String slot0Id, String slot1Id) {
        super(Text.literal("替换职业"));
        this.newClass = PlayerClass.byId(newId);
        this.slot0 = PlayerClass.byId(slot0Id);
        this.slot1 = PlayerClass.byId(slot1Id);
    }

    private int cardTop()  { return this.height / 2 - CH / 2 + 10; }
    private int card0X()   { return this.width / 2 - CW - GAP / 2; }
    private int card1X()   { return this.width / 2 + GAP / 2; }

    private static Identifier cardTex(PlayerClass c) {
        return Identifier.of(Yongye.MOD_ID, "textures/gui/class_card_" + c.id + ".png");
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }   // 允许取消

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && newClass != null) {
            int y = cardTop();
            if (slot0 != null && hit(mouseX, mouseY, card0X(), y)) { confirm(slot0); return true; }
            if (slot1 != null && hit(mouseX, mouseY, card1X(), y)) { confirm(slot1); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean hit(double mx, double my, int x, int y) {
        return mx >= x && mx <= x + CW && my >= y && my <= y + CH;
    }

    private void confirm(PlayerClass discard) {
        ClientPlayNetworking.send(new ClassReplacePayload(discard.id, newClass.id));
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int top = cardTop();
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\u25c6 \u66ff\u6362\u804c\u4e1a \u25c6").formatted(Formatting.GOLD),
                cx, top - 48, 0xFFFFD700);
        if (newClass != null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u5c06\u5b66\u4e60\uff1a\u3010" + newClass.cn + "\u3011\uff0c\u70b9\u51fb\u4e0b\u65b9\u8981\u4e22\u5f03\u7684\u804c\u4e1a")
                            .formatted(Formatting.AQUA),
                    cx, top - 34, 0xFF55FFFF);
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("\uff08ESC \u53d6\u6d88\uff0c\u4e0d\u6263\u4e66\uff09").formatted(Formatting.DARK_GRAY),
                cx, top - 22, 0xFF888888);
        drawCard(ctx, slot0, card0X(), top, mouseX, mouseY, "\u672c\u547d");
        drawCard(ctx, slot1, card1X(), top, mouseX, mouseY, "\u7b2c\u4e8c");
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawCard(DrawContext ctx, PlayerClass c, int x, int y, int mouseX, int mouseY, String slotLabel) {
        if (c == null) return;
        boolean hover = hit(mouseX, mouseY, x, y);
        if (hover) {
            int b = 0xFFFF5555;   // 悬停红框,表示「将丢弃」
            ctx.fill(x - 3, y - 3, x + CW + 3, y, b);
            ctx.fill(x - 3, y + CH, x + CW + 3, y + CH + 3, b);
            ctx.fill(x - 3, y, x, y + CH, b);
            ctx.fill(x + CW, y, x + CW + 3, y + CH, b);
        }
        ctx.drawTexture(cardTex(c), x, y, 0, 0, CW, CH, CW, CH);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(slotLabel + "\uff1a" + c.cn).formatted(Formatting.WHITE),
                x + CW / 2, y + CH + 6, 0xFFFFFFFF);
        if (hover) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("\u70b9\u51fb\u4e22\u5f03").formatted(Formatting.RED),
                    x + CW / 2, y + CH + 18, 0xFFFF5555);
        }
    }
}
