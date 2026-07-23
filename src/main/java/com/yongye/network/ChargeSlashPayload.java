package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S(m257 蓄力重斩):客户端检测「按住攻击键蓄力后松开」,上报蓄了多少 tick;
 * 服务端 ChargeSlashHandler 校验(武器/冷却/tick 钳制)后结算锥形重斩。
 */
public record ChargeSlashPayload(int chargeTicks) implements CustomPayload {
    public static final CustomPayload.Id<ChargeSlashPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "charge_slash"));
    public static final PacketCodec<PacketByteBuf, ChargeSlashPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.chargeTicks),
            buf -> new ChargeSlashPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<ChargeSlashPayload> getId() {
        return ID;
    }
}
