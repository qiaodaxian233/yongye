package com.yongye.mixin.client;

import com.yongye.client.BossBarStyles.Group;
import com.yongye.client.BossBarStyles.Style;

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
 * BOSS 血条画框 + 自适应布局 + 血量数字显示(m187):
 * <ul>
 *   <li><b>血量数字</b>(m187 新增):服务端把「‖当前/最大」嵌进血条名后缀;客户端解析后
 *       显示「X.XB / 1.0B」金字,格式化为 K/M/B/T 紧凑单位(m219)。不含 ‖ 的条(原版凋灵等)
 *       兜底显示百分比。</li>
 *   <li><b>同类 BOSS 合并成一根血条</b>(m184):组内血量求和,牌匾名带「×N」。</li>
 *   <li><b>名字随档缩放 + 浮点对中</b>(m184):按档位缩放文字(大1.0/中0.85/小0.7)。</li>
 *   <li><b>牌匾中心全量校准</b>(m184 m181):8 框逐张刻度尺人工复核。</li>
 *   <li><b>自动降档</b>:合并后行数 ≤2 大档(槽宽182)/3~4 中档(136)/≥5 小档(100)。</li>
 * </ul>
 *
 * <p><b>安全</b>:render 注入 require=0——不挂则整套回退原版血条不崩。
 */
@Mixin(BossBarHud.class)
public abstract class BossBarStyleMixin {

    /** 三档血条槽的屏幕(GUI 逻辑)宽度:大 = 原版血条等长。 */
    private static final int[] SLOT_W = {182, 136, 100};
    /** 三档名字缩放。 */
    private static final float[] TEXT_SCALE = {1.0f, 0.85f, 0.7f};
    /** 画框行间距(屏幕像素)。 */
    private static final int ROW_GAP = 11;

    // m341(P0):Style/Group 移出到 com.yongye.client.BossBarStyles(Mixin 禁止 mixin 包内类被运行时类加载)

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

        // ① 分组:画框条按组键合并,原版条另存
        LinkedHashMap<String, Group> groups = new LinkedHashMap<>();
        List<ClientBossBar> vanilla = new ArrayList<>();
        for (ClientBossBar bar : bars.values()) {
            Style st = yongye$styleOf(bar);
            if (st == null) { vanilla.add(bar); continue; }
            groups.computeIfAbsent(yongye$groupKey(bar), k -> new Group(st)).members.add(bar);
        }
        if (groups.isEmpty()) return;
        ci.cancel();

        // ② 尺寸档按合并后的行数定;m365 整体再乘全局缩放(作者点名血条太大),字号有下限保可读
        int tier = groups.size() <= 2 ? 0 : (groups.size() <= 4 ? 1 : 2);
        float k = (float) Math.max(0.3, Math.min(1.5, com.yongye.YongyeConfig.get().bossBarScale));
        int slotWScaled = Math.max(40, Math.round(SLOT_W[tier] * k));
        int rowGap = Math.max(4, Math.round(ROW_GAP * k));
        float ts = Math.max(0.5f, TEXT_SCALE[tier] * k);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx = ctx.getScaledWindowWidth() / 2;
        int screenW = ctx.getScaledWindowWidth();
        int halfH = ctx.getScaledWindowHeight() / 2;
        int j = 6; // 游标 = 下一行画框的顶边

