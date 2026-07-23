package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:沉浸式战斗手感(m239)。
 * 玩家命中/重击/击杀怪物时,服务端把这次反馈的参数打包发给攻击者本人,
 * 客户端据此做镜头微震 + FOV 顿挫 + 击杀闪光/确认音。纯视听反馈,不改任何伤害结算。
 * 强度倍率(combatFxShakeScale/combatFxFovKick)在服务端折算进 shake/fov 后再发,
 * 客户端不需要读服务端配置。
 *
 * @param kind  0=普通命中 1=重击(单刀≥怪最大生命25%) 2=击杀
 * @param shake 镜头抖动强度(已乘服务端倍率)
 * @param fov   FOV 顿挫幅度(度,已乘服务端倍率)
 * @param flash 击杀时是否闪光(combatFxKillFlash)
 * @param sound 击杀时是否播确认音(combatFxKillSound)
 */
public record CombatFxPayload(int kind, float shake, float fov, boolean flash, boolean sound)
        implements CustomPayload {

    public static final int HIT = 0, HEAVY = 1, KILL = 2;

    public static final CustomPayload.Id<CombatFxPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "combat_fx"));

    public static final PacketCodec<PacketByteBuf, CombatFxPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeInt(value.kind);
                buf.writeFloat(value.shake);
                buf.writeFloat(value.fov);
                buf.writeBoolean(value.flash);
                buf.writeBoolean(value.sound);
            },
            buf -> new CombatFxPayload(buf.readInt(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
