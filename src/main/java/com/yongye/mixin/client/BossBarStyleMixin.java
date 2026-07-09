package com.yongye.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BOSS 血条画框 + 自适应布局(m184,重写 m181 布局层,贴图管线不变):
 * <ul>
 *   <li><b>同类 BOSS 合并成一根血条</b>(修「BOSS 太多满屏框」):同一类型的多只 BOSS
 *       (同翻译键 / 同为怪物BOSS版 / 同为玩家皮肤BOSS)合并为一根画框条,血量取组内
 *       平均,牌匾名字带「×N」计数;怪物BOSS版 / 玩家BOSS 这类组内名字各异的,再在
 *       框旁边画一行小字成分标注(如「僵尸×3 骷髅×2」,超过 3 种缩略成「等N种」),
 *       放不下右侧自动换到左侧。同名的两根条本来就无法区分,合并零信息损失。</li>
 *   <li><b>名字随档缩放 + 浮点对中</b>(修「字没放到正确的位置」):m181 文字恒 9px、
 *       int 取整对齐——小档下框只剩 ~35px 高,9px 大字盖到框顶装饰上,视觉=浮在框外。
 *       现在文字按档位缩放(大 1.0 / 中 0.85 / 小 0.7,MatrixStack push/translate/
 *       scale 与 TitleScreenMixin 同款 proven 写法),并以浮点精确对齐牌匾中心。</li>
 *   <li><b>牌匾中心全量校准</b>(逐张贴图加刻度尺人工复核):凤凰 89→86、蜘蛛 52→58、
 *       苦力怕 61→57、僵尸 112→97;阿努比斯/龙/法师核对无误;佩恩仍无牌匾名字悬浮框顶。</li>
 *   <li><b>布局语义简化</b>:画框组按「框顶 = 游标」排版,名字画进框内牌匾,不再借用
 *       原版「名字行在条上方」的游标语义;原版条(任务计时/原版凋灵…)排在所有画框
 *       组之后照原版画法。三层绘制(槽底→血条→框压顶)/高清缩放 drawTexture/mcmeta
 *       双线性全承 m181。</li>
 *   <li><b>自动降档</b>:合并后的<b>行数</b> ≤2 大档(槽宽182)/ 3~4 中档(136)/ ≥5 小档(100)。
 *       合并让行数=类型数,实战绝大多数时候停留在大/中档。</li>
 * </ul>
 *
 * <p><b>安全</b>:render 注入 require=0——不挂则整套回退原版血条不崩;嵌套 record/类
 * 只是数据壳(同文件 Style record 自 m178 起 proven);无画框条时提前 return 零开销。
 */
@Mixin(BossBarHud.class)
public abstract class BossBarStyleMixin {

    /** 三档血条槽的屏幕(GUI 逻辑)宽度:大 = 原版血条等长。 */
    private static final int[] SLOT_W = {182, 136, 100};
    /** 三档名字缩放:小档下 9px 原字比牌匾还高,必须随档缩。 */
    private static final float[] TEXT_SCALE = {1.0f, 0.85f, 0.7f};
    /** 画框行间距(屏幕像素)。 */
    private static final int ROW_GAP = 11;

    /**
     * 画框几何,全部是<b>贴图像素</b>(生成脚本输出,与 PNG 一一对应):
     * 框宽高 / 槽偏移 xy / 槽宽高 / 牌匾中心 y(-1 = 无牌匾,名字悬浮框顶上方)。
     * 屏幕尺寸 = 贴图像素 × (SLOT_W[档位] / sw)。
     */
    private record Style(Identifier frame, Identifier back, Identifier fill,
                         int fw, int fh, int sx, int sy, int sw, int sh, int pcy) {}

    /** 同类合并组:样式 + 组内成员(保持服务端下发顺序)。 */
    private static final class Group {
        final Style st;
        final List<ClientBossBar> members = new ArrayList<>();
        Group(Style st) { this.st = st; }
    }

    private static Style yongye$style(String key, int fw, int fh, int sx, int sy, int sw, int sh, int pcy) {
        String base = "textures/gui/bossbar/" + key;
        return new Style(
                Identifier.of("yongye", base + "_frame.png"),
                Identifier.of("yongye", base + "_back.png"),
                Identifier.of("yongye", base + "_fill.png"),
                fw, fh, sx, sy, sw, sh, pcy);
    }

