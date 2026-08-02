package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 疾跑残影(m433,「大工程点单区」第一项落地)——疾跑时身后拖出几层渐隐发光残影。
 *
 * <p><b>为什么原本被列为大工程、现在为什么不是</b>:当初设想的是「逐帧快照骨骼姿态再回放」
 * ——那要把 ModelPart 的整棵旋转树按帧存下来重放,既费内存又要碰渲染状态机。本实现改用
 * 业界通行的取巧法:<b>用当前姿态、在身后不同距离各画一份半透明副本</b>。疾跑时肢体摆动频率高、
 * 残影只存活一瞬,肉眼读到的是「速度拖影」而不是「姿态回放」,观感等价而复杂度是另一个量级。
 *
 * <p><b>全部走在树已 CI 编过的写法,零新 API 面:</b>
 * <ul>
 *   <li>叠渲一层模型 = {@code getContextModel().render(matrices, vc, light, overlay, ARGB)}
 *       ——与 {@link EliteSkinFeatureRenderer} 逐字同款(5 参打包 ARGB 版);</li>
 *   <li>发光半透明层 = {@code RenderLayer.getEntityTranslucentEmissive}(m246 魔法阵 / m385 血条同款);</li>
 *   <li>纯白 4×4 贴图 {@code textures/fx/white.png}(m385 在用)当底,靠顶点 ARGB 染成残影色
 *       ——<b>刻意不取玩家皮肤</b>:皮肤 API(getSkinTextures)是仓库首用面,而剪影式残影本来就比
 *       贴皮肤的重影更干净、也不会在别人皮肤上糊一层;</li>
 *   <li>身后方向 = 局部 <b>+Z</b>,取自 {@link WeaponBackFeatureRenderer} 已实机验证的背挂约定
 *       (它 translate 正的 weaponBackBackOff 把武器放到背上),不自己猜坐标系朝向。</li>
 * </ul>
 *
 * <p><b>触发条件复用 m394 的教训</b>:isSprinting 旗标在顶墙/停步瞬间会残留(当年「站定了武器还挂背上」
 * 就是它),所以同样加水平速度门槛——站定立刻没残影。潜行/骑乘不出;第一人称天然不出(玩家模型不渲染)。
 *
 * <p><b>m434 实机回修(作者:「为什么残影左右摇头」)——两处病根,都是「残影必须留在过去」这条没做到:</b>
 * <ul>
 *   <li><b>甩尾(主因)</b>:m433 把残影钉在<b>局部</b>坐标系身后固定距离,而局部系是跟着 bodyYaw 转的
 *       ——转视角(bodyYaw 追 headYaw 是滞后+分步的,直线跑也会来回微调)时整条残影像转盘上的尾巴
 *       左右扫,这就是「摇头」。修=改钉在<b>世界</b>坐标:每客户端 tick 采样玩家真实位置进环形缓冲,
 *       渲染时取 N 帧前那个点、算出它相对当前插值位置的世界偏移,再逆变换回局部系画。
 *       残影从此待在玩家<b>真正走过</b>的地方,转身时是「甩在后面」而不是「跟着头转」。</li>
 *   <li><b>头部随视角摆(次因)</b>:每层副本都用当前姿态,head 的相对 yaw/pitch 也是当前的,
 *       甩鼠标时几个残影的脑袋会齐刷刷一起转。修=画残影前把 head/hat 的 yaw/pitch 归零(与躯干同向)、
 *       画完原样恢复——残影本就是一团速度虚影,脑袋不该有独立视线。</li>
 * </ul>
 *
 * <p><b>m436 二次回修(作者:「进去后还是摇头,能不能头不动」)——m434 只修了「在哪」,没修「朝哪」:</b>
 * 残影位置钉到世界坐标之后,每一层的**朝向**用的仍是<b>当前</b> bodyYaw,于是所有残影会跟着当前朝向
 * 齐刷刷原地转——转视角时整排一起摆,而且**直线跑也摆**(原版 bodyYaw 会朝移动方向来回微调,
 * 这就是「明明没转视角也在摇头」的由来)。修=轨迹缓冲连 bodyYaw 一起采样,每层渲染前右乘
 * {@code Ry(历史yaw − 当前yaw)} 把它转回被采样时的朝向。至此残影三要素(位置/朝向/头部)全部凝固在过去,
 * 玩家怎么甩视角它都纹丝不动,只随身体真实位移向后拖。
 *
 * <p>逆变换推导(渲染器已施加 {@code Ry(180°-bodyYaw)} 与 {@code scale(-1,-1,1)},故局部→世界为
 * {@code wx = lx·cos+lz·sin, wz = lx·sin-lz·cos, wy = -ly};该 XZ 矩阵对称正交且自逆,
 * 所以世界→局部是同一组式子):{@code lx = wx·cos+wz·sin, lz = wx·sin-wz·cos, ly = -wy}。
 * 代入 lz=1 得世界方向 (sin yaw, -cos yaw) 正是玩家背后,与 m247 背挂约定自洽,互为佐证。
 *
 * <p>预算:层数走 {@link FxBudget#scaleCount}(LOW 档自动减层)、总开关 enableSprintAfterimage、
 * FxBudget.on() 为 OFF 档直接退场;每层就是一次模型 draw,上限 4 层,是同屏玩家数 ×4 的常数开销。
 * 轨迹缓冲每玩家 {@value #TRAIL_LEN} 个 Vec3d,离线/换世界清表(不清也只是过期坐标,下一 tick 即覆盖)。
 */
public class SprintAfterimageFeature
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    private static final Identifier WHITE = Identifier.of(Yongye.MOD_ID, "textures/fx/white.png");
    private static final int MAX_LAYERS = 4;
    /** m434 轨迹环形缓冲长度(客户端 tick);4 层 × 每层最多 3 tick 间隔 = 12,留一格余量。 */
    private static final int TRAIL_LEN = 13;

    /** m434:每玩家最近若干 tick 的真实世界位置(环形缓冲,写指针在 idx)。 */
    private static final class Trail {
        final Vec3d[] pos = new Vec3d[TRAIL_LEN];
        /** m436:同时记下当时的 bodyYaw——残影不光要待在过去的位置,还得<b>朝着过去的方向</b>。 */
        final float[] yaw = new float[TRAIL_LEN];
        int idx = 0, filled = 0;
        void push(Vec3d p, float bodyYaw) {
            pos[idx] = p;
            yaw[idx] = bodyYaw;
            idx = (idx + 1) % TRAIL_LEN;
            if (filled < TRAIL_LEN) filled++;
        }
        private int at(int back) { return ((idx - back) % TRAIL_LEN + TRAIL_LEN) % TRAIL_LEN; }
        /** back=1 表示上一 tick 采样点;超出已填充范围返回 null。 */
        Vec3d back(int back) {
            if (back <= 0 || back > filled) return null;
            return pos[at(back)];
        }
        /** 该采样点当时的 bodyYaw(度)。 */
        float backYaw(int back) { return yaw[at(back)]; }
    }

    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static Object lastWorldRef = null;

    /**
     * m434 轨迹采样:每客户端 tick 记一次所有可见玩家的真实位置(YongyeClient 的 END_CLIENT_TICK 调)。
     * 不在渲染里采样——渲染帧率不固定,采出来的间距会随帧率变。换世界清表防旧坐标错绑。
     */
    public static void tick(net.minecraft.client.MinecraftClient mc) {
        if (mc.world != lastWorldRef) { lastWorldRef = mc.world; TRAILS.clear(); }
        if (mc.world == null) return;
        if (!YongyeConfig.get().enableSprintAfterimage || !FxBudget.on()) {
            if (!TRAILS.isEmpty()) TRAILS.clear();
            return;
        }
        TRAILS.keySet().removeIf(id -> mc.world.getPlayerByUuid(id) == null);   // 离开视野/下线即清
        for (net.minecraft.entity.player.PlayerEntity p : mc.world.getPlayers()) {
            TRAILS.computeIfAbsent(p.getUuid(), k -> new Trail()).push(p.getPos(), p.bodyYaw);
        }
    }

    public SprintAfterimageFeature(
            FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    /** 出残影条件:开关开 + 疾跑且真在移动(m394 教训:旗标会残留)+ 不潜行不骑乘。 */
    private static boolean shouldTrail(AbstractClientPlayerEntity p) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableSprintAfterimage || !FxBudget.on()) return false;
        if (!p.isSprinting() || p.isSneaking() || p.hasVehicle()) return false;
        double vx = p.getVelocity().x, vz = p.getVelocity().z;
        return vx * vx + vz * vz >= 1.0e-4;      // 站定/顶墙:旗标还在但没位移,不画
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!shouldTrail(player)) return;
        YongyeConfig c = YongyeConfig.get();

        int layers = Math.max(1, Math.min(MAX_LAYERS,
                FxBudget.scaleCount(Math.max(1, Math.min(MAX_LAYERS, c.sprintAfterimageLayers)))));
        float spacing = (float) Math.max(0.05, Math.min(1.0, c.sprintAfterimageSpacing));
        int peak = Math.max(8, Math.min(200, c.sprintAfterimageAlpha));
        int rgb = c.sprintAfterimageColor & 0xFFFFFF;

        Trail trail = TRAILS.get(player.getUuid());
        if (trail == null) return;                     // 还没采到轨迹(刚进世界):这一帧不画,下一 tick 就有

        // 采样间隔:把「层间距(方块)」换算成隔几个 tick 取一个点——跑得快取近的点、跑得慢取远的点,
        // 观感上层与层的实际间距才稳定(纯按 tick 取的话慢走时几层会糊成一坨)。
        double speed = Math.sqrt(player.getVelocity().x * player.getVelocity().x
                + player.getVelocity().z * player.getVelocity().z);        // 方块/tick
        int step = (int) Math.round(spacing / Math.max(0.02, speed));
        step = Math.max(1, Math.min(3, step));

        Vec3d cur = player.getLerpedPos(tickDelta);                        // 渲染器锚点(yarn 已核 method_30950)
        float yawDeg = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float yaw = yawDeg * ((float) Math.PI / 180f);
        float cos = MathHelper.cos(yaw), sin = MathHelper.sin(yaw);

        PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
        // m434 冻头:残影是速度虚影,不该有独立视线;画完原样恢复(渲染单线程,共享模型必须还回去)
        float hY = model.head.yaw, hP = model.head.pitch, tY = model.hat.yaw, tP = model.hat.pitch;
        model.head.yaw = 0f; model.head.pitch = 0f;
        model.hat.yaw = 0f;  model.hat.pitch = 0f;

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        for (int i = 1; i <= layers; i++) {
            Vec3d past = trail.back(i * step);
            if (past == null) break;                    // 轨迹还没这么长:后面几层这帧先不画
            // 越远的那层越淡(线性衰减到 ~25% 峰值),alpha 显式带满位(m213 铁律:1.21 颜色 int 含 alpha)
            int a = (int) (peak * (1f - 0.75f * (i - 1) / (float) Math.max(1, layers)));
            if (a < 4) continue;
            double wx = past.x - cur.x, wy = past.y - cur.y, wz = past.z - cur.z;
            // 世界偏移 → 局部(推导见类注释;该 XZ 矩阵自逆,与局部→世界同一组式子)
            float lx = (float) (wx * cos + wz * sin);
            float ly = (float) (-wy);
            float lz = (float) (wx * sin - wz * cos);
            matrices.push();
            matrices.translate(lx, ly, lz);
            // m436:把这一层转回它被采样时的朝向。渲染器施加的是 Ry(180°-当前yaw)·S(-1,-1,1),
            // 要等效换成 Ry(180°-历史yaw)·S,需右乘 R = S⁻¹·Ry(当前-历史)·S;
            // 而 S·Ry(θ)·S = Ry(−θ)(S=diag(-1,-1,1) 自逆),故 R = Ry(历史yaw − 当前yaw)。
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(
                    MathHelper.wrapDegrees(trail.backYaw(i * step) - yawDeg)));
            getContextModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV, (a << 24) | rgb);
            matrices.pop();
        }

        model.head.yaw = hY; model.head.pitch = hP;     // 恢复共享模型
        model.hat.yaw = tY;  model.hat.pitch = tP;
    }
}
