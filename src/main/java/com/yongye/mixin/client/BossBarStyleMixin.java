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

/**
 * BOSS 血条画框 + 自适应布局(m181,重写 m179/m180 贴图管线):
 * <ul>
 *   <li><b>高清贴图 + 缩放绘制</b>(修「压缩太狠看不清」):m179 把贴图预压到 GUI 逻辑像素
 *       (大档框宽仅 ~262px),实机 GUI scale 2~4 会再放大 2~4 倍必然糊。现在贴图保留
 *       槽宽 728px(= 大档 182 的 4 倍,GUI scale 4 下 1:1 原生),绘制改用 11 参缩放版
 *       {@code drawTexture(id,x,y,w,h,u,v,regionW,regionH,texW,texH)}(yarn 1.21.1 官方
 *       mapping method_25293 已核),配合每张贴图旁的 .mcmeta {"texture":{"blur":true,
 *       "clamp":true}}(原版 mojangstudios.png.mcmeta 同款机制)开双线性过滤,缩小平滑
 *       不锯齿。三档共用同一套贴图,按档位算缩放因子——42 张预压贴图换成 8 boss × 3 张。</li>
 *   <li><b>三层绘制</b>(顺应用户新素材「血条/框分离」的分层关系):空槽底(压暗血条,
 *       掉血露出熄灭槽)→ 血条(按百分比横向裁)→ 框压顶(蛛网/中央宝石等装饰盖在血条上,
 *       m178~m180 是框在底、装饰会被血条盖住)。</li>
 *   <li><b>专属画框</b>:阿努比斯 / 浴火凤凰 / 末影龙(自建龙 + 原版末地龙战)/ 红蜘蛛 /
 *       死亡法师 / 长门·佩恩(无牌匾,名字悬浮框顶)/ 玩家皮肤僵尸BOSS(「xx BOSS」后缀,
 *       m145 那套,本体是僵尸 → 新僵尸框)/ 其余怪物BOSS版(「【BOSS】」前缀 → 苦力怕框)。
 *       其余血条(任务计时/原版凋灵…)原版逻辑照画。</li>
 *   <li><b>真实高度堆叠 + 怪多自动缩小</b>(承 m179):画框按实际高度推进游标留 11px 缝,
 *       首条自动下压防顶裁;画框条数 ≤2 大档(槽宽182)/ 3~4 中档(136)/ ≥5 小档(100)。</li>
 * </ul>
 *
 * <p><b>安全</b>:render 注入 require=0 —— 不挂则整套回退原版血条不崩;
 * BossBarHudAccess 的 @Accessor/@Invoker 名字错误会在本地 build 期被 mixin AP 报出。
 * 无画框条存在时提前 return 走原版,零额外开销。
 */
@Mixin(BossBarHud.class)
public abstract class BossBarStyleMixin {

    /** 三档血条槽的屏幕(GUI 逻辑)宽度:大 = 原版血条等长。 */
    private static final int[] SLOT_W = {182, 136, 100};

    /**
     * 画框几何,全部是<b>贴图像素</b>(生成脚本输出,与 PNG 一一对应):
     * 框宽高 / 槽偏移 xy / 槽宽高 / 牌匾中心 y(-1 = 无牌匾,名字悬浮框顶上方)。
     * 屏幕尺寸 = 贴图像素 × (SLOT_W[档位] / sw)。
     */
    private record Style(Identifier frame, Identifier back, Identifier fill,
                         int fw, int fh, int sx, int sy, int sw, int sh, int pcy) {}

    private static Style yongye$style(String key, int fw, int fh, int sx, int sy, int sw, int sh, int pcy) {
        String base = "textures/gui/bossbar/" + key;
        return new Style(
                Identifier.of("yongye", base + "_frame.png"),
                Identifier.of("yongye", base + "_back.png"),
                Identifier.of("yongye", base + "_fill.png"),
                fw, fh, sx, sy, sw, sh, pcy);
    }

