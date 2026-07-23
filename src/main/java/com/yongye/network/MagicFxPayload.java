package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:地面魔法阵特效(m246,法师技能包素材)。
 * 技能施放点向附近玩家广播一枚魔法阵:5 色 × 18 帧生长动画(128×128,素材来自暂存包),
 * 客户端 {@code MagicCircleFxManager} 负责逐帧生长/旋转/淡出。纯视觉零结算。
 *
 * @param color  0蓝 1绿 2黄绿 3粉 4红(对应素材五色)
 * @param x/y/z  阵心世界坐标(y 已抬离地面少许)
 * @param radius 阵半径(格)
 */
public record MagicFxPayload(int color, double x, double y, double z, float radius)
        implements CustomPayload {

    public static final CustomPayload.Id<MagicFxPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "magic_fx"));

    public static final PacketCodec<PacketByteBuf, MagicFxPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeInt(value.color);
                buf.writeDouble(value.x);
                buf.writeDouble(value.y);
                buf.writeDouble(value.z);
                buf.writeFloat(value.radius);
            },
            buf -> new MagicFxPayload(buf.readInt(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readFloat())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
