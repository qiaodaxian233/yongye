package com.yongye.mixin.client;

import com.yongye.YongyeConfig;
import com.yongye.client.SlashFxManager;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拔刀剑式攻击动画(m240/m242)第三人称姿态 + MoBends 式全身发力(m243)。
 * m243 学 MoBends(AttackSlashDown/WhirlSlash/Stance 逐帧扒过)三板斧:
 *  ① 躯干大幅参与——body 拧身 + 前倾(他们 20~40°,原版三方风格都只动手臂,发力感来自这里);
 *  ② 头部反向补偿——head 减去躯干旋转增量,身体甩出去、视线锁定目标不动(MoBends 的
 *    head.orient(headYaw − bodyRotation) 手法,是「帅」的灵魂);
 *  ③ 攻击弓步——副手侧腿前弓、持械侧腿后蹬(他们腿 orientX −30°+分腿;我们无膝关节做直腿弓步);
 *  外加不对称包络:出手前反向蓄力 → 爆发过冲 → 收势缓落(他们 armSwing=clamp(t×3) 快打慢收)。
 * 安全性同 m240:原版 setAngles 每帧对 body/head/双臂/双腿的旋转全部重新赋值(腿=limbSwing 公式
 * 无条件赋值,body.pitch 有 else 归零分支),TAIL 叠加不跨帧累积;只碰旋转不碰 pivot(pivot 的
 * 重置路径不保证,碰了会漂移);包络两端(p=0/1)均为 0。盔甲 copyBipedStateTo 照抄部件角度跟着摆。
 * require = 0:映射不符则静默不挂,只丢姿态不崩游戏。slashFxBends=false 回 m242 简版(仅拧身+臂)。
 *
 * m248 注入点改挂 {@link BipedEntityModel}(原挂 PlayerEntityModel):
 *  ① 实机反馈「姿态没生效」,最大嫌疑就是 PlayerEntityModel 的 setAngles 注入点在运行时对不上而
 *    require=0 静默失效;BipedEntityModel.setAngles 是姿态计算的本体实现,必然存在,注入点最稳。
 *  ② 顺带修一个层级瑕疵:PlayerEntityModel.setAngles 是 super.setAngles 之后才把 袖子/裤腿/外套
 *    copyTransform 过去——旧版挂 PlayerEntityModel TAIL 在拷贝「之后」才改角度,皮肤外层不跟手;
 *    改挂 BipedEntityModel TAIL 后姿态在拷贝「之前」就位,外层自然跟随。
 *  ③ 语义不变:处理器开头加 instanceof PlayerEntity 门,仍只对玩家生效(僵尸等双足怪不摆)。
 *  另加 slashFxPoseScale 幅度倍率(默认 1.35 比旧版更夸张),嫌轻/嫌重 config set 一条即调。
 */
@Mixin(BipedEntityModel.class)
public abstract class SlashPoseMixin {

