package com.yongye.mixin.client;

import com.yongye.client.ClientStats;
import com.yongye.client.HealthRateTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Locale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD 重写(m94):
 * 1. 血量超阈值时替换原版心形为 RPG 横向血条;血量正常则保持原版。
 * 2. 血条下方画职业资源条(MP)。
 * 3. 整合饥饿/护甲到血条同排(图标+数字),取消原版食物条/护甲条上浮。
 * 4. 整体上移,不再和底部物品栏/原版食物条争位。
 */
@Mixin(InGameHud.class)
public class HudCompactMixin {

    private static final float THRESHOLD = 60.0f;

    private static final int BAR_W = 182;
    private static final int BAR_H = 6;
    private static final int MP_H  = 4;
    private static final int FOOD_H = 6;
    private static final int GUARD_H = 4;   // m278 格挡条(MP 条下方)
    private static final int GAP   = 2;

    private static final Identifier HEART = Identifier.ofVanilla("hud/heart/full");
    private static final Identifier ARMOR = Identifier.ofVanilla("hud/armor_full");
    private static final Identifier FOOD  = Identifier.ofVanilla("hud/food_full");

    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$renderStatusBars(DrawContext ctx, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player == null) return;
        float maxHp = player.getMaxHealth();
        float absHp = player.getAbsorptionAmount();
        if (maxHp + absHp <= THRESHOLD) { ClientStats.guardBarShown = false; return; }  // 低血量交回原版,不接管
        TextRenderer tr = mc.textRenderer;

        // m278 格挡条:持械 / 没回满 / 破防中 才占一行;不相关时不占地方。回写 guardBarShown 供阶段名/箭头连锁上移。
        boolean guardShown = ClientStats.guardMax > 0.5f
                && (ClientStats.guardHolding
                    || ClientStats.guardCur < ClientStats.guardMax - 0.5f
                    || ClientStats.guardBroken > 0);
        ClientStats.guardBarShown = guardShown;

        float curHp = player.getHealth();
        int   armor = player.getArmor();
        int   food  = player.getHungerManager().getFoodLevel();
        float rate  = HealthRateTracker.getRatePerSec();

        // 锚点:整个 HUD 块下移,贴近物品栏上方;m278 有格挡条时整块上移一行,不压进物品栏
        int left = mc.getWindow().getScaledWidth() / 2 - 91;
        int top  = mc.getWindow().getScaledHeight() - 44 - (guardShown ? GAP + GUARD_H : 0);

        // 底衬(m142 方案A 精致玻璃):2px 切角圆角 + 玻璃描边 + 顶亮底暗渐变;配色仍蓝系
        int totalH = BAR_H + GAP + MP_H + GAP + FOOD_H + (guardShown ? GAP + GUARD_H : 0);
        yongye$panel(ctx, left, top - 11, BAR_W, totalH + 13, 0xCC1B5288, 0xCC0C2C50, 0xFF2E7AD0);

        // ===== 等级行(本命职业 Lv.X · 名)在血条正上方 =====
        String cls0 = ClientStats.className;
        if (cls0 != null && !cls0.isEmpty()) {
            int lv = yongye$classLevel(cls0);
            String lvStr = "Lv." + lv + " " + yongye$classCnName(cls0);
            ctx.drawTextWithShadow(tr, Text.literal(lvStr), left, top - 10, 0xFFFFD700);
        }

        // ===== 血条(红色,所有职业统一;m142 渐变+高光+末端光头)=====
        ctx.fill(left, top, left + BAR_W, top + BAR_H, 0xFF3B0000);              // 深红底槽
        ctx.fill(left, top, left + BAR_W, top + 1, 0x60000000);                 // 顶内阴影(凹陷感)
        float totalHp = maxHp + absHp;
        if (absHp > 0.5f) {
            int absEnd = (int)(BAR_W * Math.min(1f, (curHp + absHp) / totalHp));
            ctx.fill(left, top, left + absEnd, top + BAR_H, 0xFF806000);         // 金色吸收
        }
        int hpW = (int)(BAR_W * Math.max(0f, Math.min(1f, curHp / maxHp)));
        if (hpW > 0) {
            yongye$gradV(ctx, left, top, hpW, BAR_H, 0xFFE83030, 0xFF8B0000);    // 红血量渐变(上亮下暗)
            ctx.fill(left, top, left + hpW, top + 1, 0x90FFFFFF);                // 顶高光
            if (hpW >= 2) ctx.fill(left + hpW - 2, top, left + hpW, top + BAR_H, 0xFFFF7070);  // 末端光头
        }

