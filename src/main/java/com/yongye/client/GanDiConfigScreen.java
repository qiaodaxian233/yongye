package com.yongye.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 干弟小队设置屏(m414,作者点名:皮肤/名字要在 UI 里设,只有召唤师能看见入口)。
 * 五行=五只(岛风/晚安/不爱肝/迷人/芥末),每行两个输入框(名字/正版皮肤ID)+单行应用钮;
 * 底部 全部应用/还原默认/返回背包。落值复用 m413 的 /yongye puppet 命令后端
 * (服务端权威写配置+在场即时改名;皮肤客户端下一帧读新配置经 Mojang 管线自动换),
 * 空框以 "-" 哨兵下发=清槽回默认。输入框写法照爆率编辑器(m301 在树)。
 */
public class GanDiConfigScreen extends Screen {

    private static final String[] DEF = com.yongye.entity.GanDiEntity.VARIANT_NAMES;
    private final Screen parent;
    private final TextFieldWidget[] nameF = new TextFieldWidget[5];
    private final TextFieldWidget[] skinF = new TextFieldWidget[5];

    public GanDiConfigScreen(Screen parent) {
        super(Text.literal("干弟小队"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var c = com.yongye.YongyeConfig.get();
        String[] names = (c.summonGanDiNames == null ? "" : c.summonGanDiNames).split(",", -1);
        String[] skins = (c.summonGanDiSkins == null ? "" : c.summonGanDiSkins).split(",", -1);
        int cx = this.width / 2;
        int top = 58, rowH = 26;
        for (int i = 0; i < 5; i++) {
            int y = top + i * rowH;
            nameF[i] = new TextFieldWidget(this.textRenderer, cx - 118, y, 86, 18, Text.literal("名字" + (i + 1)));
            nameF[i].setMaxLength(16);
            nameF[i].setText(i < names.length ? names[i].trim() : "");
            addDrawableChild(nameF[i]);
            skinF[i] = new TextFieldWidget(this.textRenderer, cx - 26, y, 86, 18, Text.literal("皮肤" + (i + 1)));
            skinF[i].setMaxLength(16);
            skinF[i].setText(i < skins.length ? skins[i].trim() : "");
            addDrawableChild(skinF[i]);
            final int slot = i + 1;
            addDrawableChild(ButtonWidget.builder(Text.literal("应用"), b -> apply(slot))
                    .dimensions(cx + 66, y - 1, 40, 20).build());
        }
        int by = top + 5 * rowH + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("全部应用"), b -> { for (int s = 1; s <= 5; s++) apply(s); })
                .dimensions(cx - 118, by, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("还原默认"), b -> {
            run("yongye puppet reset");
            for (int i = 0; i < 5; i++) { nameF[i].setText(""); skinF[i].setText(""); }
        }).dimensions(cx - 40, by, 70, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回背包"), b -> close())
                .dimensions(cx + 38, by, 70, 20).build());
    }

    /** 单槽落值:空框 → "-" 哨兵(服务端清槽回默认);名字剥逗号防串坏(服务端同口径再兜一层)。 */
    private void apply(int slot) {
        String nm = nameF[slot - 1].getText().trim().replace(",", "");
        String id = skinF[slot - 1].getText().trim().replace(",", "").replace(" ", "");
        run("yongye puppet name " + slot + " " + (nm.isEmpty() ? "-" : nm));
        run("yongye puppet skin " + slot + " " + (id.isEmpty() ? "-" : id));
    }

    private void run(String cmd) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.networkHandler.sendCommand(cmd);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);
        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 干弟小队 · 名字与皮肤 ◆").formatted(Formatting.GOLD), cx, 16, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("左=名字 右=正版皮肤ID(自动拉皮肤,几秒生效);留空=默认").formatted(Formatting.GRAY),
                cx, 32, 0xFFAAAAAA);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("名字 / 皮肤ID").formatted(Formatting.DARK_GRAY), cx - 32, 46, 0xFF888888);
        for (int i = 0; i < 5; i++) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal((i + 1) + "·" + DEF[i]).formatted(Formatting.YELLOW),
                    cx - 176, 63 + i * 26, 0xFFFFFF55);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
