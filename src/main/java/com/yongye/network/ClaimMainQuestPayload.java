package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:请求领取当前主线任务奖励(m328,服务端权威复核达成条件)。 */
public record ClaimMainQuestPayload() implements CustomPayload {
    public static final CustomPayload.Id<ClaimMainQuestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "claim_main_quest"));
    public static final PacketCodec<PacketByteBuf, ClaimMainQuestPayload> CODEC =
            PacketCodec.unit(new ClaimMainQuestPayload());
    @Override public CustomPayload.Id<ClaimMainQuestPayload> getId() { return ID; }
}
