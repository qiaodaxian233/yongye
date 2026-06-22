package com.yongye.client;

import com.yongye.network.ConfigValuesPayload;
import com.yongye.network.RequestConfigPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 爆率编辑器:把可调的爆率/经验字段做成**可直接输入的文本框**,改完点「应用并保存」即对每个字段
 * 执行 `config set <key> <值>`(服务端即时生效并写盘到 config/yongye.json)。
 *
 * 取当前值:打开时发 {@link RequestConfigPayload},服务端回 {@link ConfigValuesPayload}(key=value 多行),
 * 由 {@link #onValues(String)} 填进缓存并刷新输入框(预填当前值,方便在现值基础上微调)。
 *
 * 调完用「导出配置」按钮(打印 config/yongye.json 路径),把该文件发给作者即可设成默认配置。
 */
@Environment(EnvType.CLIENT)
public class DropRateConfigScreen extends Screen {

    /** 服务端回传的当前值缓存(key→value,顺序即 EDITABLE_KEYS 顺序)。 */
    private static final Map<String, String> CACHE = new LinkedHashMap<>();

    private final List<KeyField> fields = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    private record KeyField(String key, TextFieldWidget field) {}
    private record Label(String text, int x, int y) {}

    public DropRateConfigScreen() {
        super(Text.literal("爆率编辑器"));
        // 打开即请求服务端把当前值发来(异步;到达后 onValues 会刷新输入框)
        ClientPlayNetworking.send(new RequestConfigPayload());
    }

    /** 收到服务端回传:解析 key=value 多行存入缓存;若编辑器正开着,刷新其输入框预填值。 */
    public static void onValues(String data) {
        CACHE.clear();
        for (String line : data.split("\n")) {
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            CACHE.put(line.substring(0, eq), line.substring(eq + 1));
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof DropRateConfigScreen s) {
            s.rebuildWidgets(); // 重建:用新缓存预填输入框
        }
    }

    @Override
    protected void init() {
        fields.clear();
        labels.clear();

        // 两列布局:每列 标签 + 输入框
        int colW = 196;
        int fieldW = 58;
        int labelW = colW - fieldW - 4;
        int gap = 12;
        int totalW = colW * 2 + gap;
        int x0 = (this.width - totalW) / 2;
        int top = 46;
        int rowH = 22;

        String[] keys = ConfigValuesPayload.EDITABLE_KEYS;
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            int col = i % 2;
            int row = i / 2;
            int cellX = x0 + col * (colW + gap);
            int fy = top + row * rowH;

            labels.add(new Label(ConfigValuesPayload.labelOf(key), cellX, fy + 5));

            TextFieldWidget tf = new TextFieldWidget(this.textRenderer,
                    cellX + labelW + 2, fy, fieldW, 16, Text.literal(key));
            tf.setMaxLength(16);
            tf.setText(CACHE.getOrDefault(key, ""));
            addDrawableChild(tf);
            fields.add(new KeyField(key, tf));
        }

        // 底部按钮行
        int rows = (keys.length + 1) / 2;
        int by = top + rows * rowH + 10;
        by = Math.min(by, this.height - 52);
        int bw = 110, bh = 20, bgap = 6;
        int bx = (this.width - (bw * 4 + bgap * 3)) / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("✔ 应用并保存"), b -> applyAll())
                .dimensions(bx, by, bw, bh).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("↻ 刷新当前值"),
                        b -> ClientPlayNetworking.send(new RequestConfigPayload()))
                .dimensions(bx + (bw + bgap), by, bw, bh).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⤓ 导出配置"), b -> run("yongye config export"))
                .dimensions(bx + (bw + bgap) * 2, by, bw, bh).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(bx + (bw + bgap) * 3, by, bw, bh).build());
    }

    /** 把每个输入框的值都 config set 下去(留空的跳过);完成后刷新显示。 */
    private void applyAll() {
        for (KeyField kf : fields) {
            String v = kf.field().getText().trim();
            if (v.isEmpty()) continue;
            run("yongye config set " + kf.key() + " " + v);
        }
        // 重新拉取一次,确认服务端最终值(也会刷新输入框)
        ClientPlayNetworking.send(new RequestConfigPayload());
    }

    private void run(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.networkHandler.sendCommand(command);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 爆率编辑器(直接输入,应用即保存)◆").formatted(Formatting.GOLD),
                this.width / 2, 14, 0xFFFFD700);

        if (CACHE.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("正在读取当前配置…").formatted(Formatting.GRAY),
                    this.width / 2, 30, 0xFFAAAAAA);
        }
        // 各字段标签
        for (Label l : labels) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal(l.text()).formatted(Formatting.AQUA), l.x(), l.y(), 0xFF55FFFF);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
