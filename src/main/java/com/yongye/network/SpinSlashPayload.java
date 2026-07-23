package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S(m258 空中回旋斩):客户端在空中挥砍触发回旋斩(七式之四)时上报,
 * 服务端 SpinSlashHandler 校验(离地/武器/冷却)后对身周一圈结算伤害——回旋斩「转一圈就该扫一圈」。
 */
public record SpinSlashPayload() implements CustomPayload {
    public static final CustomPayload.Id<SpinSlashPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "spin_slash"));
    public static final PacketCodec<PacketByteBuf, SpinSlashPayload> CODEC = PacketCodec.unit(new SpinSlashPayload());

    @Override
    public CustomPayload.Id<SpinSlashPayload> getId() {
        return ID;
    }
}
