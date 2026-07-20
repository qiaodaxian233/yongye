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
 * 开局选职界面(m205 海报翻页版;m207 重排:海报正中、页签贴左、确认钮挂页签下)。
 * 实机反馈 m205 版「海报偏右 + 页签孤在最左 + 确认钮压快捷栏」,重排为一个居中的整体:
 * 海报屏幕正中等比铺满高度,6 个职业页签紧贴海报左侧,确认按钮在页签列正下方(远离底部 HUD)。
 * 强制选择(屏蔽 ESC);贴图 768×1024,drawTexture 9 参签名照 AccessoryScreen。
 */
public class ClassSelectScreen extends Screen {

    /** 海报贴图像素(装入时统一 768×1024,3:4)。 */
    private static final int TW = 768, TH = 1024;

    private final PlayerClass[] classes = PlayerClass.values();
    private final YongyeButton[] tabs = new YongyeButton[PlayerClass.values().length];
    private int sel = 0;

    // init() 里算好的布局(海报位置尺寸 / 页签列 X / 确认钮下方注释行 Y)
    private int drawW, drawH, px, py, tabX, noteY;

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
        // 海报:上下各留 10、3:4 等比、屏幕正中;超窄窗口按宽度回推
        drawH = this.height - 20;
        drawW = drawH * 3 / 4;
        int maxW = this.width - 96 - 16;   // 给页签列(64 宽 + 12 间距)和边距留位
        if (drawW > maxW) {
            drawW = Math.max(60, maxW);
            drawH = drawW * 4 / 3;
        }
        px = this.width / 2 - drawW / 2;
        py = (this.height - drawH) / 2;
        tabX = Math.max(6, px - 76);       // 页签紧贴海报左侧

        int th = 20, gap = 6;
        int blockH = classes.length * (th + gap) - gap + 14 + th;  // 页签块 + 间距 + 确认钮
        int ty = this.height / 2 - blockH / 2;
        for (int i = 0; i < classes.length; i++) {
            final int idx = i;
            tabs[i] = new YongyeButton(tabX, ty + i * (th + gap), 64, th,
                    Text.literal(classes[i].cn), b -> this.sel = idx);
            addDrawableChild(tabs[i]);
        }
        // 确认钮:页签列正下方(与页签列同轴,远离底部快捷栏)
        int cy = ty + classes.length * (th + gap) - gap + 14;
        noteY = cy + th + 4;
        addDrawableChild(new YongyeButton(tabX - 8, cy, 80, th,
                Text.literal("✔ 选定职业"), b -> {
            ClientPlayNetworking.send(new ChooseClassPayload(classes[sel].id));
            MinecraftClient.getInstance().setScreen(null);
        }));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        // 再压一层夜色,衬海报
        ctx.fill(0, 0, this.width, this.height, 0x66050710);

        // 海报(屏幕正中)
        float s = drawW / (float) TW;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(px, py, 0);
        ctx.getMatrices().scale(s, s, 1.0f);
        ctx.drawTexture(posterOf(classes[sel]), 0, 0, 0, 0, TW, TH, TW, TH);
        ctx.getMatrices().pop();

        // 当前页签指示(金色小箭头)+ 确认钮下方提示
        YongyeButton cur = tabs[sel];
        if (cur != null) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("▶"),
                    cur.getX() - 9, cur.getY() + 6, 0xFFFFD700);
        }
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("（不可更改）"), tabX + 32, noteY, 0xFF8A93A3);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
