package com.yongye.client;

import com.yongye.network.EnhanceSelectPayload;
import com.yongye.system.EquipmentEnhancer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 强化界面(Ward 式):自动扫描背包列出所有「可强化装备」(武器/盔甲/盾/背饰),
 * 显示当前强化等级 + 用背包全部材料可加的级数;点某件 → 发 C2S {@link EnhanceSelectPayload},
 * 服务端用背包里全部强化材料一键强化它并扣料。界面实时读本地背包,强化后立即刷新。
 */
@Environment(EnvType.CLIENT)
public class EnhanceSelectScreen extends Screen {

    private static final int ROW_H = 22;
    private static final int ROWS_PER_PAGE = 9;
    private static final int LIST_W = 240;

    private final Screen parent;
    private int page = 0;
    private ButtonWidget prevBtn, nextBtn;

    public EnhanceSelectScreen(Screen parent) {
        super(Text.literal("强化 · 选择要强化的装备"));
        this.parent = parent;
    }

    private record Entry(int slot, ItemStack stack) {}

    /** 实时扫描本地背包,收集所有可强化装备(按槽位顺序)。 */
    private List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
        if (this.client == null || this.client.player == null) return out;
        PlayerInventory inv = this.client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && EquipmentEnhancer.isEnhanceable(s.getItem())) out.add(new Entry(i, s));
        }
        return out;
    }

    /** 背包里全部强化材料合计可加的级数(客户端预览;服务端施加时按权威配置重算)。 */
    private int availableLevels() {
        if (this.client == null || this.client.player == null) return 0;
        return EquipmentEnhancer.totalMaterialLevels(this.client.player.getInventory());
    }

    private int listX() { return this.width / 2 - LIST_W / 2; }
    private int listY() { return this.height / 2 - (ROWS_PER_PAGE * ROW_H) / 2; }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int bottom = listY() + ROWS_PER_PAGE * ROW_H + 8;
        prevBtn = ButtonWidget.builder(Text.literal("← 上一页"), b -> { if (page > 0) page--; })
                .dimensions(cx - LIST_W / 2, bottom, 70, 20).build();
        nextBtn = ButtonWidget.builder(Text.literal("下一页 →"), b -> { if ((page + 1) * ROWS_PER_PAGE < collect().size()) page++; })
                .dimensions(cx + LIST_W / 2 - 70, bottom, 70, 20).build();
        addDrawableChild(prevBtn);
        addDrawableChild(nextBtn);
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(cx - 40, bottom + 24, 80, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);

        List<Entry> all = collect();
        int avail = availableLevels();
        int maxPage = Math.max(0, (all.size() - 1) / ROWS_PER_PAGE);
        if (page > maxPage) page = maxPage;
        prevBtn.active = page > 0;
        nextBtn.active = page < maxPage;

        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int x = listX();
        int y0 = listY();

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 强化:点击装备 = 用背包全部材料一键强化 ◆").formatted(Formatting.GOLD),
                cx, y0 - 36, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(avail > 0 ? ("当前材料可强化 +" + avail + " 级") : "背包里没有强化材料(碎片/结晶/核心/血核)")
                        .formatted(avail > 0 ? Formatting.AQUA : Formatting.GRAY),
                cx, y0 - 24, avail > 0 ? 0xFF55FFFF : 0xFFAAAAAA);

        if (all.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("背包里没有可强化的装备(武器/护甲/盾/背饰)").formatted(Formatting.GRAY),
                    cx, y0 + 20, 0xFFAAAAAA);
            return;
        }

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, all.size());
        for (int idx = start; idx < end; idx++) {
            Entry e = all.get(idx);
            int rowY = y0 + (idx - start) * ROW_H;
            boolean canEnhance = avail > 0;
            boolean hovered = canEnhance && mouseX >= x && mouseX <= x + LIST_W && mouseY >= rowY && mouseY <= rowY + ROW_H;
            if (hovered) ctx.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0x40FFFFFF);
            ctx.drawItem(e.stack(), x + 2, rowY + 2);
            int lvl = EquipmentEnhancer.getLevel(e.stack());
            ctx.drawTextWithShadow(this.textRenderer, e.stack().getName(), x + 24, rowY + 2, 0xFFFFFFFF);
            String sub = "Lv." + lvl + (canEnhance ? ("  → +" + avail) : "");
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal(sub).formatted(canEnhance ? Formatting.GREEN : Formatting.GRAY),
                    x + 24, rowY + 12, canEnhance ? 0xFF8CFFA8 : 0xFFAAAAAA);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("第 " + (page + 1) + " / " + (maxPage + 1) + " 页 · 共 " + all.size() + " 件").formatted(Formatting.GRAY),
                cx, y0 + ROWS_PER_PAGE * ROW_H - 14, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && availableLevels() > 0) {
            List<Entry> all = collect();
            int x = listX();
            int y0 = listY();
            int start = page * ROWS_PER_PAGE;
            int end = Math.min(start + ROWS_PER_PAGE, all.size());
            for (int idx = start; idx < end; idx++) {
                int rowY = y0 + (idx - start) * ROW_H;
                if (mouseX >= x && mouseX <= x + LIST_W && mouseY >= rowY && mouseY <= rowY + ROW_H) {
                    ClientPlayNetworking.send(new EnhanceSelectPayload(all.get(idx).slot()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
