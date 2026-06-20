package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:请求打开饰品栏。 */
public record OpenAccessoryPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenAccessoryPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_accessory"));
    public static final PacketCodec<PacketByteBuf, OpenAccessoryPayload> CODEC =
            PacketCodec.unit(new OpenAccessoryPayload());

    @Override
    public CustomPayload.Id<OpenAccessoryPayload> getId() {
        return ID;
    }
}
