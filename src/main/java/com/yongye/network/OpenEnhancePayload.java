package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:请求打开武器强化窗口。 */
public record OpenEnhancePayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenEnhancePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_enhance"));
    public static final PacketCodec<PacketByteBuf, OpenEnhancePayload> CODEC =
            PacketCodec.unit(new OpenEnhancePayload());

    @Override
    public CustomPayload.Id<OpenEnhancePayload> getId() {
        return ID;
    }
}
