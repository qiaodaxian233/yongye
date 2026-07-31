package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.item.ChaosBladeItem;
import com.yongye.item.ClassWeaponItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.util.math.RotationAxis;

/**
 * 疾跑收刀(m247)——学 Back Tools / BackSlot 的经典观感:
 * 疾跑且不在挥击时,主手武器从手上「收」到背后斜挎(第三人称);
 * 配套 {@code WeaponSheathMixin} 把手上那份藏掉,两处条件共用 {@link #shouldSheath},永不「手背两把」。
 * 挂在 body 骨骼上:潜行/骑乘的躯干倾斜自动跟随。第一人称不动(自己视角照常持刀,不影响操作感)。
 * 武器判定与拔刀刀光同口径:原版剑/斧/三叉戟 + 本模组武器。总开关 weaponOnBackEnabled。
 *
 * <p><b>m394 实机截图回修(作者:「肉盾武器怎么飞起来了」)</b>:两处病根——
 * ①旧 Z 旋转 -125° 想当然假设物品竖直起算,但 FIXED 展示模式下剑类模型<b>本身自带 45° 斜置</b>
 * (GUI 同款对角朝向),净角≈水平 → 大型职业武器(巨阙)看着就是横平悬空一把;默认角改
 * -80°(净效果=斜挎柄朝右肩),且角度/缩放/下移/贴背四个值全部<b>配置化</b>(weaponBack* 四项,
 * 设置屏「疾跑姿态」区可即点即调,不合适不用再改代码);②isSprinting 旗标在顶墙/停步瞬间
 * 可能残留=站定了武器还挂背上手上空着 → shouldSheath 加水平速度门槛,站定必回手。
 */
public class WeaponBackFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    public WeaponBackFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    /** 收刀条件(渲染背挂与藏手共用):开关开 + 疾跑且真在移动 + 不在挥击 + 主手是武器。 */
    public static boolean shouldSheath(AbstractClientPlayerEntity p) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.weaponOnBackEnabled || cfg.sprintWeaponStyle != 1) return false; // m327:仅「背后」样式走收刀
        if (!p.isSprinting() || p.handSwinging) return false;
        double vx = p.getVelocity().x, vz = p.getVelocity().z;                    // m394:站定必回手
        if (vx * vx + vz * vz < 1.0e-4) return false;
        return isWeapon(p.getMainHandStack());
    }

    /** m327 改 public:拖刀姿态(SlashPoseMixin)复用同一套武器判定。 */
    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        var item = stack.getItem();
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem
                || item instanceof ClassWeaponItem || item instanceof ChaosBladeItem;
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       AbstractClientPlayerEntity player, float limbAngle, float limbDistance,
                       float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!shouldSheath(player)) return;
        ItemStack stack = player.getMainHandStack();
        YongyeConfig cfg = YongyeConfig.get();                        // m394:四个背挂参数配置化即点即调
        float sc = (float) Math.max(0.2, Math.min(2.0, cfg.weaponBackScale));

        matrices.push();
        this.getContextModel().body.rotate(matrices); // 跟随躯干(潜行倾斜/坐骑自动正确)
        matrices.translate(0.0f, (float) cfg.weaponBackDownOff, (float) cfg.weaponBackBackOff);
        // 斜挎:FIXED 下剑模型自带 45° 斜置,默认 -80° 净效果=柄朝右肩上方(m394 修:旧 -125° 净角≈水平)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) cfg.weaponBackAngleDeg));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));  // 刀面贴背
        matrices.scale(sc, sc, sc);
        MinecraftClient.getInstance().getItemRenderer().renderItem(
                player, stack, ModelTransformationMode.FIXED, false,
                matrices, vertexConsumers, player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
        matrices.pop();
    }
}
