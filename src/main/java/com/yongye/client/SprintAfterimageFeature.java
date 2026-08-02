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
 * <p>预算:层数走 {@link FxBudget#scaleCount}(LOW 档自动减层)、总开关 enableSprintAfterimage、
 * FxBudget.on() 为 OFF 档直接退场;每层就是一次模型 draw,上限 4 层,是同屏玩家数 ×4 的常数开销。
 */
public class SprintAfterimageFeature
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    private static final Identifier WHITE = Identifier.of(Yongye.MOD_ID, "textures/fx/white.png");
    private static final int MAX_LAYERS = 4;

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

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucentEmissive(WHITE));
        for (int i = 1; i <= layers; i++) {
            // 越远的那层越淡(线性衰减到 ~25% 峰值),alpha 必须显式带满位(m213 铁律:1.21 颜色 int 含 alpha)
            int a = (int) (peak * (1f - 0.75f * (i - 1) / (float) Math.max(1, layers)));
            if (a < 4) continue;
            matrices.push();
            matrices.translate(0.0f, 0.0f, spacing * i);   // 局部 +Z = 身后(照 m247 背挂约定)
            getContextModel().render(matrices, vc, light, OverlayTexture.DEFAULT_UV,
                    (a << 24) | rgb);
            matrices.pop();
        }
    }
}
