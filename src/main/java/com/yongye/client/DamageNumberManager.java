package com.yongye.client;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 伤害飘字(m373,3A 打磨路线图第 1 项):收到 DamageNumberPayload 后在世界内怪物身上
 * 弹出漂浮伤害数字——弹出过冲(前 140ms 从 0.4 冲到 1.35 再 90ms 回落 1.0)→ ease-out
 * 上浮约 1 格 → 末 260ms 淡出。普通命中=暖白小字,重击=金色大字(基准尺寸 ×1.45)。
 *
 * <p>实现取舍:
 * <ul>
 *   <li>渲染走 {@code WorldRenderEvents.AFTER_TRANSLUCENT}(与 MagicCircleFxManager/SlashFxManager
 *       同挂点),广告牌朝向 = 相机四元数({@code ctx.camera().getRotation()},yarn 1.21.1 映射已核
 *       method_23767 返回 org.joml.Quaternionf);矩阵手工构造 Matrix4f(translation→rotate→scale,
 *       scale 取 (-s,-s,s) 与原版名牌同约定),不碰 MatrixStack 少赌一个 API 面。</li>
 *   <li>文本绘制用 {@code TextRenderer.draw(String,float,float,int,boolean,Matrix4f,
 *       VertexConsumerProvider,TextLayerType,int,int)}(yarn 1.21.1 映射已核 method_27521 逐参对上);
 *       TextLayerType.NORMAL 常量名为仓库首用,列「待编译验证」(1.17 起该枚举名未变,极低险,
 *       若报错换 POLYGON_OFFSET 或 SEE_THROUGH 同枚举任一常量即可)。</li>
 *   <li>淡出用颜色 alpha 高字节;MC 文本渲染对 alpha&lt;0x04 会强制不透明(tweakTransparency),
 *       故最低钳 8,寿命一到直接移除。颜色恒带满/显式 alpha(m213 染色铁律)。</li>
 *   <li>数字口径:≥10 取整走 NumFmt.compact(全模组数字口径一致),&lt;10 保一位小数
 *       (前期 2.5 点伤害取整成 2 观感说谎)。</li>
 *   <li>同屏上限 {@link #MAX} 条(超出挤掉最旧),48 格外只老化不渲染;高频 AOE 的量闸在
 *       服务端 CombatFxHandler(每玩家每 tick 限发),两头兜。</li>
 * </ul>
 */
public final class DamageNumberManager {
    private DamageNumberManager() {}

    /** 同屏最多同时存活的飘字数(超出移除最旧,防 AOE 清屏时铺满)。 */
    private static final int MAX = 60;
    private static final long LIFE_MS = 900, POP_MS = 140, POP_BACK_MS = 90, FADE_MS = 260;
    /** 渲染距离平方(48 格外不画,仍老化)。 */
    private static final double MAX_DIST_SQ = 48 * 48;

    /** 一条飘字:出生点 + 水平漂移向量(随机散布,防同一怪身上数字叠死)+ 文本 + 类别 + 出生时刻
     *  + 原始数值与目标 id(m406 合并窗口用:同目标短窗内新伤并进旧条,记录不可变=原位整条替换)。 */
    private record Num(double x, double y, double z, double dx, double dz,
                       String text, int kind, long bornNanos, float amount, int targetId) {}

    /** m406 同目标合并窗口:出生后此毫秒数内,同 targetId 的新伤并入本条(数值累加/档位取高/文本重排),
     *  超窗才另起新条——AOE 连跳与暴击尾刀不再在同一只怪身上叠一摞小字。 */
    private static final long MERGE_MS = 350;

    private static final List<Num> NUMS = new ArrayList<>();
    private static final Random RAND = new Random();

    /** 客户端初始化时挂世界渲染事件(YongyeClient 调)。 */
    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(DamageNumberManager::render);
    }

    /** 收包入口(主线程)。 */
    public static void onNumber(double x, double y, double z, float amount, int kind, int targetId) {
        if (amount <= 0 || !FxBudget.on()) return; // m381 预算闸
        long now = System.nanoTime();
        // m406 合并窗口:同目标 350ms 内并入旧条(数值累加/档位取高),关配置=回逐条旧观感
        if (YongyeConfig.get().enableDamageNumberMerge && targetId != 0) {
            for (int i = NUMS.size() - 1; i >= 0; i--) {
                Num n = NUMS.get(i);
                if (n.targetId != targetId) continue;
                if ((now - n.bornNanos) / 1_000_000L > MERGE_MS) break;   // 同目标最新一条已超窗=另起
                float sum = n.amount + amount;
                NUMS.set(i, new Num(n.x, n.y, n.z, n.dx, n.dz,
                        fmt(sum), Math.max(n.kind, kind), n.bornNanos, sum, targetId));
                return;
            }
        }
        while (NUMS.size() >= Math.max(12, FxBudget.scaleCount(MAX))) NUMS.remove(0); // m381 缩上限(保底 12)
        // 随机散布:水平 ±0.45 格圆盘内一点,连击时数字错开不叠字
        double ang = RAND.nextDouble() * Math.PI * 2;
        double r = 0.15 + RAND.nextDouble() * 0.30;
        NUMS.add(new Num(x, y, z, Math.cos(ang) * r, Math.sin(ang) * r,
                fmt(amount), kind, now, amount, targetId));
    }

    /** 数字口径与 m373 相同:≥10 取整 compact,<10 保一位小数(纯格式,不掺语义缀字——m379 评审红线)。 */
    private static String fmt(float amount) {
        return amount >= 10f
                ? NumFmt.compact(Math.round(amount))
                : NumFmt.compact(Math.round(amount * 10f) / 10.0);
    }

    private static void render(WorldRenderContext ctx) {
        if (NUMS.isEmpty()) return;
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableDamageNumbers) { NUMS.clear(); return; }
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        TextRenderer tr = mc.textRenderer;
        Vec3d cam = ctx.camera().getPos();
        Quaternionf rot = ctx.camera().getRotation();
        float cfgScale = (float) Math.max(0.3, Math.min(3.0, c.damageNumberScale));
        long now = System.nanoTime();

        Iterator<Num> it = NUMS.iterator();
        while (it.hasNext()) {
            Num n = it.next();
            long ageMs = (now - n.bornNanos) / 1_000_000L;
            long life = FxBudget.scaleLife(LIFE_MS); // m381 LOW 档短寿
            if (ageMs >= life) { it.remove(); continue; }

            // 位置:ease-out 上浮 1.0 格 + 同曲线水平漂移
            double t = ageMs / (double) life;
            double rise = 1.0 - Math.pow(1.0 - t, 3);
            double px = n.x + n.dx * rise, py = n.y + 1.0 * rise, pz = n.z + n.dz * rise;
            double rx = px - cam.x, ry = py - cam.y, rz = pz - cam.z;
            if (rx * rx + ry * ry + rz * rz > MAX_DIST_SQ) continue;

            // 弹出过冲:0.4 → 1.35 → 1.0
            float pop;
            if (ageMs < POP_MS) pop = 0.4f + (ageMs / (float) POP_MS) * 0.95f;
            else if (ageMs < POP_MS + POP_BACK_MS) pop = 1.35f - ((ageMs - POP_MS) / (float) POP_BACK_MS) * 0.35f;
            else pop = 1.0f;

            // 基准字号:普通 0.022(略小于原版名牌 0.025 不喧宾);重击 ×1.45 金;
            // m406 暴击 ×critScale(默认1.6,数值微调页可拖)橙红;处决 ×1.9 暗红——语义档只在渲染层定观感
            float tierMul = switch (n.kind) {
                case DamageKind.HEAVY -> 1.45f;
                case DamageKind.CRITICAL -> (float) Math.max(1.0, Math.min(3.0, c.damageNumberCritScale));
                case DamageKind.EXECUTION -> 1.9f;
                default -> 1.0f;
            };
            float s = 0.022f * tierMul * cfgScale * pop;

            // 末段淡出(alpha 钳 [8,255]:MC 对 alpha<0x04 强制不透明)
            int alpha = 255;
            long fadeStart = life - FADE_MS;
            if (ageMs > fadeStart) alpha = (int) (255 * (1.0 - (ageMs - fadeStart) / (double) FADE_MS));
            alpha = Math.max(8, Math.min(255, alpha));
            int rgb = switch (n.kind) {
                case DamageKind.HEAVY -> 0xFFB428;      // 重击=金
                case DamageKind.CRITICAL -> 0xFF6238;   // 暴击=橙红
                case DamageKind.EXECUTION -> 0xD42B3A;  // 处决=暗红
                default -> 0xFFF2E8;                    // 普通=暖白
            };
            int argb = (alpha << 24) | rgb;
            // 缀字在渲染层拼(「暴」「斩」不进数字格式层,m379 评审红线;BMP 内字符)
            String shown = switch (n.kind) {
                case DamageKind.CRITICAL -> n.text + " 暴";
                case DamageKind.EXECUTION -> n.text + " 斩";
                default -> n.text;
            };

            Matrix4f m = new Matrix4f()
                    .translation((float) rx, (float) ry, (float) rz)
                    .rotate(rot)
                    .scale(-s, -s, s);
            float w = tr.getWidth(shown);
            tr.draw(shown, -w / 2f, -4f, argb, true, m, consumers,
                    TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        }
    }

    /** 与 DamageNumberPayload 的 kind 常量同口径(避免客户端类直接依赖 network 包常量名散落)。 */
    private static final class DamageKind {
        static final int HEAVY = 1, CRITICAL = 2, EXECUTION = 3;   // 与 DamageNumberPayload 同口径(m406)
    }
}
