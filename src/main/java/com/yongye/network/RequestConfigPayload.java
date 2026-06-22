package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S:打开「爆率编辑器」时,客户端请求服务端把当前可编辑字段的值发回来(用于预填输入框)。
 * 空包,无字段。
 */
public record RequestConfigPayload() implements CustomPayload {

    public static final CustomPayload.Id<RequestConfigPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "request_config"));

    public static final PacketCodec<PacketByteBuf, RequestConfigPayload> CODEC =
            PacketCodec.unit(new RequestConfigPayload());

    @Override
    public CustomPayload.Id<RequestConfigPayload> getId() {
        return ID;
    }
}