        String hpStr = yongye$num(curHp) + " / " + yongye$num(maxHp)
                + (absHp >= 0.5f ? "  +" + yongye$num(absHp) : "");
        int tw = tr.getWidth(hpStr);
        ctx.drawTextWithShadow(tr, Text.literal(hpStr), left + (BAR_W - tw) / 2, top - 1, 0xFFFFFFFF);

        // 速率(条左)
        if (Math.abs(rate) >= 0.1f) {
            String rs = yongye$rate(rate);
            int col = rate > 0 ? 0xFF55FF55 : 0xFFFF5555;
            ctx.drawTextWithShadow(tr, Text.literal(rs), left - tr.getWidth(rs) - 5, top - 1, col);
        }

        // 血条右侧:护甲(图标+数字)
        int rx = left + BAR_W + 6;
        if (armor > 0) {
            ctx.drawGuiTexture(ARMOR, rx, top - 1, 8, 8);
            String as = String.valueOf(armor);
            ctx.drawTextWithShadow(tr, Text.literal(as), rx + 10, top, 0xFFB0C4FF);
        }

        // ===== 食物条(黄色,血条正下方;m142 渐变+高光+末端光头)=====
        int foodTop = top + BAR_H + GAP;
        int foodW = (int)(BAR_W * Math.max(0f, Math.min(1f, food / 20f)));
        yongye$bar(ctx, left, foodTop, BAR_W, FOOD_H, 0xFF332600, foodW, 0xFFF2D84E, 0xFFB89A1E, 0xFFFFF0A0);
        ctx.drawGuiTexture(FOOD, left + BAR_W + 6, foodTop - 1, 8, 8);
        ctx.drawTextWithShadow(tr, Text.literal(food + "/20"), left + BAR_W + 16, foodTop, 0xFFE6C42A);

        // ===== MP/资源 条(食物条下方) =====
        yongye$renderMpBar(ctx, tr, left, top + BAR_H + GAP + FOOD_H + GAP);

        // ===== 格挡条(m278,MP 条下方;青蓝渐变,低于30%转橙,破防整条红闪+倒计时) =====
        if (guardShown) {
            yongye$renderGuardBar(ctx, tr, left, top + BAR_H + GAP + FOOD_H + GAP + MP_H + GAP);
        }

