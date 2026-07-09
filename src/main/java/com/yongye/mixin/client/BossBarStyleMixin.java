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
 * BOSS 血条画框 + 自适应布局(m179,重写 m178):
 * 接管 {@code BossBarHud.render(DrawContext)} 整个血条堆叠——
 * <ul>
 *   <li><b>专属画框</b>:阿努比斯(胡狼紫焰)/ 浴火凤凰(凤翼金焰)/ 末影龙(双龙首魔能,
 *       同时覆盖自建龙与原版末地龙战)/ 怪物BOSS版(苦力怕酸浊框,按「【BOSS】」前缀 /
 *       「 BOSS」后缀的字面量名匹配)。其余血条(法师/任务计时/原版凋灵…)原版逻辑照画。</li>
 *   <li><b>真实高度堆叠</b>(修 m178 重叠):画框按各自实际高度推进游标,框间留 11px;
 *       首条画框自动下压防止顶部被屏幕裁切(m178 实机截图两大坑一次修掉)。</li>
 *   <li><b>怪多自动缩小</b>:按当前画框条数选尺寸档——≤2 大档(槽宽182=原版等长)、
 *       3~4 中档(136)、≥5 小档(100);三档贴图均预缩放到 GUI 像素,
 *       全程只用仓库 proven 的 9 参 drawTexture 1:1 绘制。</li>
 * </ul>
 *
 * <p><b>识别</b>:优先按血条名翻译键(entity.yongye.anubis / fire_phoenix / toro_ender_dragon /
 * entity.minecraft.ender_dragon —— 原版龙战血条名即龙实体名),再按字面量前后缀兜底(怪物BOSS版)。
 * 名字文本不拦、由本 mixin 照原版画在牌匾中心(画框牌匾都已是空牌/抹字)。
 *
 * <p><b>安全</b>:render 注入 require=0 —— 不挂则整套回退原版血条不崩;
 * BossBarHudAccess 的 @Accessor/@Invoker 名字错误会在本地 build 期被 mixin AP 报出。
 * 无画框条存在时提前 return 走原版,零额外开销。
 */
@Mixin(BossBarHud.class)
public abstract class BossBarStyleMixin {

    /** 画框几何(全 GUI 像素):框宽高 / 槽偏移 xy / 槽高 / 槽宽 / 牌匾中心 y。 */
    private record Geo(Identifier frame, Identifier fill,
                       int fw, int fh, int sx, int sy, int sh, int slotW, int pcy) {}

    private static Geo[] yongye$tiers(String boss, int[][] v) {
        Geo[] out = new Geo[3];
        String[] t = {"l", "m", "s"};
        for (int i = 0; i < 3; i++) {
            out[i] = new Geo(
                    Identifier.of("yongye", "textures/gui/bossbar/" + boss + "_frame_" + t[i] + ".png"),
                    Identifier.of("yongye", "textures/gui/bossbar/" + boss + "_fill_" + t[i] + ".png"),
                    v[i][0], v[i][1], v[i][2], v[i][3], v[i][4], v[i][5], v[i][6]);
        }
        return out;
    }

    // 生成脚本输出的几何常量(fw,fh,sx,sy,sh,slotW,pcy),与贴图像素一一对应
    private static final Geo[] ANUBIS = yongye$tiers("anubis",
            new int[][]{{262, 57, 40, 30, 12, 182, 22}, {196, 43, 30, 22, 9, 136, 17}, {144, 31, 22, 17, 7, 100, 12}});
    private static final Geo[] PHOENIX = yongye$tiers("phoenix",
            new int[][]{{286, 62, 52, 31, 15, 182, 25}, {214, 46, 39, 23, 11, 136, 18}, {157, 34, 29, 17, 8, 100, 14}});
    private static final Geo[] DRAGON = yongye$tiers("dragon",
            new int[][]{{327, 93, 73, 47, 17, 182, 29}, {244, 69, 54, 35, 12, 136, 22}, {180, 51, 40, 26, 9, 100, 16}});
    private static final Geo[] CREEPER = yongye$tiers("creeper",
            new int[][]{{277, 63, 44, 24, 15, 182, 23}, {207, 47, 33, 18, 11, 136, 17}, {152, 34, 24, 13, 8, 100, 12}});

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
        int screenW = ctx.getScaledWindowWidth();
        int cx = screenW / 2;
        int j = 12; // 游标语义与原版一致:名字画在 j-9、原版条画在 j

        for (ClientBossBar bar : bars.values()) {
            Geo[] style = yongye$styleOf(bar);
            Text name = bar.getName();
            int tw = tr.getWidth(name);
            if (style != null) {
                Geo g = style[tier];
                j = Math.max(j, g.pcy + 6);                 // 首条防顶部裁切
                int fx0 = cx - g.fw / 2;
                int fy0 = j - 4 - g.pcy;                    // 牌匾中心对准名字行中心(≈ j-4.5)
                ctx.drawTexture(g.frame, fx0, fy0, 0, 0, g.fw, g.fh, g.fw, g.fh);
                float pct = Math.max(0f, Math.min(1f, bar.getPercent()));
                int w = Math.round(g.slotW * pct);
                if (w > 0) {
                    ctx.drawTexture(g.fill, fx0 + g.sx, fy0 + g.sy, 0, 0, w, g.sh, g.slotW, g.sh);
                }
                ctx.drawTextWithShadow(tr, name, cx - tw / 2, j - 9, 0xFFFFFF);
                j = fy0 + g.fh + 11;                        // 按真实框高推进(修重叠)
            } else {
                acc.yongye$renderVanillaBar(ctx, cx - 91, j, bar);
                ctx.drawTextWithShadow(tr, name, cx - tw / 2, j - 9, 0xFFFFFF);
                j += 10 + tr.fontHeight;                    // 原版步进
            }
            if (j >= ctx.getScaledWindowHeight() / 2) break; // 画框高,上限放宽到半屏(原版1/3)
        }
    }

    /** 识别血条画框:先翻译键(语言无关),再字面量前后缀兜底(怪物BOSS版)。 */
    private static Geo[] yongye$styleOf(ClientBossBar bar) {
        Text name = bar.getName();
        if (name == null) return null;
        if (name.getContent() instanceof TranslatableTextContent t) {
            String key = t.getKey();
            if ("entity.yongye.anubis".equals(key)) return ANUBIS;
            if ("entity.yongye.fire_phoenix".equals(key)) return PHOENIX;
            if ("entity.yongye.toro_ender_dragon".equals(key)) return DRAGON;
            if ("entity.minecraft.ender_dragon".equals(key)) return DRAGON; // 原版末地龙战
            return null;
        }
        String s = name.getString();
        if (s.startsWith("【BOSS】") || s.endsWith(" BOSS")) return CREEPER;  // 怪物BOSS版(字面量名)
        return null;
    }
}
