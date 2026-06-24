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
 * 调试 / 运营菜单:把**所有可用的** /yongye(及 /talent)命令做成按钮,点一下即执行,免去手敲。
 *
 * 实现要点(零编译风险、纯客户端、不新增任何服务端逻辑):
 *  - 入口:`/yongye debug`(OP)→ 服务端发 OpenDebugPayload → 客户端 setScreen 打开本界面;
 *  - 每个按钮点击 = client.player.networkHandler.sendCommand("...")(命令串不带前导斜杠),
 *    相当于「替玩家在聊天框敲这条命令」;命令仍在服务端按权限 2 执行;
 *  - 命令多,改为**顶部分类标签分页**:每页只显示一类命令,点标签切页(清空后重建本页按钮)。
 *
 * 注:带参数的命令(发书等级、强化等级、神器等级、config 数值等)按钮里写了常用固定值;
 *     要自定义数值/未列出的字段,仍可手敲命令(config set <字段> <值>,config list 查全部)。
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

    // 当前页签索引
    private int page = 0;

    // init() 里登记每个分组标题与其 y 坐标,render() 时统一绘制(两表一一对应)
    private final List<String> headerTexts = new ArrayList<>();
    private final List<Integer> headerYs = new ArrayList<>();

    public DebugScreen() {
        super(Text.literal("永夜 · 调试菜单"));
    }

    // ============ 全部命令数据(按页签分类)============
    private record Btn(String label, String cmd) {}
    private record Section(String title, Btn[] btns) {}
    private record Page(String tab, Section[] sections) {}

    private static final Page[] PAGES = new Page[]{
            // —— 永夜 / 任务 ——
            new Page("永夜", new Section[]{
                    new Section("永夜等级 / 节奏", new Btn[]{
                            new Btn("永夜·清 0", "yongye nightfall 0"),
                            new Btn("永夜·灾变 4", "yongye nightfall 4"),
                            new Btn("永夜·灭世 5", "yongye nightfall 5"),
                            new Btn("永夜·深渊 7", "yongye nightfall 7"),
                            new Btn("永夜状态", "yongye nightfall status"),
                            new Btn("赎夜 -1", "yongye redeem"),
                    }),
                    new Section("任务(quest)", new Btn[]{
                            new Btn("猎杀", "yongye quest hunt"),
                            new Btn("存活", "yongye quest survive"),
                            new Btn("逃亡", "yongye quest flee"),
                            new Btn("核心", "yongye quest core"),
                            new Btn("采集", "yongye quest gather"),
                    }),
                    new Section("排行", new Btn[]{
                            new Btn("存活排行", "yongye top"),
                    }),
            }),
            // —— 成长道具 ——
            new Page("道具", new Section[]{
                    new Section("成长", new Btn[]{
                            new Btn("血量书 V200", "yongye book 200"),
                            new Btn("设等级 50", "yongye level 50"),
                            new Btn("设等级 500", "yongye level 500"),
                            new Btn("手持强化+100", "yongye enhance 100"),
                            new Btn("手持强化+500", "yongye enhance 500"),
                            new Btn("找回强化", "yongye recover"),
                    }),
                    new Section("技能书(V50;type level)", new Btn[]{
                            new Btn("攻击 attack", "yongye skillbook attack 50"),
                            new Btn("护甲 armor", "yongye skillbook armor 50"),
                            new Btn("回复 regen", "yongye skillbook regen 50"),
                            new Btn("闪避 evasion", "yongye skillbook evasion 50"),
                            new Btn("荆棘 thorns", "yongye skillbook thorns 50"),
                            new Btn("抗性 resist", "yongye skillbook resistance 50"),
                            new Btn("饱腹 satiety", "yongye skillbook satiety 50"),
                            new Btn("偷取 steal", "yongye skillbook steal 50"),
                    }),
            }),
            // —— 神器 ——
            new Page("神器", new Section[]{
                    new Section("神器(L3;可手敲 artifact <type> 1-6)", new Btn[]{
                            new Btn("生命神像", "yongye artifact life_idol 3"),
                            new Btn("铁壁核心", "yongye artifact iron_core 3"),
                            new Btn("骨箭护符", "yongye artifact bone_arrow_charm 3"),
                            new Btn("巫毒净瓶", "yongye artifact voodoo_bottle 3"),
                            new Btn("逃亡之靴", "yongye artifact escapist_boots 3"),
                            new Btn("掘墓罗盘", "yongye artifact gravedigger_compass 3"),
                            new Btn("不灭余烬", "yongye artifact undying_ember 3"),
                            new Btn("永夜之眼", "yongye artifact nightfall_eye 3"),
                            new Btn("饕餮心脏", "yongye artifact glutton_heart 3"),
                            new Btn("世界锚石", "yongye artifact world_anchor 3"),
                    }),
                    new Section("其它", new Btn[]{
                            new Btn("守护附魔书", "yongye wardbook"),
                    }),
            }),
            // —— 职业 / 武器 ——
            new Page("职业", new Section[]{
                    new Section("职业书(classbook)", new Btn[]{
                            new Btn("坦克 tank", "yongye classbook tank"),
                            new Btn("战士 warrior", "yongye classbook warrior"),
                            new Btn("术士 warlock", "yongye classbook warlock"),
                            new Btn("剑客 swordsman", "yongye classbook swordsman"),
                            new Btn("武僧 monk", "yongye classbook monk"),
                            new Btn("刺客 assassin", "yongye classbook assassin"),
                    }),
                    new Section("职业专属武器(classweapon)", new Btn[]{
                            new Btn("坦克·镇魂", "yongye classweapon tank"),
                            new Btn("战士武器", "yongye classweapon warrior"),
                            new Btn("术士武器", "yongye classweapon warlock"),
                            new Btn("剑客武器", "yongye classweapon swordsman"),
                            new Btn("刺客武器", "yongye classweapon assassin"),
                    }),
                    new Section("特殊武器", new Btn[]{
                            new Btn("混沌之刃", "yongye chaosblade"),
                    }),
            }),
            // —— 刷怪 / 事件 ——
            new Page("刷怪", new Section[]{
                    new Section("一键召出(就地/附近)", new Btn[]{
                            new Btn("精英化附近", "yongye elite"),
                            new Btn("BOSS化附近", "yongye mobboss"),
                            new Btn("长门降临", "yongye painboss"),
                            new Btn("灾厄核心", "yongye core"),
                    }),
            }),
            // —— 掉率(loot)——
            new Page("掉率", new Section[]{
                    new Section("掉率(loot;即时生效并保存)", new Btn[]{
                            new Btn("查看掉率", "yongye loot show"),
                            new Btn("碎片 0.10", "yongye loot shard 0.1"),
                            new Btn("结晶 0.05", "yongye loot crystal 0.05"),
                            new Btn("核心 0.05", "yongye loot core 0.05"),
                            new Btn("血核 0.025", "yongye loot bloodcore 0.025"),
                            new Btn("掉落·开", "yongye loot enable true"),
                            new Btn("掉落·关", "yongye loot enable false"),
                    }),
            }),
            // —— 配置(config)——
            new Page("配置", new Section[]{
                    new Section("常用数值(config set 预设)", new Btn[]{
                            new Btn("技书精英0.001", "yongye config set skillBookDropChanceElite 0.001"),
                            new Btn("技书普通0.001", "yongye config set skillBookDropChanceNormal 0.001"),
                            new Btn("碎片 0.10", "yongye config set lifeShardDropChance 0.1"),
                            new Btn("结晶 0.05", "yongye config set lifeCrystalDropChance 0.05"),
                            new Btn("永夜增血/级2", "yongye config set nightfallBeyondHpPerLevel 2"),
                            new Btn("佩恩血 2万", "yongye config set painBossMaxHealth 20000"),
                            new Btn("佩恩攻 2000", "yongye config set painBossAttack 2000"),
                            new Btn("脉动黑暗·开", "yongye config set nightfallDarknessEffect true"),
                    }),
                    new Section("Boss 门控(掠夺者队长)", new Btn[]{
                            new Btn("队长Boss第8天", "yongye config set bossRaidCaptainMinDay 8"),
                            new Btn("队长Boss第15天", "yongye config set bossRaidCaptainMinDay 15"),
                            new Btn("队长Boss关闭", "yongye config set bossRaidCaptainMinDay 9999"),
                    }),
                    new Section("维护", new Btn[]{
                            new Btn("列出全部字段", "yongye config list"),
                            new Btn("导出配置(路径)", "yongye config export"),
                            new Btn("重置为默认", "yongye config reset"),
                    }),
            }),
            // —— 天赋(talent)——
            new Page("天赋", new Section[]{
                    new Section("天赋(learn/info 需 id,见列表)", new Btn[]{
                            new Btn("天赋总览", "talent"),
                            new Btn("天赋列表", "talent list"),
                            new Btn("重置天赋", "talent reset"),
                    }),
            }),
    };

    @Override
    protected void init() {
        headerTexts.clear();
        headerYs.clear();

        int gridW = COLS * BTN_W + (COLS - 1) * GAP_X;
        int x0 = (this.width - gridW) / 2;

        // —— 顶部页签行 ——(点击切页:设页码 → 清空重建)
        int tabY = 26;
        int tabH = 16;
        int tabGap = 2;
        int tabW = (gridW - (PAGES.length - 1) * tabGap) / PAGES.length;
        for (int i = 0; i < PAGES.length; i++) {
            final int idx = i;
            int tx = x0 + i * (tabW + tabGap);
            ButtonWidget tab = ButtonWidget.builder(Text.literal(PAGES[i].tab()), b -> {
                this.page = idx;
                this.clearAndInit();  // 1.21.1 标准:清空子控件并重跑 init() 重建为新页
            }).dimensions(tx, tabY, tabW, tabH).build();
            tab.active = (i != page);   // 当前页签置灰=高亮"正处于此页"
            addDrawableChild(tab);
        }

        // —— 当前页内容 ——
        int y = tabY + tabH + 8;
        for (Section s : PAGES[page].sections()) {
            y = section(x0, y, s.title(), s.btns());
        }

        // 掉率页:额外加一个「直接输入编辑」按钮(客户端动作,打开爆率编辑器,而非发命令)
        if ("掉率".equals(PAGES[page].tab())) {
            addDrawableChild(ButtonWidget.builder(
                            Text.literal("✎ 爆率编辑器(直接输入数值)").formatted(Formatting.YELLOW),
                            b -> MinecraftClient.getInstance().setScreen(new DropRateConfigScreen()))
                    .dimensions(x0, y + 2, COLS * BTN_W + (COLS - 1) * GAP_X, BTN_H).build());
            y += BTN_H + GAP_Y;
        }

        // 关闭按钮(底部居中)
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(this.width / 2 - 50, Math.min(this.height - 24, y + 6), 100, 20).build());
    }

    /** 摆一个分组:登记标题(render 时绘制)+ 按钮(超过 COLS 个自动换行);返回下一组的起始 y。 */
    private int section(int x0, int y, String title, Btn[] btns) {
        headerTexts.add(title);
        headerYs.add(y);
        int by = y + HEADER_H;
        for (int i = 0; i < btns.length; i++) {
            int col = i % COLS;
            int rowIdx = i / COLS;
            int bx = x0 + col * (BTN_W + GAP_X);
            int byy = by + rowIdx * (BTN_H + GAP_Y);
            final String cmd = btns[i].cmd(); // 捕获到 lambda,避免引用循环变量
            addDrawableChild(ButtonWidget.builder(Text.literal(btns[i].label()), b -> run(cmd))
                    .dimensions(bx, byy, BTN_W, BTN_H).build());
        }
        int rows = (btns.length + COLS - 1) / COLS;
        return by + rows * (BTN_H + GAP_Y);
    }

    /** 替玩家执行一条命令(命令串不含前导斜杠,sendCommand 自会处理)。 */
    private void run(String command) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.networkHandler.sendCommand(command);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx, mouseX, mouseY, delta);
        super.render(ctx, mouseX, mouseY, delta); // 绘制所有按钮(含页签)

        // 顶部大标题
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("◆ 调试菜单 · " + PAGES[page].tab() + " ◆").formatted(Formatting.GOLD),
                this.width / 2, 12, 0xFFFFD700);

        // 当前页各分组标题(左对齐到按钮网格左边缘)
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
}