    // 几何常量由贴图生成脚本输出(见 DEVLOG m181),与 PNG 像素一一对应
    private static final Style ANUBIS  = yongye$style("anubis",  1055, 229, 163, 132, 728, 51, 85);
    private static final Style PHOENIX = yongye$style("phoenix", 1102, 241, 188,  97, 728, 86, 89);
    private static final Style DRAGON  = yongye$style("dragon",  1154, 333, 214, 173, 728, 56, 105);
    private static final Style SPIDER  = yongye$style("spider",  1245, 568, 258, 194, 728, 54, 102);
    private static final Style MAGE    = yongye$style("mage",    1084, 222, 179, 102, 728, 54, 77);
    private static final Style PAIN    = yongye$style("pain",    1131, 345, 217, 147, 728, 91, -1);
    private static final Style CREEPER = yongye$style("creeper", 1112, 252, 192,  74, 728, 89, 61);
    private static final Style ZOMBIE  = yongye$style("zombie",  1166, 389, 215, 173, 728, 55, 112);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$layout(DrawContext ctx, CallbackInfo ci) {
        BossBarHudAccess acc = (BossBarHudAccess) this;
        var bars = acc.yongye$getBossBars();
        if (bars.isEmpty()) return;

        // 数画框条;一根都没有 → 交回原版(零开销路径)
        int customCount = 0;
        for (ClientBossBar bar : bars.values()) {
            if (yongye$styleOf(bar) != null) customCount++;
        }
        if (customCount == 0) return;
        ci.cancel();

        // 尺寸档:≤2 大 / 3~4 中 / ≥5 小
        int tier = customCount <= 2 ? 0 : (customCount <= 4 ? 1 : 2);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int cx = ctx.getScaledWindowWidth() / 2;
        int j = 12; // 游标语义与原版一致:名字画在 j-9、原版条画在 j

        for (ClientBossBar bar : bars.values()) {
            Style st = yongye$styleOf(bar);
            Text name = bar.getName();
            int tw = tr.getWidth(name);
            if (st != null) {
                float s = SLOT_W[tier] / (float) st.sw;          // 贴图像素 → 屏幕像素
                int fw = Math.round(st.fw * s), fh = Math.round(st.fh * s);
                int ox = Math.round(st.sx * s), oy = Math.round(st.sy * s);
                int slotW = SLOT_W[tier], slotH = Math.max(1, Math.round(st.sh * s));
                int pc = st.pcy < 0 ? -4 : Math.round(st.pcy * s); // -4 = 名字悬浮框顶(佩恩)

                j = Math.max(j, pc + 6);                          // 首条防顶部裁切
                int fx0 = cx - fw / 2;
                int fy0 = j - 4 - pc;                             // 牌匾中心对准名字行中心(≈ j-4.5)

                // ① 空槽底(压暗血条,掉血露出熄灭槽)
                ctx.drawTexture(st.back, fx0 + ox, fy0 + oy, slotW, slotH,
                        0f, 0f, st.sw, st.sh, st.sw, st.sh);
                // ② 血条按百分比横向裁(贴图区域与屏幕宽同比例缩,不变形)
                float pct = Math.max(0f, Math.min(1f, bar.getPercent()));
                int w = Math.round(slotW * pct);
                int rw = Math.round(st.sw * pct);
                if (w > 0 && rw > 0) {
                    ctx.drawTexture(st.fill, fx0 + ox, fy0 + oy, w, slotH,
                            0f, 0f, rw, st.sh, st.sw, st.sh);
                }
                // ③ 框压顶(装饰盖在血条上)
                ctx.drawTexture(st.frame, fx0, fy0, fw, fh,
                        0f, 0f, st.fw, st.fh, st.fw, st.fh);

                ctx.drawTextWithShadow(tr, name, cx - tw / 2, j - 9, 0xFFFFFF);
                j = fy0 + fh + 11;                                // 按真实框高推进(防重叠)
            } else {
                acc.yongye$renderVanillaBar(ctx, cx - 91, j, bar);
                ctx.drawTextWithShadow(tr, name, cx - tw / 2, j - 9, 0xFFFFFF);
                j += 10 + tr.fontHeight;                          // 原版步进
            }
            if (j >= ctx.getScaledWindowHeight() / 2) break;      // 上限半屏(原版1/3)
        }
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
}
