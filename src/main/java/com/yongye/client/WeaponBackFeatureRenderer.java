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
 * 实机调优项:FIXED 展示模式下各武器在背后的比例/贴背深度,不合适截图回来调这三个常量。
 */
public class WeaponBackFeatureRenderer
        extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {

    private static final float SCALE = 0.85f;   // 背挂缩放
    private static final float BACK_OFF = 0.19f; // 贴背深度(格)
    private static final float DOWN_OFF = 0.30f; // 背中偏下(格)

    public WeaponBackFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    /** 收刀条件(渲染背挂与藏手共用):开关开 + 疾跑 + 不在挥击 + 主手是武器。 */
    public static boolean shouldSheath(AbstractClientPlayerEntity p) {
        if (!YongyeConfig.get().weaponOnBackEnabled) return false;
        if (!p.isSprinting() || p.handSwinging) return false;
        return isWeapon(p.getMainHandStack());
    }

    private static boolean isWeapon(ItemStack stack) {
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

        matrices.push();
        this.getContextModel().body.rotate(matrices); // 跟随躯干(潜行倾斜/坐骑自动正确)
        matrices.translate(0.0f, DOWN_OFF, BACK_OFF);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-125.0f)); // 斜挎:柄朝右肩上方
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));  // 刀面贴背
        matrices.scale(SCALE, SCALE, SCALE);
        MinecraftClient.getInstance().getItemRenderer().renderItem(
                player, stack, ModelTransformationMode.FIXED, false,
                matrices, vertexConsumers, player.getWorld(), light, OverlayTexture.DEFAULT_UV, player.getId());
        matrices.pop();
    }
}
