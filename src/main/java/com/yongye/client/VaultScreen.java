package com.yongye.client;

import com.yongye.network.RequestVaultPayload;
import com.yongye.network.VaultDepositPayload;
import com.yongye.network.VaultWithdrawPayload;
import com.yongye.system.VaultManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 材料仓库界面(m356):聚合视图——同物品(技能书同级)一行,「名字 ×数量 [取出]」。
 * 数据流照爆率编辑器口径:开屏 Request → 服务端 VaultSyncPayload("键=数量\n") → onSync 刷新;
 * 存/取都发包走服务端权威(VaultManager),本界面纯展示零本地状态。分页每页 9 行。
 */
@Environment(EnvType.CLIENT)
public class VaultScreen extends Screen {

    /** 最近一次同步的条目:[键, 数量字符串](服务端 TreeMap 已排序,同族物品相邻)。 */
    private static List<String[]> ENTRIES = new ArrayList<>();

    private static final int PER_PAGE = 9;
    private final Screen parent;
    private int page = 0;

    public VaultScreen(Screen parent) {
        super(Text.literal("材料仓库"));
        this.parent = parent;
        ClientPlayNetworking.send(new RequestVaultPayload());
    }

    /** 收 VaultSyncPayload:按行解析,若当前正开着本界面则重建(页码钳回有效区)。 */
    public static void onSync(String data) {
        List<String[]> list = new ArrayList<>();
        if (data != null && !data.isEmpty()) {
            for (String line : data.split("\n")) {
                int eq = line.lastIndexOf('=');
                if (eq > 0) list.add(new String[]{line.substring(0, eq), line.substring(eq + 1)});
            }
        }
        ENTRIES = list;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof VaultScreen s) {
            int maxPage = Math.max(0, (ENTRIES.size() - 1) / PER_PAGE);
            if (s.page > maxPage) s.page = maxPage;
            s.clearAndInit();
        }
    }

    @Override
    protected void init() {
        int x0 = this.width / 2 - 150, y0 = 40;
        // 顶排操作
        addDrawableChild(new YongyeButton(x0, y0, 96, 16, Text.literal("存入全部材料"),
                b -> ClientPlayNetworking.send(new VaultDepositPayload())));
        addDrawableChild(new YongyeButton(x0 + 100, y0, 50, 16, Text.literal("刷新"),
                b -> ClientPlayNetworking.send(new RequestVaultPayload())));
        addDrawableChild(new YongyeButton(x0 + 154, y0, 50, 16, Text.literal("关闭"), b -> close()));
        // 行内「取出」钮(标签/图标在 render 画,按钮只占右侧)
        int rowY = y0 + 24;
        int start = page * PER_PAGE;
        for (int i = start; i < Math.min(ENTRIES.size(), start + PER_PAGE); i++) {
            final String key = ENTRIES.get(i)[0];
            addDrawableChild(new YongyeButton(x0 + 246, rowY + (i - start) * 18, 46, 15,
                    Text.literal("取出"), b -> ClientPlayNetworking.send(new VaultWithdrawPayload(key))));
        }
        // 分页
        int maxPage = Math.max(0, (ENTRIES.size() - 1) / PER_PAGE);
        int py = y0 + 24 + PER_PAGE * 18 + 6;
        if (maxPage > 0) {
            ButtonWidget prev = new YongyeButton(x0, py, 30, 15, Text.literal("◀"),
                    b -> { if (page > 0) { TabSwitchFx.trigger(this, -1); page--; clearAndInit(); } }); // m391
            ButtonWidget next = new YongyeButton(x0 + 262, py, 30, 15, Text.literal("▶"),
                    b -> { if (page < maxPage) { TabSwitchFx.trigger(this, 1); page++; clearAndInit(); } }); // m391
            prev.active = page > 0;
            next.active = page < maxPage;
            addDrawableChild(prev);
            addDrawableChild(next);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        int x0 = this.width / 2 - 150, y0 = 40;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 材料仓库 ◆").formatted(Formatting.GOLD), this.width / 2, 14, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("强化材料/强化石/技能书/终焉精华/保护卷 · 无限堆叠 · 死亡不丢").formatted(Formatting.DARK_GRAY),
                this.width / 2, 26, 0xFF888888);

        if (ENTRIES.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("仓库空空如也——点「存入全部材料」把背包里的成长物资收进来").formatted(Formatting.GRAY),
                    this.width / 2, y0 + 60, 0xFFAAAAAA);
            return;
        }
        int rowY = y0 + 24;
        int start = page * PER_PAGE;
        for (int i = start; i < Math.min(ENTRIES.size(), start + PER_PAGE); i++) {
            String[] e = ENTRIES.get(i);
            int y = rowY + (i - start) * 18;
            ctx.fill(x0 - 2, y - 1, x0 + 296, y + 16, (i - start) % 2 == 0 ? 0x300E1A2E : 0x18000000); // 斑马条
            ItemStack icon = VaultManager.stackFor(e[0], 1);
            String name;
            if (icon.isEmpty()) {
                name = e[0] + "(已失效)";
            } else {
                ctx.drawItem(icon, x0, y);
                name = icon.getName().getString();
            }
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(name).formatted(Formatting.WHITE),
                    x0 + 20, y + 4, 0xFFFFFFFF);
            long n;
            try { n = Long.parseLong(e[1]); } catch (NumberFormatException ex) { n = 0; }
            String cnt = "×" + NumFmt.compact(n);
            ctx.drawTextWithShadow(this.textRenderer, Text.literal(cnt).formatted(Formatting.AQUA),
                    x0 + 240 - this.textRenderer.getWidth(cnt), y + 4, 0xFF55FFFF);
        }
        int maxPage = Math.max(0, (ENTRIES.size() - 1) / PER_PAGE);
        if (maxPage > 0) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("第 " + (page + 1) + "/" + (maxPage + 1) + " 页 · 共 " + ENTRIES.size() + " 项")
                            .formatted(Formatting.GRAY),
                    this.width / 2, y0 + 24 + PER_PAGE * 18 + 9, 0xFF9AA6B2);
        }
    }

    @Override public void close() { MinecraftClient.getInstance().setScreen(parent); }
    @Override public boolean shouldPause() { return false; }
}