        ci.cancel();
    }

    /** 0xAARRGGBB 颜色线性插值(含 alpha)。 */
    private static int yongye$lerp(int c1, int c2, float t) {
        int a1 = (c1 >>> 24) & 255, r1 = (c1 >> 16) & 255, g1 = (c1 >> 8) & 255, b1 = c1 & 255;
        int a2 = (c2 >>> 24) & 255, r2 = (c2 >> 16) & 255, g2 = (c2 >> 8) & 255, b2 = c2 & 255;
        int a = (int)(a1 + (a2 - a1) * t), r = (int)(r1 + (r2 - r1) * t);
        int g = (int)(g1 + (g2 - g1) * t), b = (int)(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 垂直渐变填充(逐行 fill;top→bot)。 */
    private static void yongye$gradV(DrawContext ctx, int x, int y, int w, int h, int top, int bot) {
        if (w <= 0 || h <= 0) return;
        for (int i = 0; i < h; i++) {
            float t = h > 1 ? (float) i / (h - 1) : 0f;
            ctx.fill(x, y + i, x + w, y + i + 1, yongye$lerp(top, bot, t));
        }
    }

    /** 精致玻璃底衬:2px 切角 + 顶亮底暗渐变 + 玻璃描边 + 顶部内高光。 */
    private static void yongye$panel(DrawContext ctx, int x, int y, int w, int h, int top, int bot, int edge) {
        if (w <= 4 || h <= 4) return;
        yongye$gradV(ctx, x, y + 2, w, h - 4, top, bot);          // 主体(满宽渐变)
        ctx.fill(x + 2, y, x + w - 2, y + 2, top);                // 顶 2 行(切角缩进)
        ctx.fill(x + 2, y + h - 2, x + w - 2, y + h, bot);        // 底 2 行(切角缩进)
        ctx.fill(x + 2, y, x + w - 2, y + 1, edge);               // 上描边
        ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, edge);       // 下描边
        ctx.fill(x, y + 2, x + 1, y + h - 2, edge);               // 左描边
        ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, edge);       // 右描边
        ctx.fill(x + 3, y + 2, x + w - 3, y + 3, 0x40FFFFFF);      // 顶部内高光
    }

    /** 一根条:底槽 + 顶内阴影 + 渐变填充 + 顶高光 + 末端光头。 */
    private static void yongye$bar(DrawContext ctx, int x, int y, int w, int h, int bg,
                                   int fillW, int fTop, int fBot, int head) {
        ctx.fill(x, y, x + w, y + h, bg);                         // 底槽
        ctx.fill(x, y, x + w, y + 1, 0x60000000);                 // 顶内阴影
        int fw = Math.max(0, Math.min(fillW, w));
        if (fw > 0) {
            yongye$gradV(ctx, x, y, fw, h, fTop, fBot);           // 渐变填充
            ctx.fill(x, y, x + fw, y + 1, 0x90FFFFFF);            // 顶高光
            if (fw >= 2) ctx.fill(x + fw - 2, y, x + fw, y + h, head);  // 末端光头
        }
    }

    /** m278 格挡条:和血条同一套质感(渐变+高光+末端光头)。破防=整条红色呼吸闪烁+左侧倒计时;余量<30%=橙色预警。
     *  m283:标签/倒计时改画在条**左侧**(面板外)——右侧上下两行「坚守」「格挡」只隔 6px,CJK 字高 9px 必然叠字(实机截图证实)。 */
    private static void yongye$renderGuardBar(DrawContext ctx, TextRenderer tr, int left, int top) {
        int broken = ClientStats.guardBroken;
        if (broken > 0) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 120.0);  // 呼吸闪烁
            int a = 0x70 + (int) (0x8F * pulse);
            ctx.fill(left, top, left + BAR_W, top + GUARD_H, (a << 24) | 0xC01818);
            ctx.fill(left, top, left + BAR_W, top + 1, 0x60000000);
            String s = "破防 " + (int) Math.ceil(broken / 20.0) + "s";
            ctx.drawTextWithShadow(tr, Text.literal(s), left - tr.getWidth(s) - 5, top - 2, 0xFFFF5555);
            return;
        }
        float max = Math.max(1f, ClientStats.guardMax);
        float frac = Math.max(0f, Math.min(1f, ClientStats.guardCur / max));
        boolean low = frac < 0.30f;
        int fillW = (int) (BAR_W * frac);
        if (low) yongye$bar(ctx, left, top, BAR_W, GUARD_H, 0xFF2A1804, fillW, 0xFFFFB040, 0xFFB06810, 0xFFFFE0A0);
        else     yongye$bar(ctx, left, top, BAR_W, GUARD_H, 0xFF06242E, fillW, 0xFF3CD9E8, 0xFF157F9E, 0xFFA8F4FF);
        String lb = "格挡";
        ctx.drawTextWithShadow(tr, Text.literal(lb), left - tr.getWidth(lb) - 5, top - 2, low ? 0xFFFFB040 : 0xFF7FDCEC);
    }

    private static void yongye$renderMpBar(DrawContext ctx, TextRenderer tr, int left, int top) {
        float mp = ClientStats.mp;
        String cls = ClientStats.className;
        if (cls == null || cls.isEmpty()) return;
        int[] colors = yongye$mpColors(cls);
        if (colors == null) return;

        int fillW = (int)(BAR_W * Math.max(0f, Math.min(1f, mp)));
        // m142:渐变+高光+末端光头(colors[0]底槽 / [1]填充主色 / [2]高光&光头)
        yongye$bar(ctx, left, top, BAR_W, MP_H, colors[0],
                   fillW, yongye$lerp(colors[1], colors[2], 0.35f), colors[1], colors[2]);
        String label = yongye$mpLabel(cls);
        if (!label.isEmpty()) {
            ctx.drawTextWithShadow(tr, Text.literal(label), left + BAR_W + 6, top - 2, 0xFFAAAAAA);
        }
    }

    private static int[] yongye$mpColors(String cls) {
        // m139:所有职业统一蓝色资源条(底 / 填充 / 高光)
        return switch (cls) {
            case "warlock", "assassin", "warrior", "swordsman", "tank", "monk"
                    -> new int[]{0xFF0A1E38, 0xFF2E7AD0, 0xFF7FCFFF};
            default -> null;
        };
    }

    private static String yongye$mpLabel(String cls) {
        return switch (cls) {
            case "warlock"   -> "灵力";
            case "assassin"  -> "暗能";
            case "warrior"   -> "怒气";
            case "swordsman" -> "剑气";
            case "tank"      -> "坚守";
            case "monk"      -> "拳意";
            default          -> "";
        };
    }

    /** 职业 id → 中文名(HUD 等级行用)。 */
    private static String yongye$classCnName(String cls) {
        return switch (cls) {
            case "tank"      -> "肉盾";
            case "warrior"   -> "战士";
            case "warlock"   -> "术士";
            case "swordsman" -> "剑客";
            case "monk"      -> "武僧";
            case "assassin"  -> "刺客";
            default          -> "";
        };
    }

    /** m355 口径修复:此前按索引取 ClientStats.levels——但该数组由 sendStats 填的是**技能书各类型累计等级**
     *  (SkillType 序:攻击/护甲/恢复…),「Lv.10 肉盾」实际显示的是攻击书等级,与职业无关(作者:「我 571 级
     *  职业却只有 11 级」实锤)。项目没有独立职业经验系统,统一改显**技能总级**(血量书累计+全技能书累计),
     *  与任务书图鉴「技能总级 VN」完全同口径,面板/图鉴数字从此对得上。 */
    private static int yongye$classLevel(String cls) {
        long sum = com.yongye.client.ClientStats.health;   // 血量书累计(sendStats 的 health 字段)
        for (int v : ClientStats.levels) sum += v;         // 各类型技能书累计
        return (int) Math.min(Integer.MAX_VALUE, sum);
    }

    /** 数字紧凑显示(m219 起统一走 NumFmt:K/M/B/T,十亿以上不再堆成一长串 M)。 */
    private static String yongye$num(float v) {
        return com.yongye.client.NumFmt.compact(v);
    }

    private static String yongye$rate(float r) {
        float a = Math.abs(r);
        String n = (a < 10f) ? String.format(Locale.ROOT, "%.1f", a) : String.valueOf(Math.round(a));
        return (r > 0 ? "+" : "-") + n + "/s";
    }

    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private static void yongye$cancelArmor(DrawContext ctx, PlayerEntity player,
                                           int a, int b, int c, int x, CallbackInfo ci) {
        float total = player.getMaxHealth() + player.getAbsorptionAmount();
        if (total > THRESHOLD) ci.cancel();
    }

    /** m283:action bar(处决!/完美格挡!/蓄力条等 overlay 消息)原版画在 h-68,正好压在永夜阶段名(h-66)
     *  和加高后的面板顶上(实机截图证实重叠)。面板接管期整体上抬 18px 到 h-86,落进阶段名与核心箭头之间的空档。
     *  push/pop 成对包住整个方法,矩阵不外漏。
     *  【待编译验证】renderOverlayMessage 方法名+RenderTickCounter 形参(yarn 1.21.1 官方 mapping method_55800/
     *  class_9779=net.minecraft.client.render.RenderTickCounter 已核,仓库首用);require=0,失败=不抬、只回截图那种重叠,不崩。 */
    @Inject(method = "renderOverlayMessage", at = @At("HEAD"), require = 0)
    private void yongye$liftActionBarPush(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter, CallbackInfo ci) {
        PlayerEntity player = MinecraftClient.getInstance().player;
        yongye$actionBarLifted = player != null
                && player.getMaxHealth() + player.getAbsorptionAmount() > THRESHOLD;
        if (yongye$actionBarLifted) {
            ctx.getMatrices().push();
            ctx.getMatrices().translate(0, -18, 0);
        }
    }

    @Inject(method = "renderOverlayMessage", at = @At("RETURN"), require = 0)
    private void yongye$liftActionBarPop(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter, CallbackInfo ci) {
        if (yongye$actionBarLifted) {
            ctx.getMatrices().pop();
            yongye$actionBarLifted = false;
        }
    }

    private static boolean yongye$actionBarLifted = false;

    /**
     * 取消原版食物条(已整合到血条右侧)。
     * 【待编译验证】1.21.1 InGameHud 食物条方法名:可能是 renderFood / renderHungerBar。
     * 若 build 报"找不到方法 renderFood",改成本地 yarn 实际名(用 mc.player 反查或看 InGameHud)。
     */
    @Inject(method = "renderFood", at = @At("HEAD"), cancellable = true)
    private void yongye$cancelFood(DrawContext ctx, PlayerEntity player, int x, int y, CallbackInfo ci) {
        float total = player.getMaxHealth() + player.getAbsorptionAmount();
        if (total > THRESHOLD) ci.cancel();
    }
}
