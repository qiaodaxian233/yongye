package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:受击方向指示(m374,3A 打磨路线图第 2 项)。
 * 玩家挨打瞬间把伤害来源的世界水平坐标发给受击者本人,客户端在准星四周
 * 对应方向弹红色弧形指示(存世界坐标逐帧重算方位角=转视角时指示实时对齐来源)。
 * 只发水平 x/z——指示器是 2D 方位,高度差不参与。
 *
 * @param x/z      伤害来源水平坐标(攻击者优先,弹射物本体兜底)
 * @param severity 这一下占玩家最大生命的比例(0~1,客户端据此调指示浓度)
 */
public record HurtDirectionPayload(double x, double z, float severity)
        implements CustomPayload {

    public static final CustomPayload.Id<HurtDirectionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "hurt_direction"));

    public static final PacketCodec<PacketByteBuf, HurtDirectionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x);
                buf.writeDouble(value.z);
                buf.writeFloat(value.severity);
            },
            buf -> new HurtDirectionPayload(buf.readDouble(), buf.readDouble(), buf.readFloat())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
