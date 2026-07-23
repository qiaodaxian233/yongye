package com.yongye.mixin.client;

import com.yongye.client.SlashFxManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拔刀剑式攻击动画(m240):第三人称挥砍姿态。
 * SlashBlade-Refabricated 的玩家动作靠外部 player-animator 库 + VMD 动作文件(硬依赖,不引入);
 * 本实现在 PlayerEntityModel.setAngles 末尾**叠加**三式连击姿态(斜劈/反手/横扫,与轨迹的 combo 同步):
 * 身体拧转 + 持械臂大弧度摆动 + 副手反向平衡 + 头部随动,包络 sin(p·π) 起收都归零。
 * 安全性:只在 handSwingProgress∈(0,1) 生效,而原版 animateArms 在攻击期间每帧都会**重新赋值**
 * body.yaw / 双臂角度 / head.yaw——叠加量不会跨帧累积;盔甲层渲染前 copyBipedStateTo 会照抄部件角度,
 * 盔甲跟着摆不穿模。require = 0:映射不符则静默不挂,只丢姿态不崩游戏。
 */
@Mixin(PlayerEntityModel.class)
public abstract class SlashPoseMixin {

    @Inject(method = "setAngles", at = @At("TAIL"), require = 0)
    private void yongye$slashPose(LivingEntity entity, float limbAngle, float limbDistance,
                                  float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        float p = m.handSwingProgress;                    // 渲染器每帧写入的插值挥手进度
        if (p <= 0f || p >= 1f) return;
        if (!SlashFxManager.poseEligible(entity)) return; // 开关 + 主手武器判定,与轨迹同一套

        boolean rightHanded = entity.getMainArm() == Arm.RIGHT;
        ModelPart arm = rightHanded ? m.rightArm : m.leftArm;
        ModelPart off = rightHanded ? m.leftArm : m.rightArm;
        float dir = rightHanded ? 1f : -1f;
        float e = MathHelper.sin(p * (float) Math.PI);    // 包络:起收归零,不与原版动画打架

        switch (SlashFxManager.poseVariant(entity)) {
            case 0 -> { // 斜劈:举臂过肩斜挥下,身体前拧
                m.body.yaw += -0.28f * e * dir;
                arm.pitch  += -0.70f * e;
                arm.roll   +=  0.40f * e * dir;
            }
            case 1 -> { // 反手回斩:反向拧身,臂内旋
                m.body.yaw +=  0.26f * e * dir;
                arm.pitch  += -0.45f * e;
                arm.roll   += -0.55f * e * dir;
            }
            default -> { // 横扫收式:大拧身,臂抬平横甩
                m.body.yaw += -0.34f * e * dir;
                arm.pitch  += -1.00f * e;
                arm.yaw    += -0.45f * e * dir;
            }
        }
        off.pitch  += 0.22f * e;          // 副手反向摆一点,身体不僵
        m.head.yaw += 0.10f * e * dir;    // 头部微随动
    }
}