    // 几何常量由贴图生成脚本输出,牌匾中心 m184 逐张刻度尺校准
    private static final Style ANUBIS  = yongye$style("anubis",  1055, 229, 163, 132, 728, 51,  85);
    private static final Style PHOENIX = yongye$style("phoenix", 1102, 241, 188,  97, 728, 86,  86);
    private static final Style DRAGON  = yongye$style("dragon",  1154, 333, 214, 173, 728, 56, 105);
    private static final Style SPIDER  = yongye$style("spider",  1180, 239, 226, 100, 728, 29,  58);
    private static final Style MAGE    = yongye$style("mage",    1084, 222, 179, 102, 728, 54,  77);
    private static final Style PAIN    = yongye$style("pain",    1122, 219, 197,  88, 728, 67,  -1);
    private static final Style CREEPER = yongye$style("creeper", 1112, 252, 192,  74, 728, 89,  57);
    private static final Style ZOMBIE  = yongye$style("zombie",  1166, 389, 215, 173, 728, 55,  97);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$layout(DrawContext ctx, CallbackInfo ci) {
        BossBarHudAccess acc = (BossBarHudAccess) this;
        var bars = acc.yongye$getBossBars();
        if (bars.isEmpty()) return;

        // ① 分组:画框条按组键合并(保持下发顺序),原版条另存
        LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
        List<ClientBossBar> vanilla = new ArrayList<>();
        for (ClientBossBar bar : bars.values()) {
            Style st = yongye$styleOf(bar);
            if (st == null) { vanilla.add(bar); continue; }
            groups.computeIfAbsent(yongye$groupKey(bar), k -> new Group(st)).members.add(bar);
        }
        if (groups.isEmpty()) return; // 一根画框条都没有 → 交回原版(零开销路径)
        ci.cancel();

        // ② 尺寸档按合并后的行数定:≤2 大 / 3~4 中 / ≥5 小
        int tier = groups.size() <= 2 ? 0 : (groups.size() <= 4 ? 1 : 2);
        float ts = TEXT_SCALE[tier];

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx = ctx.getScaledWindowWidth() / 2;
        int screenW = ctx.getScaledWindowWidth();
        int halfH = ctx.getScaledWindowHeight() / 2;
        int j = 6; // 游标 = 下一行画框的顶边

        // ③ 画框组
        for (Group g : groups.values()) {
            Style st = g.st;
            int n = g.members.size();
            float s = SLOT_W[tier] / (float) st.sw;          // 贴图像素 → 屏幕像素
            int fw = Math.round(st.fw * s), fh = Math.round(st.fh * s);
            int ox = Math.round(st.sx * s), oy = Math.round(st.sy * s);
            int slotW = SLOT_W[tier], slotH = Math.max(1, Math.round(st.sh * s));

            int fy0;       // 框顶
            float nameCy;  // 名字中心线(浮点,精确对牌匾)
            if (st.pcy >= 0) {
                fy0 = j;
                nameCy = fy0 + st.pcy * s;
            } else {       // 佩恩:无牌匾,名字悬浮框顶上方,先给名字留高
                int nameH = Math.round(9 * ts) + 3;
                fy0 = j + nameH;
                nameCy = j + (9 * ts) / 2f;
            }

            // 槽底(掉血露出熄灭槽)→ 血条按组平均百分比横向裁 → 框压顶
            ctx.drawTexture(st.back, fx(cx, fw) + ox, fy0 + oy, slotW, slotH,
                    0f, 0f, st.sw, st.sh, st.sw, st.sh);
            float pct = 0f;
            for (ClientBossBar b : g.members) pct += b.getPercent();
            pct = Math.max(0f, Math.min(1f, pct / n));
            int w = Math.round(slotW * pct);
            int rw = Math.round(st.sw * pct);
            if (w > 0 && rw > 0) {
                ctx.drawTexture(st.fill, fx(cx, fw) + ox, fy0 + oy, w, slotH,
                        0f, 0f, rw, st.sh, st.sw, st.sh);
            }
            ctx.drawTexture(st.frame, fx(cx, fw), fy0, fw, fh,
                    0f, 0f, st.fw, st.fh, st.fw, st.fh);

            // 名字(带 ×N)缩放画在牌匾中心;成分标注画在框旁
            Text label = yongye$label(g);
            yongye$drawScaled(ctx, tr, label, cx - tr.getWidth(label) * ts / 2f, nameCy - 4.5f * ts, ts, 0xFFFFFF);
            String ann = yongye$annotation(g);
            if (ann != null) {
                Text at = Text.literal(ann);
                float as = 0.8f * ts;
                float aw = tr.getWidth(at) * as;
                float ax = fx(cx, fw) + fw + 4;
                if (ax + aw > screenW - 2) ax = fx(cx, fw) - 4 - aw; // 右边放不下换左边
                if (ax < 2) ax = 2;
                yongye$drawScaled(ctx, tr, at, ax, fy0 + oy + slotH / 2f - 4.5f * as, as, 0xFFCC66);
            }

            j = fy0 + fh + ROW_GAP; // 按真实框高推进(防重叠)
            if (j >= halfH) return; // 上限半屏
        }

        // ④ 原版条(任务计时/原版凋灵…)排在画框组之后,照原版画法
        for (ClientBossBar bar : vanilla) {
            Text name = bar.getName();
            int tw = tr.getWidth(name);
            ctx.drawTextWithShadow(tr, name, cx - tw / 2, j, 0xFFFFFF);
            acc.yongye$renderVanillaBar(ctx, cx - 91, j + 9, bar);
            j += 26;
            if (j >= halfH) return;
        }
    }

