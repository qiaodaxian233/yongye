package com.yongye.client;

import com.yongye.network.WardApplyPayload;
import com.yongye.registry.ModComponents;
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
 * 守护界面:右键守护书打开。自动扫描背包(含护甲/副手),列出所有「可守护装备」
 * (武器/盔甲/盾牌,原版与永夜物品皆可),点击某件 → 发 C2S {@link WardApplyPayload};
 * 真正的校验/施加/扣书在服务端 {@code WardBookItem.applyWard} 完成。
 * 界面每帧实时读本地背包,故附成功(背包同步回来)后该件立即变「✔ 已守护」且不可再点。
 */
@Environment(EnvType.CLIENT)
public class WardScreen extends Screen {

    private static final int ROW_H = 22;       // 每行高度
    private static final int ROWS_PER_PAGE = 9; // 每页行数
    private static final int LIST_W = 220;      // 列表宽度

    private int page = 0;
    private ButtonWidget prevBtn, nextBtn;

    public WardScreen() {
        super(Text.literal("守护 · 选择要保护的装备"));
    }

    /** 背包里一项可守护装备:槽位 + 当前 stack(用于显示,服务端会按槽位重新取权威值)。 */
    private record Entry(int slot, ItemStack stack) {}

    /** 实时扫描本地背包,收集所有可守护装备(按槽位顺序)。 */
    private List<Entry> collect() {
        List<Entry> out = new ArrayList<>();
        if (this.client == null || this.client.player == null) return out;
        PlayerInventory inv = this.client.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && EquipmentEnhancer.isWardable(s)) out.add(new Entry(i, s));
        }
        return out;
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
        int maxPage = Math.max(0, (all.size() - 1) / ROWS_PER_PAGE);
        if (page > maxPage) page = maxPage;
        prevBtn.active = page > 0;
        nextBtn.active = page < maxPage;

        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        int x = listX();
        int y0 = listY();

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 守护:点击装备即附「无法被夺取」(消耗 1 本守护书)◆").formatted(Formatting.GOLD),
                cx, y0 - 24, 0xFFFFD700);

        if (all.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("背包里没有可守护的装备(武器/护甲/盾牌)").formatted(Formatting.GRAY),
                    cx, y0 + 20, 0xFFAAAAAA);
            return;
        }

        int start = page * ROWS_PER_PAGE;
        int end = Math.min(start + ROWS_PER_PAGE, all.size());
        for (int idx = start; idx < end; idx++) {
            Entry e = all.get(idx);
            int rowY = y0 + (idx - start) * ROW_H;
            boolean warded = e.stack().getOrDefault(ModComponents.DISARM_PROOF, false);
            boolean hovered = !warded && mouseX >= x && mouseX <= x + LIST_W && mouseY >= rowY && mouseY <= rowY + ROW_H;
            if (hovered) ctx.fill(x, rowY, x + LIST_W, rowY + ROW_H, 0x40FFFFFF); // 悬停高亮
            // 图标
            ctx.drawItem(e.stack(), x + 2, rowY + 2);
            // 名字 + 状态
            Text name = e.stack().getName();
            if (warded) {
                ctx.drawTextWithShadow(this.textRenderer,
                        Text.literal(name.getString()).formatted(Formatting.DARK_GRAY), x + 24, rowY + 6, 0xFF888888);
                ctx.drawTextWithShadow(this.textRenderer,
                        Text.literal("✔ 已守护").formatted(Formatting.GREEN), x + LIST_W - 56, rowY + 6, 0xFF55FF55);
            } else {
                ctx.drawTextWithShadow(this.textRenderer, name, x + 24, rowY + 6, 0xFFFFFFFF);
            }
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("第 " + (page + 1) + " / " + (maxPage + 1) + " 页 · 共 " + all.size() + " 件").formatted(Formatting.GRAY),
                cx, y0 + ROWS_PER_PAGE * ROW_H - 14, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<Entry> all = collect();
            int x = listX();
            int y0 = listY();
            int start = page * ROWS_PER_PAGE;
            int end = Math.min(start + ROWS_PER_PAGE, all.size());
            for (int idx = start; idx < end; idx++) {
                int rowY = y0 + (idx - start) * ROW_H;
                if (mouseX >= x && mouseX <= x + LIST_W && mouseY >= rowY && mouseY <= rowY + ROW_H) {
                    Entry e = all.get(idx);
                    boolean warded = e.stack().getOrDefault(ModComponents.DISARM_PROOF, false);
                    if (!warded) {
                        ClientPlayNetworking.send(new WardApplyPayload(e.slot())); // 服务端校验+施加+扣书
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false; // 不暂停游戏(与其它自定义界面一致)
    }
}
