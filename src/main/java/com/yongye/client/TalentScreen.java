package com.yongye.client;

import com.yongye.item.PlayerClass;
import com.yongye.network.TalentLearnPayload;
import com.yongye.system.TalentManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * 天赋界面:按已习得职业逐行展示各自天赋树的 5 个节点。
 * 数据来源:ClientTalents(服务端同步的点数/已点等级) + TalentManager.treeView(通用代码,直接读树结构)。
 * 交互:点击"可加点"的节点 → 发 TalentLearnPayload → 服务端校验加点 → 回传 TalentSyncPayload → 本界面下一帧即刷新。
 * 仅用已确认的 Screen API(renderBackground / fill / drawCenteredTextWithShadow / ButtonWidget / mouseClicked)。
 */
public class TalentScreen extends Screen {

    private final Screen parent;

    // 节点盒尺寸与排布
    private static final int NW = 100, NH = 40, GAP_X = 8, ROW_GAP = 26;
    private static final int TOP = 64;

    public TalentScreen(Screen parent) {
        super(Text.literal("天赋"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 返回按钮(复用 StatsScreen 的做法)
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 30, 100, 20).build());
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    /** 当前要展示的职业列表(过滤掉无法识别的 id)。 */
    private List<String> classIds() {
        return ClientTalents.classes;
    }

    /** 第 ci 个职业行、第 ni 个节点的左上角 X(按该行节点总数 count 居中,自适应节点数变化)。 */
    private int nodeX(int ni, int count) {
        int rowW = count * NW + (count - 1) * GAP_X;
        return this.width / 2 - rowW / 2 + ni * (NW + GAP_X);
    }
    private int rowY(int ci) {
        return TOP + ci * (NH + ROW_GAP);
    }

    private int rank(String id) {
        return ClientTalents.learned.getOrDefault(id, 0);
    }

    /** 该节点当前是否可加点(与服务端 learn 的门控保持一致)。 */
    private boolean learnable(TalentManager.NodeView n) {
        if (ClientTalents.points <= 0) return false;
        if (rank(n.id()) >= n.maxRank()) return false;
        if (n.prereq() != null && rank(n.prereq()) <= 0) return false;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            List<String> ids = classIds();
            for (int ci = 0; ci < ids.size(); ci++) {
                PlayerClass c = PlayerClass.byId(ids.get(ci));
                if (c == null) continue;
                List<TalentManager.NodeView> nodes = TalentManager.treeView(c);
                int y = rowY(ci) + 12;
                for (int ni = 0; ni < nodes.size(); ni++) {
                    int x = nodeX(ni, nodes.size());
                    if (mouseX >= x && mouseX <= x + NW && mouseY >= y && mouseY <= y + NH) {
                        TalentManager.NodeView n = nodes.get(ni);
                        if (learnable(n)) {
                            ClientPlayNetworking.send(new TalentLearnPayload(n.id()));
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta);

        int cx = this.width / 2;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 天赋 ◆").formatted(Formatting.GOLD), cx, 18, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("可用天赋点:" + ClientTalents.points).formatted(Formatting.AQUA), cx, 34, 0xFF55FFFF);

        List<String> ids = classIds();
        if (ids.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("你还没有职业,无法点天赋").formatted(Formatting.GRAY), cx, TOP + 20, 0xFFAAAAAA);
            return;
        }

        String hoverDesc = null;
        for (int ci = 0; ci < ids.size(); ci++) {
            PlayerClass c = PlayerClass.byId(ids.get(ci));
            if (c == null) continue;
            List<TalentManager.NodeView> nodes = TalentManager.treeView(c);
            int headerY = rowY(ci);
            int y = headerY + 12;

            // 职业名(行首,画在第一个节点左侧上方)
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("【" + c.cn + "】").formatted(Formatting.YELLOW), nodeX(0, nodes.size()), headerY, 0xFFFFFF55);

            for (int ni = 0; ni < nodes.size(); ni++) {
                TalentManager.NodeView n = nodes.get(ni);
                int x = nodeX(ni, nodes.size());
                int r = rank(n.id());
                boolean maxed = r >= n.maxRank();
                boolean locked = n.prereq() != null && rank(n.prereq()) <= 0;
                boolean can = learnable(n);
                boolean hover = mouseX >= x && mouseX <= x + NW && mouseY >= y && mouseY <= y + NH;

                // 盒子底色:满级金 / 可点绿 / 锁灰 / 其它深蓝灰
                int bg = maxed ? 0xCC4A3B12 : can ? 0xCC1E4D2B : locked ? 0xCC2A2A2A : 0xCC1B2436;
                int border = hover ? 0xFFFFD700 : maxed ? 0xFFD9A441 : can ? 0xFF4CC76A : 0xFF555555;
                ctx.fill(x, y, x + NW, y + NH, bg);
                // 边框(四条 1px)
                ctx.fill(x, y, x + NW, y + 1, border);
                ctx.fill(x, y + NH - 1, x + NW, y + NH, border);
                ctx.fill(x, y, x + 1, y + NH, border);
                ctx.fill(x + NW - 1, y, x + NW, y + NH, border);

                int nameColor = maxed ? 0xFFFFD700 : can ? 0xFF8CFFA8 : locked ? 0xFF888888 : 0xFFCCCCCC;
                String tag = n.isSkill() ? "✦" : "◆";
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal(tag + n.cn()), x + NW / 2, y + 8, nameColor);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal(r + "/" + n.maxRank() + (locked ? " 锁" : can ? " 可点" : "")),
                        x + NW / 2, y + 24, locked ? 0xFFAA5555 : 0xFFBBBBBB);

                if (hover) hoverDesc = n.cn() + " — " + n.desc();
            }
        }

        // 悬停说明(底部)
        if (hoverDesc != null) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal(hoverDesc).formatted(Formatting.GRAY), cx, this.height - 48, 0xFFAAAAAA);
        }
    }
}
