package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:任务书界面打开/刷新时请求主线进度(m328);服务端回 {@link MainQuestSyncPayload}。 */
public record RequestMainQuestPayload() implements CustomPayload {
    public static final CustomPayload.Id<RequestMainQuestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "request_main_quest"));
    public static final PacketCodec<PacketByteBuf, RequestMainQuestPayload> CODEC =
            PacketCodec.unit(new RequestMainQuestPayload());
    @Override public CustomPayload.Id<RequestMainQuestPayload> getId() { return ID; }
}
