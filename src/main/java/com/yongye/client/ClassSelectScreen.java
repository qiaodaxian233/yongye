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
 * 开局选职界面(m215 横版全屏:作者换新一批 16:9 海报,「上次的太传奇了」)。
 * 布局:海报 cover 全屏铺满(等比放大到盖住整屏,超出部分居中裁掉,不留黑边;
 * 海报文字区都在左侧,底部裁一点无伤大雅);底部一条暗色横带压两行——
 * 上行 6 个职业页签(当前页签下方金色底条指示),下行确认按钮。
 * 强制选择(屏蔽 ESC);贴图 1280×720,drawTexture 9 参签名照 AccessoryScreen。
 */
public class ClassSelectScreen extends Screen {

    /** 海报贴图像素(m215 起横版,统一 1280×720,16:9)。 */
    private static final int TW = 1280, TH = 720;

    private final PlayerClass[] classes = PlayerClass.values();
    private final YongyeButton[] tabs = new YongyeButton[PlayerClass.values().length];
    private int sel = 0;

    // init() 里算好的布局(底部页签行/确认行 Y)
    private int tabY, confirmY;

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
        int th = 20, gap = 6;
        // 页签宽:默认 64,窄窗口(GUI 缩放 4 等)按可用宽度回缩,最小 40
        int tw = 64;
        if (classes.length * (tw + gap) - gap > this.width - 8) {
            tw = Math.max(40, (this.width - 8 - (classes.length - 1) * gap) / classes.length);
        }
        int totalW = classes.length * (tw + gap) - gap;
        int x0 = this.width / 2 - totalW / 2;
        confirmY = this.height - 26;
        tabY = confirmY - th - 6;
        for (int i = 0; i < classes.length; i++) {
            final int idx = i;
            tabs[i] = new YongyeButton(x0 + i * (tw + gap), tabY, tw, th,
                    Text.literal(classes[i].cn), b -> this.sel = idx);
            addDrawableChild(tabs[i]);
        }
        // 确认钮:页签行正下方,文案自带「不可更改」提示
        addDrawableChild(new YongyeButton(this.width / 2 - 70, confirmY, 140, th,
                Text.literal("✔ 选定职业(不可更改)"), b -> {
            ClientPlayNetworking.send(new ChooseClassPayload(classes[sel].id));
            MinecraftClient.getInstance().setScreen(null);
        }));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);

        // 海报全屏 cover:等比放大到盖满屏幕,超出部分居中裁掉(不留黑边、不拉伸变形)
        float s = Math.max(this.width / (float) TW, this.height / (float) TH);
        int dw = Math.round(TW * s), dh = Math.round(TH * s);
        int px = (this.width - dw) / 2, py = (this.height - dh) / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(px, py, 0);
        ctx.getMatrices().scale(s, s, 1.0f);
        ctx.drawTexture(posterOf(classes[sel]), 0, 0, 0, 0, TW, TH, TW, TH);
        ctx.getMatrices().pop();

        // 底部暗色横带衬托按钮(海报信息栏在左上,不会被压)
        ctx.fill(0, tabY - 8, this.width, this.height, 0x99050710);

        // 当前页签指示:按钮正下方一条金色底条
        YongyeButton cur = tabs[sel];
        if (cur != null) {
            ctx.fill(cur.getX(), cur.getY() + 20, cur.getX() + cur.getWidth(), cur.getY() + 22, 0xFFFFD700);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
