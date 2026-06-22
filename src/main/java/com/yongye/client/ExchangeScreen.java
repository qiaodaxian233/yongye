package com.yongye.client;

import com.yongye.network.ExchangePayload;
import com.yongye.registry.ModItems;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 材料兑换界面:10 碎片→1 结晶,10 结晶→1 核心,10 核心→1 血核。
 * 纯客户端 Screen——按钮只发 C2S {@link ExchangePayload},真正扣料/给料在服务端
 * {@code MaterialExchange} 完成;界面实时读取本地背包显示各材料数量,兑换后随背包同步刷新。
 */
@Environment(EnvType.CLIENT)
public class ExchangeScreen extends Screen {

    private static final int RATIO = 10;
    private final Screen parent;

    public ExchangeScreen(Screen parent) {
        super(Text.literal("材料兑换"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int y0 = this.height / 2 - 46;
        addRowButtons(cx, y0, 0);
        addRowButtons(cx, y0 + 36, 1);
        addRowButtons(cx, y0 + 72, 2);
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(cx - 50, y0 + 116, 100, 20).build());
    }

    private void addRowButtons(int cx, int y, int tier) {
        addDrawableChild(ButtonWidget.builder(Text.literal("兑换 " + RATIO + "→1"),
                        b -> ClientPlayNetworking.send(new ExchangePayload(tier, false)))
                .dimensions(cx + 28, y, 92, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("全部兑换"),
                        b -> ClientPlayNetworking.send(new ExchangePayload(tier, true)))
                .dimensions(cx + 124, y, 72, 20).build());
    }

    private int count(Item item) {
        if (this.client == null || this.client.player == null) return 0;
        PlayerInventory inv = this.client.player.getInventory();
        int c = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.getItem() == item) c += s.getCount();
        }
        return c;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int y0 = this.height / 2 - 46;

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 材料兑换(" + RATIO + " 换 1 · 等值)◆").formatted(Formatting.GOLD),
                cx, y0 - 26, 0xFFFFD700);

        drawRowLabel(ctx, cx, y0, ModItems.LIFE_SHARD, "生命碎片", "生命结晶");
        drawRowLabel(ctx, cx, y0 + 36, ModItems.LIFE_CRYSTAL, "生命结晶", "生命核心");
        drawRowLabel(ctx, cx, y0 + 72, ModItems.LIFE_CORE, "生命核心", "灾变血核");
    }

    private void drawRowLabel(DrawContext ctx, int cx, int y, Item fromItem, String from, String to) {
        ctx.drawTextWithShadow(this.textRenderer,
                Text.literal(from + " ×" + count(fromItem) + "  →  " + to).formatted(Formatting.AQUA),
                cx - 174, y + 6, 0xFF55FFFF);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