    @Inject(method = "setAngles", at = @At("TAIL"), require = 0)
    private void yongye$slashPose(LivingEntity entity, float limbAngle, float limbDistance,
                                  float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        com.yongye.client.CombatFxManager.markInjected("SlashPose(姿态)");
        if (!(entity instanceof PlayerEntity)) return;    // 只对玩家摆姿态(语义与旧版一致)
        BipedEntityModel<?> m = (BipedEntityModel<?>) (Object) this;
        float p = m.handSwingProgress;                    // 渲染器每帧写入的插值挥手进度
        if (p <= 0f || p >= 1f) return;
        if (!SlashFxManager.poseEligible(entity)) return; // 开关 + 主手武器判定,与轨迹同一套

        boolean rightHanded = entity.getMainArm() == Arm.RIGHT;
        ModelPart arm = rightHanded ? m.rightArm : m.leftArm;
        ModelPart off = rightHanded ? m.leftArm : m.rightArm;
        ModelPart mainLeg = rightHanded ? m.rightLeg : m.leftLeg;   // 持械侧腿(后蹬)
        ModelPart offLeg  = rightHanded ? m.leftLeg  : m.rightLeg;  // 副手侧腿(前弓)
        float dir = rightHanded ? 1f : -1f;

        YongyeConfig cfg = YongyeConfig.get();
        boolean bends = cfg.slashFxBends;
        float s = MathHelper.clamp((float) cfg.slashFxPoseScale, 0.3f, 2.5f); // m248 幅度倍率
        float e = MathHelper.sin(p * (float) Math.PI) * s;  // 拧身/弓步包络:起收归零(单向)
        float w = (bends ? yongye$strike(p) : MathHelper.sin(p * (float) Math.PI)) * s; // 挥击包络
        float k = bends ? 1.6f : 1.0f;                    // MoBends 档拧身放大

        float bYaw, bPitch;                               // 记录躯干增量,供头部反向补偿
        switch (SlashFxManager.poseVariant(entity)) {
            case 0 -> { // 斜劈:举臂过肩斜挥下,身体前拧前倾
                bYaw = -0.28f * k * e * dir; bPitch = 0.18f * e;
                arm.pitch += -0.75f * w;  arm.roll += 0.45f * w * dir;
            }
            case 1 -> { // 反手回斩:反向拧身,臂内旋
                bYaw =  0.26f * k * e * dir; bPitch = 0.14f * e;
                arm.pitch += -0.50f * w;  arm.roll += -0.60f * w * dir;
            }
            case 2 -> { // 上撩斩:前倾蓄势,臂从低处大弧挑上过头
                bYaw =  0.19f * k * e * dir; bPitch = 0.22f * e;
                arm.pitch += -1.80f * w;  arm.roll += 0.25f * w * dir;
            }
            case 4 -> { // 空中回旋斩:大幅拧身带双臂横甩 + 收腿剪(MoBends WhirlSlash)
                bYaw = -0.80f * k * e * dir; bPitch = 0f;
                arm.pitch += -1.35f * w;  arm.yaw += -0.65f * w * dir;
                off.yaw   +=  0.55f * e * dir;
                if (bends) { mainLeg.pitch += 0.50f * e; offLeg.pitch += -0.60f * e; }
            }
            case 5 -> { // 疾跑突刺:深前倾,持械臂蓄力后收再直挺刺出
                bYaw = -0.38f * k * e * dir; bPitch = 0.30f * e;
                arm.pitch += -1.50f * w;
                if (bends) { offLeg.pitch += -0.25f * e; mainLeg.pitch += 0.13f * e; } // 弓步加深
            }
            case 6 -> { // 潜行居合:低姿大横抽,臂平甩
                bYaw = -0.40f * k * e * dir; bPitch = 0.12f * e;
                arm.pitch += -0.55f * w;  arm.yaw += -0.85f * w * dir;
            }
            default -> { // 横扫收式(第四击):最大拧身,臂抬平横甩
                bYaw = -0.36f * k * e * dir; bPitch = 0.16f * e;
                arm.pitch += -1.05f * w;  arm.yaw += -0.55f * w * dir;
            }
        }

        m.body.yaw += bYaw;
        if (bends) {
            m.body.pitch += bPitch;                        // 前倾发力(原版有 else 归零分支,叠加安全)
            m.head.yaw   += -0.85f * bYaw;                 // MoBends 头部反补:身体甩、视线锁定
            m.head.pitch += -0.80f * bPitch;
            offLeg.pitch  += -0.45f * e;                   // 攻击弓步:副手侧前弓、持械侧后蹬
            mainLeg.pitch +=  0.32f * e;
            offLeg.yaw    += -0.12f * e * dir;             // 分腿站稳(MoBends 腿 rotateY ±25°)
            mainLeg.yaw   +=  0.12f * e * dir;
            off.roll      += -0.30f * e * dir;             // 副手外张护身(他们 offArm orientZ −80°)
        } else {
            m.head.yaw += 0.10f * e * dir;                 // 旧版:头部微随动
        }
        off.pitch += 0.22f * e;                            // 副手反向摆一点,身体不僵
    }

    /** 三段挥击包络(m243):蓄力反向(0~0.22,峰 −0.4)→ smoothstep 爆发到 1(~0.52)→ 二次缓落归零。 */
    private static float yongye$strike(float p) {
        if (p < 0.22f) { float t = p / 0.22f; return -0.40f * MathHelper.sin(t * 1.5708f); }
        if (p < 0.52f) { float t = (p - 0.22f) / 0.30f; return -0.40f + 1.40f * (t * t * (3f - 2f * t)); }
        float t = (p - 0.52f) / 0.48f;
        return (1f - t) * (1f - t);
    }
}