        // ③ 画框组
        for (Group g : groups.values()) {
            Style st = g.st;
            int n = g.members.size();
            float s = slotWScaled / (float) st.sw;
            int fw = Math.round(st.fw * s), fh = Math.round(st.fh * s);
            int ox = Math.round(st.sx * s), oy = Math.round(st.sy * s);
            int slotW = slotWScaled, slotH = Math.max(1, Math.round(st.sh * s));

            int fy0;
            float nameCy;
            if (st.pcy >= 0) {
                fy0 = j;
                nameCy = fy0 + st.pcy * s;
            } else {
                int nameH = Math.round(9 * ts) + 3;
                fy0 = j + nameH;
                nameCy = j + (9 * ts) / 2f;
            }

            // 槽底 → 血条(合并求和) → 框压顶
            ctx.drawTexture(st.back, fx(cx, fw) + ox, fy0 + oy, slotW, slotH,
                    0f, 0f, st.sw, st.sh, st.sw, st.sh);

            // m187:有 HP 数据时按实际比算填充,否则用百分比平均
            double[] groupHp = yongye$parseGroupHp(g);
            float pct;
            if (groupHp != null && groupHp[1] > 0) {
                pct = (float) Math.max(0.0, Math.min(1.0, groupHp[0] / groupHp[1])); // m222:double 通道后全程 double 再收窄,修编译错
            } else {
                float sum = 0f;
                for (ClientBossBar b : g.members) sum += b.getPercent();
                pct = Math.max(0f, Math.min(1f, sum / n));
            }
            int w = Math.round(slotW * pct);
            int rw = Math.round(st.sw * pct);
            if (w > 0 && rw > 0) {
                ctx.drawTexture(st.fill, fx(cx, fw) + ox, fy0 + oy, w, slotH,
                        0f, 0f, rw, st.sh, st.sw, st.sh);
            }
            ctx.drawTexture(st.frame, fx(cx, fw), fy0, fw, fh,
                    0f, 0f, st.fw, st.fh, st.fw, st.fh);

            // 名字(带 ×N,已剥离 ‖hp 后缀)
            Text label = yongye$label(g);
            yongye$drawScaled(ctx, tr, label, cx - tr.getWidth(label) * ts / 2f, nameCy - 4.5f * ts, ts, 0xFFFFFF);

            // m187/m188:血量数字画在血条槽正中(MMO 惯例;m187 初版画名字下方与牌匾名重叠已修)
            float slotCx = fx(cx, fw) + ox + slotW / 2f;
            float slotCy = fy0 + oy + slotH / 2f;
            float hpScale = 0.78f * ts;
            String hpStr = (groupHp != null && groupHp[1] > 0)
                    ? yongye$fmtHp(groupHp[0]) + " / " + yongye$fmtHp(groupHp[1])
                    : Math.round(pct * 100) + "%"; // 兜底:无 HP 数据显示百分比
            Text hpText = Text.literal(hpStr);
            yongye$drawScaled(ctx, tr, hpText,
                    slotCx - tr.getWidth(hpText) * hpScale / 2f,
                    slotCy - 4.5f * hpScale,
                    hpScale, 0xFFFFFF);

            // m304 BOSS 格挡条:血条槽正下方 3px——青蓝=格挡余量(同玩家格挡条视觉),
            // 破防=红色呼吸闪烁;仅单成员条绘制(合并组 ×N 的格挡各自独立,合着画会撒谎)。
            if (g.members.size() == 1) {
                float[] guard = yongye$parseGuard(g.members.get(0));
                if (guard != null && guard[1] > 0) {
                    int gx = fx(cx, fw) + ox;
                    int gy = fy0 + oy + slotH + 1;
                    ctx.fill(gx, gy, gx + slotW, gy + 3, 0xAA000000);
                    if (guard[2] > 0) {
                        int a = 120 + (int) (100 * Math.abs(Math.sin(System.currentTimeMillis() / 180.0)));
                        ctx.fill(gx, gy, gx + slotW, gy + 3, (a << 24) | 0xFF2020);
                    } else {
                        int fillW = (int) (slotW * Math.max(0f, Math.min(1f, guard[0] / guard[1])));
                        if (fillW > 0) ctx.fill(gx, gy, gx + fillW, gy + 3, 0xFF35D8E8);
                    }
                }
            }

            // 成分标注(怪物BOSS版/玩家BOSS混名组)
            String ann = yongye$annotation(g);
            if (ann != null) {
                Text at = Text.literal(ann);
                float as = 0.8f * ts;
                float aw = tr.getWidth(at) * as;
                float ax = fx(cx, fw) + fw + 4;
                if (ax + aw > screenW - 2) ax = fx(cx, fw) - 4 - aw;
                if (ax < 2) ax = 2;
                yongye$drawScaled(ctx, tr, at, ax, fy0 + oy + slotH / 2f - 4.5f * as, as, 0xFFCC66);
            }

            j = fy0 + fh + rowGap;
            if (j >= halfH) return;
        }

