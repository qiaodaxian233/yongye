package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 → 服务端:材料兑换请求。
 *  tier: 0 碎片→结晶 / 1 结晶→核心 / 2 核心→血核。
 *  all:  false=兑换一次(10→1) / true=尽可能全部兑换。
 */
public record ExchangePayload(int tier, boolean all) implements CustomPayload {

    public static final CustomPayload.Id<ExchangePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "material_exchange"));

    public static final PacketCodec<PacketByteBuf, ExchangePayload> CODEC = PacketCodec.of(
            (value, buf) -> { buf.writeVarInt(value.tier); buf.writeBoolean(value.all); },
            buf -> new ExchangePayload(buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
