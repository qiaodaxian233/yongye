package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.item.PlayerClass;
import com.yongye.network.ChooseClassPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 开局选职界面(m205:用户提供的 6 张「职业介绍」海报,整图展示 + 翻页选择)。
 * 左侧 6 个职业页签(夜蚀主题按钮)切换海报;海报按 3:4 等比缩放居中铺满可用高度
 * (海报自带职业名/定位/技能列表,界面不再叠字);底部「选定当前职业」确认后提交并关闭。
 * 强制选择(屏蔽 ESC);贴图 768×1024(原图 LANCZOS 降采样),drawTexture 9 参签名照 AccessoryScreen。
 */
public class ClassSelectScreen extends Screen {

    /** 海报贴图像素(装入时统一 768×1024,3:4)。 */
    private static final int TW = 768, TH = 1024;

    private final PlayerClass[] classes = PlayerClass.values();
    private final YongyeButton[] tabs = new YongyeButton[PlayerClass.values().length];
    private int sel = 0;

    public ClassSelectScreen() {
        super(Text.literal("选择本命职业"));
    }

    private static Identifier posterOf(PlayerClass c) {
        return Identifier.of(Yongye.MOD_ID, "textures/gui/class_poster_" + c.id + ".png");
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        // 左侧职业页签
        int th = 20, gap = 6;
        int ty = this.height / 2 - (classes.length * th + (classes.length - 1) * gap) / 2;
        for (int i = 0; i < classes.length; i++) {
            final int idx = i;
            tabs[i] = new YongyeButton(10, ty + i * (th + gap), 64, th,
                    Text.literal(classes[i].cn), b -> this.sel = idx);
            addDrawableChild(tabs[i]);
        }
        // 底部确认(跟海报同轴居中)
        int pcx = (84 + (this.width - 10)) / 2;
        addDrawableChild(new YongyeButton(pcx - 90, this.height - 26, 180, 20,
                Text.literal("✔ 选定当前职业（不可更改）"), b -> {
            ClientPlayNetworking.send(new ChooseClassPayload(classes[sel].id));
            MinecraftClient.getInstance().setScreen(null);
        }));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        // 再压一层夜色,衬海报
        ctx.fill(0, 0, this.width, this.height, 0x66050710);

        // 海报:在页签右侧区域等比(3:4)最大化居中
        int areaL = 84, areaR = this.width - 10;
        int drawH = this.height - 42;              // 顶 8 + 底部确认区 34
        int drawW = drawH * 3 / 4;
        if (drawW > areaR - areaL) {
            drawW = areaR - areaL;
            drawH = drawW * 4 / 3;
        }
        int px = (areaL + areaR) / 2 - drawW / 2;
        int py = 8 + (this.height - 42 - drawH) / 2;
        float s = drawW / (float) TW;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(px, py, 0);
        ctx.getMatrices().scale(s, s, 1.0f);
        ctx.drawTexture(posterOf(classes[sel]), 0, 0, 0, 0, TW, TH, TW, TH);
        ctx.getMatrices().pop();

        // 当前页签指示(金色小箭头)
        YongyeButton cur = tabs[sel];
        if (cur != null) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("▶"),
                    cur.getX() - 9, cur.getY() + 6, 0xFFFFD700);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
