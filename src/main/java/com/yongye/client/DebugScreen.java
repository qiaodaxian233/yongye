package com.yongye.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试 / 运营菜单:把常用的 /yongye 命令做成一排排按钮,点一下即执行,免去手敲。
 *
 * 实现要点(零编译风险、纯客户端、不新增任何服务端逻辑):
 *  - 入口:`/yongye debug`(OP)→ 服务端发 OpenDebugPayload → 客户端 setScreen 打开本界面;
 *  - 每个按钮点击 = client.player.networkHandler.sendCommand("yongye ...")，相当于
 *    「替玩家在聊天框敲这条命令」(命令串不带前导斜杠);命令仍在服务端按权限 2 执行,
 *    所以能打开本菜单的玩家(已是 OP)点按钮才有效——权限边界与命令本身天然一致;
 *  - 只是现有命令的快捷入口,不依赖 mixin。
 *
 * 注:数值型命令(发书等级、强化等级等)按钮里写了常用固定值;要自定义数值仍可手敲命令。
 */
@Environment(EnvType.CLIENT)
public class DebugScreen extends Screen {

    // —— 布局参数(按钮 4 列网格)——
    private static final int COLS = 4;
    private static final int BTN_W = 96;
    private static final int BTN_H = 18;
    private static final int GAP_X = 4;
    private static final int GAP_Y = 3;
    private static final int HEADER_H = 12; // 每个分组标题占的高度

    // init() 里登记每个分组标题与其 y 坐标,render() 时统一绘制(两表一一对应)
    private final List<String> headerTexts = new ArrayList<>();
    private final List<Integer> headerYs = new ArrayList<>();

    public DebugScreen() {
        super(Text.literal("永夜 · 调试菜单"));
    }

    @Override
    protected void init() {
        headerTexts.clear();
        headerYs.clear();

        // 整个网格垂直居中:先估算总高度,再定起点 y(6 个分组,每组 = 标题 + 1 行按钮 + 组间距)
        int groups = 8;
        int blockH = HEADER_H + BTN_H + GAP_Y;
        int totalH = 24 /*顶部标题*/ + groups * blockH + 28 /*底部关闭按钮区*/;
        int startY = Math.max(30, (this.height - totalH) / 2 + 24);

        int gridW = COLS * BTN_W + (COLS - 1) * GAP_X;
        int x0 = (this.width - gridW) / 2;

        int y = startY;

        // —— 永夜 / 节奏 ——(永夜·深渊 7 用到本轮修好的 nightfall 参数解封顶)
        y = section(x0, y, "永夜 / 节奏", new Btn[]{
                new Btn("永夜·清(0)", "yongye nightfall 0"),
                new Btn("永夜·灭世(5)", "yongye nightfall 5"),
                new Btn("永夜·深渊(7)", "yongye nightfall 7"),
                new Btn("赎夜 -1", "yongye redeem"),
        });

        // —— 成长道具 ——(血量书 V200 用于验证 m55 破 1024;手持强化用于验证强化链路)
        y = section(x0, y, "成长道具", new Btn[]{
                new Btn("血量书 V200", "yongye book 200"),
                new Btn("攻击书 V100", "yongye skillbook attack 100"),
                new Btn("护甲书 V100", "yongye skillbook armor 100"),
                new Btn("手持强化 +100", "yongye enhance 100"),
        });

        // —— 职业 / 武器 ——(镇魂用于验证 m55 攻防双修 HYBRID 强化)
        y = section(x0, y, "职业 / 武器", new Btn[]{
                new Btn("镇魂(坦克)", "yongye classweapon tank"),
                new Btn("磐盾", "yongye tankshield"),
                new Btn("混沌之刃", "yongye chaosblade"),
                new Btn("设等级 50", "yongye level 50"),
        });

        // —— 神器 ——(L6=终焉 / L3=远古,用于验证 m56 远古/终焉合成与等级)
        y = section(x0, y, "神器", new Btn[]{
                new Btn("生命神像 L6", "yongye artifact life_idol 6"),
                new Btn("不灭余烬 L6", "yongye artifact undying_ember 6"),
                new Btn("世界之锚 L3", "yongye artifact world_anchor 3"),
                new Btn("守护书", "yongye wardbook"),
        });

        // —— 刷怪测试 ——(一键召出特殊怪实机查看:精英 / 怪物BOSS / 长门)
        y = section(x0, y, "刷怪测试", new Btn[]{
                new Btn("精英化附近", "yongye elite"),
                new Btn("BOSS化附近", "yongye mobboss"),
                new Btn("长门降临", "yongye painboss"),
        });

        // —— 事件 / 任务 ——
        y = section(x0, y, "事件 / 任务", new Btn[]{
                new Btn("灾厄核心", "yongye core"),
                new Btn("任务·猎杀", "yongye quest hunt"),
        });

        // —— 运维 ——
        y = section(x0, y, "运维", new Btn[]{
                new Btn("查掉率", "yongye loot show"),
                new Btn("重置配置", "yongye config reset"),
        });

        // —— 调参 / 配置 ——(常用预设;任意字段用 /yongye config set <字段> <值>,查全部用 config list)
        y = section(x0, y, "调参 / 配置(更多用 config set)", new Btn[]{
                new Btn("技书·精英0.15", "yongye config set skillBookDropChanceElite 0.15"),
                new Btn("技书·普通0.008", "yongye config set skillBookDropChanceNormal 0.008"),
                new Btn("碎片爆率0.3", "yongye config set lifeShardDropChance 0.3"),
                new Btn("永夜增血/级2", "yongye config set nightfallBeyondHpPerLevel 2"),
        });

        // 关闭按钮(底部居中;y 已是最后一组之后的位置)
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(this.width / 2 - 50, Math.min(this.height - 24, y + 6), 100, 20).build());
    }

    /** 摆一个分组:登记标题(render 时绘制)+ 一行按钮(最多 COLS 个);返回下一组的起始 y。 */
    private int section(int x0, int y, String title, Btn[] btns) {
        headerTexts.add(title);
        headerYs.add(y);
        int by = y + HEADER_H;
        for (int i = 0; i < btns.length && i < COLS; i++) {
            int bx = x0 + i * (BTN_W + GAP_X);
            final String cmd = btns[i].cmd(); // 捕获到 lambda,避免引用循环变量
            addDrawableChild(ButtonWidget.builder(Text.literal(btns[i].label()), b -> run(cmd))
                    .dimensions(bx, by, BTN_W, BTN_H).build());
        }
        return by + BTN_H + GAP_Y;
    }

    /** 替玩家执行一条 /yongye 命令(命令串不含前导斜杠,sendCommand 自会处理)。 */
    private void run(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendCommand(command);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta); // 绘制所有按钮

        // 顶部大标题
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 调试菜单 ◆").formatted(Formatting.GOLD), this.width / 2, 12, 0xFFFFD700);

        // 各分组标题(左对齐到按钮网格左边缘)
        int gridW = COLS * BTN_W + (COLS - 1) * GAP_X;
        int x0 = (this.width - gridW) / 2;
        for (int i = 0; i < headerTexts.size(); i++) {
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal(headerTexts.get(i)).formatted(Formatting.AQUA),
                    x0, headerYs.get(i), 0xFF55FFFF);
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null); // 命令直开,无父界面,直接关回游戏
    }

    /** 调试菜单不暂停游戏:方便点完命令立刻观察效果(如刷出的 Boss 会继续行动)。 */
    @Override
    public boolean shouldPause() {
        return false;
    }

    /** 一个命令按钮的定义:显示文本 + 要执行的命令串。 */
    private record Btn(String label, String cmd) {}
}