    /** 框左边缘(框居中于屏幕)。 */
    private static int fx(int cx, int fw) {
        return cx - fw / 2;
    }

    /** MatrixStack 缩放画字(TitleScreenMixin 同款 proven 写法),x/y 为屏幕像素、文字左上角。 */
    private static void yongye$drawScaled(DrawContext ctx, TextRenderer tr, Text text,
                                          float x, float y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawTextWithShadow(tr, text, 0, 0, color);
        ctx.getMatrices().pop();
    }

    /** 组的牌匾名:单只 = 原名;多只 = 「类型名 ×N」(混名组用统一类型名)。 */
    private static Text yongye$label(Group g) {
        int n = g.members.size();
        Text first = g.members.get(0).getName();
        if (n == 1) return first;
        String base;
        if (g.st == CREEPER) base = "BOSS怪";
        else if (g.st == ZOMBIE) base = "玩家BOSS";
        else base = first.getString();
        return Text.literal(base + " ×" + n);
    }

    /** 混名组(怪物BOSS版/玩家BOSS)合并后的成分标注:「僵尸×3 骷髅×2」,>3 种缩略。 */
    private static String yongye$annotation(Group g) {
        if (g.members.size() < 2 || (g.st != CREEPER && g.st != ZOMBIE)) return null;
        LinkedHashMap<String, Integer> comp = new LinkedHashMap<>();
        for (ClientBossBar b : g.members) {
            String nm = b.getName().getString();
            if (nm.startsWith("【BOSS】")) nm = nm.substring("【BOSS】".length());
            if (nm.endsWith(" BOSS")) nm = nm.substring(0, nm.length() - " BOSS".length());
            nm = nm.trim();
            if (nm.isEmpty()) nm = "?";
            comp.merge(nm, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> e : comp.entrySet()) {
            if (shown == 3) { sb.append(" 等").append(comp.size()).append("种"); break; }
            if (shown > 0) sb.append(' ');
            sb.append(e.getKey()).append('×').append(e.getValue());
            shown++;
        }
        return sb.toString();
    }

    /** 识别血条画框:先翻译键(语言无关),再字面量前后缀兜底。 */
    private static Style yongye$styleOf(ClientBossBar bar) {
        Text name = bar.getName();
        if (name == null) return null;
        if (name.getContent() instanceof TranslatableTextContent t) {
            String key = t.getKey();
            if ("entity.yongye.anubis".equals(key)) return ANUBIS;
            if ("entity.yongye.fire_phoenix".equals(key)) return PHOENIX;
            if ("entity.yongye.red_spider".equals(key)) return SPIDER;
            if ("entity.yongye.death_mage".equals(key)) return MAGE;
            if ("entity.yongye.toro_ender_dragon".equals(key)) return DRAGON;
            if ("entity.minecraft.ender_dragon".equals(key)) return DRAGON; // 原版末地龙战
            return null;
        }
        String s = name.getString();
        if (s.contains("佩恩")) return PAIN;              // 长门·佩恩(字面量名「佩恩·天道」)
        if (s.endsWith(" BOSS")) return ZOMBIE;           // 玩家皮肤僵尸BOSS(m145,本体是僵尸)
        if (s.startsWith("【BOSS】")) return CREEPER;     // 其余怪物BOSS版(字面量名)
        return null;
    }

    /** 合并组键:同键的条并成一根。翻译键各自成组;佩恩/玩家BOSS/怪物BOSS版各按类别成组。 */
    private static String yongye$groupKey(ClientBossBar bar) {
        Text name = bar.getName();
        if (name.getContent() instanceof TranslatableTextContent t) return t.getKey();
        String s = name.getString();
        if (s.contains("佩恩")) return "yongye:pain";
        if (s.endsWith(" BOSS")) return "yongye:zombie_group";
        return "yongye:creeper_group";
    }
}