        // ④ 原版条排在画框组之后
        for (ClientBossBar bar : vanilla) {
            Text name = yongye$cleanName(bar);
            int tw = tr.getWidth(name);
            ctx.drawTextWithShadow(tr, name, cx - tw / 2, j, 0xFFFFFF);
            acc.yongye$renderVanillaBar(ctx, cx - 91, j + 9, bar);
            // m188:数字覆画在原版条正中(条 j+9..j+14,文字上缘 j+7 上下对称跨条)
            double[] hp = yongye$parseHp(bar);
            String sub = hp != null && hp[1] > 0
                    ? yongye$fmtHp(hp[0]) + " / " + yongye$fmtHp(hp[1])
                    : Math.round(bar.getPercent() * 100) + "%";
            ctx.drawTextWithShadow(tr, Text.literal(sub), cx - tr.getWidth(sub) / 2, j + 7, 0xFFFFFF);
            j += 26;
            if (j >= halfH) return;
        }
    }

    /** 框左边缘。 */
    private static int fx(int cx, int fw) { return cx - fw / 2; }

    /** MatrixStack 缩放画字。 */
    private static void yongye$drawScaled(DrawContext ctx, TextRenderer tr, Text text,
                                          float x, float y, float scale, int color) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.drawTextWithShadow(tr, text, 0, 0, color);
        ctx.getMatrices().pop();
    }

    // ===== HP 解析 =====

    /** 从血条名的 ‖cur/max 后缀解析血量;解析失败返回 null。 */
    private static double[] yongye$parseHp(ClientBossBar bar) {
        String s = bar.getName().getString();
        int idx = s.indexOf('\u2016'); // ‖
        if (idx < 0) return null;
        String seg = s.substring(idx + 1);
        int idx2 = seg.indexOf('\u2016'); // m304:后面可能还挂着 ‖G 格挡段,先截断
        if (idx2 >= 0) seg = seg.substring(0, idx2);
        String[] parts = seg.split("/", 2);
        if (parts.length < 2) return null;
        try {
            return new double[]{Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 合并组 HP 求和;任意成员缺失 HP 则返回 null。 */
    private static double[] yongye$parseGroupHp(Group g) {
        double sumCur = 0, sumMax = 0;
        for (ClientBossBar b : g.members) {
            double[] hp = yongye$parseHp(b);
            if (hp == null) return null;
            sumCur += hp[0];
            sumMax += hp[1];
        }
        return new double[]{sumCur, sumMax};
    }

    /** 紧凑血量格式(m220 起通道全 double:服务端 %.0f 写入、此处 parseDouble,u64 级血量不再被 int/long 卡住)。 */
    private static String yongye$fmtHp(double n) {
        return com.yongye.client.NumFmt.compact(n);
    }

    /** m304:从血条名的 ‖G当前/上限/破防剩余tick 段解析格挡;无该段或解析失败返回 null。 */
    private static float[] yongye$parseGuard(ClientBossBar bar) {
        String s = bar.getName().getString();
        int idx = s.indexOf("\u2016G");
        if (idx < 0) return null;
        String seg = s.substring(idx + 2);
        int idx2 = seg.indexOf('\u2016');
        if (idx2 >= 0) seg = seg.substring(0, idx2);
        String[] parts = seg.split("/", 3);
        if (parts.length < 3) return null;
        try {
            return new float[]{Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim()),
                    Float.parseFloat(parts[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 从血条名的字符串中剥去 ‖hp 后缀,返回纯名字字符串。 */
    private static String yongye$rawName(ClientBossBar bar) {
        String s = bar.getName().getString();
        int idx = s.indexOf('\u2016');
        return idx >= 0 ? s.substring(0, idx) : s;
    }

    /** 返回剥去 ‖hp 后缀的干净 Text(用于显示)。 */
    private static Text yongye$cleanName(ClientBossBar bar) {
        String raw = yongye$rawName(bar);
        return Text.literal(raw);
    }

    // ===== 样式识别 =====

    /** 识别血条画框;先翻译键,再字面量前后缀(均基于 rawName,不含 ‖ 后缀)。 */
    private static Style yongye$styleOf(ClientBossBar bar) {
        Text name = bar.getName();
        if (name == null) return null;
        if (name.getContent() instanceof TranslatableTextContent t) {
            String key = t.getKey();
            if ("entity.yongye.anubis".equals(key))           return ANUBIS;
            if ("entity.yongye.fire_phoenix".equals(key))     return PHOENIX;
            if ("entity.yongye.red_spider".equals(key))       return SPIDER;
            if ("entity.yongye.death_mage".equals(key))       return MAGE;
            if ("entity.yongye.toro_ender_dragon".equals(key)) return DRAGON;
            if ("entity.minecraft.ender_dragon".equals(key))  return DRAGON;
            return null;
        }
        // 字面量:用 rawName 避免 ‖ 干扰
        String s = yongye$rawName(bar);
        if (s.contains("佩恩"))        return PAIN;
        if (s.endsWith(" BOSS"))       return ZOMBIE;
        if (s.startsWith("【BOSS】"))   return CREEPER;
        return null;
    }

    /** 合并组键。 */
    private static String yongye$groupKey(ClientBossBar bar) {
        Text name = bar.getName();
        if (name.getContent() instanceof TranslatableTextContent t) return t.getKey();
        String s = yongye$rawName(bar);
        if (s.contains("佩恩"))        return "yongye:pain";
        if (s.endsWith(" BOSS"))       return "yongye:zombie_group";
        return "yongye:creeper_group";
    }

    /** 组的牌匾名:单只 = 剥 hp 后的原名;多只 = 「类型名 ×N」。 */
    private static Text yongye$label(Group g) {
        int n = g.members.size();
        String firstName = yongye$rawName(g.members.get(0));
        if (n == 1) return Text.literal(firstName);
        String base;
        if (g.st == CREEPER) base = "BOSS怪";
        else if (g.st == ZOMBIE) base = "玩家BOSS";
        else base = firstName;
        return Text.literal(base + " ×" + n);
    }

    /** 混名组成分标注;使用 rawName 避免 ‖ 混入。 */
    private static String yongye$annotation(Group g) {
        if (g.members.size() < 2 || (g.st != CREEPER && g.st != ZOMBIE)) return null;
        LinkedHashMap<String, Integer> comp = new LinkedHashMap<>();
        for (ClientBossBar b : g.members) {
            String nm = yongye$rawName(b);
            if (nm.startsWith("【BOSS】")) nm = nm.substring("【BOSS】".length());
            if (nm.contains(" BOSS"))    nm = nm.replace(" BOSS", "");
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
}
