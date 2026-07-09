package com.yongye.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * BOSS 血条专属画框(m178):阿努比斯 / 浴火凤凰 的血条替换为用户 GPT 生成的像素风华丽框
 * (胡狼首紫焰石框 + 凤首金翼火焰框),其余血条(法师紫条/BOSS版红条/原版)不受影响。
 *
 * <p><b>原理</b>:拦截 {@code BossBarHud.renderBossBar(DrawContext,int,int,BossBar)}(单条血条的
 * 底槽+填充绘制),按血条名的<b>翻译键</b>识别目标(服务端血条名 = getType().getName(),
 * 序列化到客户端仍是 translatable 组件,键为 entity.yongye.anubis / entity.yongye.fire_phoenix,
 * 与客户端语言无关);命中则:画整框贴图(BOSS 字已抹掉、槽内=熄灭暗色底槽)→ 按 getPercent()
 * 横向裁切画填充条(岩浆/火焰)→ cancel 原版绘制。原版随后在 (y-9) 画的名字文本<b>不拦</b>——
 * 画框的垂直定位让牌匾中心正对名字行,金色实体名正好落在空牌匾上(= 名字牌)。
 *
 * <p><b>贴图</b>(textures/gui/bossbar/,已预缩放到目标 GUI 像素,全程只用仓库 proven 的
 * 9 参 drawTexture(id,x,y,u,v,w,h,texW,texH) 1:1 绘制,规避 11 参缩放签名风险):
 * anubis_frame 262×57(槽偏移 40,30 高 12,牌匾cy 22)、anubis_fill 182×12;
 * phoenix_frame 286×62(槽偏移 52,31 高 15,牌匾cy 25)、phoenix_fill 182×15。
 * 填充槽宽固定 182 GUI 单位 = 与原版血条等长,读血感一致。
 *
 * <p><b>安全</b>:require = 0 —— 若目标方法在本映射名字/签名不符(沙箱编不了,待启动日志验证),
 * mixin 静默不挂、血条回退 m177 玻璃条,不崩游戏。
 *
 * <p><b>已知取舍</b>:进度中途裁切右端为直角(原版同此);两只 BOSS 同屏时原版堆叠步进(19px)
 * 小于画框高度,框体上下会轻微交叠(稀有场景,违和再议加大步进)。
 */
@Mixin(BossBarHud.class)
public abstract class BossBarStyleMixin {

    private static final Identifier ANUBIS_FRAME = Identifier.of("yongye", "textures/gui/bossbar/anubis_frame.png");
    private static final Identifier ANUBIS_FILL = Identifier.of("yongye", "textures/gui/bossbar/anubis_fill.png");
    private static final Identifier PHOENIX_FRAME = Identifier.of("yongye", "textures/gui/bossbar/phoenix_frame.png");
    private static final Identifier PHOENIX_FILL = Identifier.of("yongye", "textures/gui/bossbar/phoenix_fill.png");

    /** 填充槽在屏幕上的 GUI 宽度(= 原版血条 182,读血手感一致)。 */
    private static final int SLOT_W = 182;

    @Inject(method = "renderBossBar(Lnet/minecraft/client/gui/DrawContext;IILnet/minecraft/entity/boss/BossBar;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void yongye$customBar(DrawContext ctx, int x, int y, BossBar bar, CallbackInfo ci) {
        String key = yongye$translationKey(bar.getName());
        if (key == null) return;
        if ("entity.yongye.anubis".equals(key)) {
            // frame 262×57 / 槽(40,30) 高12 / 牌匾cy 22
            yongye$draw(ctx, x, y, bar, ANUBIS_FRAME, ANUBIS_FILL, 262, 57, 40, 30, 12, 22);
            ci.cancel();
        } else if ("entity.yongye.fire_phoenix".equals(key)) {
            // frame 286×62 / 槽(52,31) 高15 / 牌匾cy 25
            yongye$draw(ctx, x, y, bar, PHOENIX_FRAME, PHOENIX_FILL, 286, 62, 52, 31, 15, 25);
            ci.cancel();
        }
    }

    /** 取血条名的翻译键(非 translatable 组件返回 null,不拦)。 */
    private static String yongye$translationKey(Text name) {
        if (name != null && name.getContent() instanceof TranslatableTextContent t) {
            return t.getKey();
        }
        return null;
    }

    /**
     * 画一条:整框 → 按血量裁切的填充。
     * 原版把 x 传成「屏幕中心 - 91」、y 为该条的堆叠顶;画框以中心对齐、
     * 垂直上让牌匾中心对准原版名字行中心(名字画在 y-9、高 9 → 中心 ≈ y-4.5)。
     */
    private static void yongye$draw(DrawContext ctx, int x, int y, BossBar bar,
                                    Identifier frame, Identifier fill,
                                    int fw, int fh, int sx, int sy, int sh, int plaqueCy) {
        int cx = x + 91;
        int fx0 = cx - fw / 2;
        int fy0 = y - 4 - plaqueCy;
        ctx.drawTexture(frame, fx0, fy0, 0, 0, fw, fh, fw, fh);
        float pct = Math.max(0f, Math.min(1f, bar.getPercent()));
        int w = Math.round(SLOT_W * pct);
        if (w > 0) {
            ctx.drawTexture(fill, fx0 + sx, fy0 + sy, 0, 0, w, sh, SLOT_W, sh);
        }
    }
}
