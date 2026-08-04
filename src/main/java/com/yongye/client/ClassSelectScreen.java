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

    /** m453:召唤师随枚举移除,页签回到 PlayerClass.values() 全量(六职业);
     *  SKILL_INTRO 按 values() 顺序一一对位,渲染处用 ordinal 取行。 */
    private final PlayerClass[] classes = PlayerClass.values();
    private final YongyeButton[] tabs = new YongyeButton[PlayerClass.values().length];
    private int sel = 0;

    // init() 里算好的布局(底部页签行/确认行 Y)
    private int tabY, confirmY;

    /** m228:技能按键介绍(与 PlayerClass.values() 顺序一一对应:肉盾/战士/术士/剑客/武僧/刺客)。 */
    private static final String[][] SKILL_INTRO = {
            {"【X】不动如山:嘲讽群怪 + 抗性/吸收护体", "【C】盾击:小范围重击 + 击退减速", "被动:嘲讽聚怪 · 护盾吸收 · 格挡反震 · 15% 真减伤"},
            {"【X】旋风斩:周身范围一击", "【C】战吼:震慑周围(虚弱/缓慢),自身力量", "被动:吸血 15% · 残血目标斩杀"},
            {"【X】灭世:献祭生命,大范围魔法爆发", "【C】暗影分身:召唤2个分身(50%血/100%攻)", "潜行=耗血蓄力 · 法杖按住右键蓄力,按秒倍增"},
            {"【X】万剑归一:前方大范围剑气洞穿", "【C】剑气斩:前方短距剑气", "连斩 10 刀自动放穿透剑气 · 招架反弹 · +12% 移速"},
            {"【X】百裂拳:周身连击 + 强力击退", "【C】金钟罩:短时抗性II + 回复", "空手连击层层叠伤 · 15% 缴械 · 越打越痛"},
            {"【X】影遁:隐身 + 迅捷突袭", "【C】疾影步:向前猛冲 + 短暂加速", "背刺伤害翻倍 · 20% 闪避/暴击 · 脱战加速+夜视"},
    };
    private static final String COMMON_INTRO = "通用:R/G/V=混沌之刃武器技能(需持有) · 大招与小技能冷却互不占用 · 按键均可在 设置-按键 里改";

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
                    Text.literal(classes[i].cn), b -> {
                        TabSwitchFx.trigger(this, idx - this.sel);   // m391 页签过渡(海报随 sel 切,滑动+薄纱)
                        this.sel = idx;
                    });
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

        // 技能按键介绍(m228):压在底部按钮带上方,三行——大招 / 职业机制 / 通用键位
        // m426:按 ordinal 取行(SKILL_INTRO=values() 全量对位)——页签被下架过滤后 sel 与枚举序不再同步
        String[] intro = SKILL_INTRO[Math.min(classes[sel].ordinal(), SKILL_INTRO.length - 1)];
        int introY = tabY - 8 - 4 * 11 - 6;
        ctx.fill(0, introY - 4, this.width, tabY - 8, 0x99050710);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(intro[0]), this.width / 2, introY, 0xFFFFD700);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(intro[1]), this.width / 2, introY + 11, 0xFF7FE8C8);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(intro[2]), this.width / 2, introY + 22, 0xFFCFE8FF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(COMMON_INTRO), this.width / 2, introY + 33, 0xFF8A93A3);

        // 当前页签指示:按钮正下方一条金色底条
        YongyeButton cur = tabs[sel];
        if (cur != null) {
            ctx.fill(cur.getX(), cur.getY() + 20, cur.getX() + cur.getWidth(), cur.getY() + 22, 0xFFFFD700);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }
}
