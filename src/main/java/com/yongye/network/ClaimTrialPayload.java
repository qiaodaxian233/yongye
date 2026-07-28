package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:请求领取当前职业试炼奖励(m332,服务端权威复核)。 */
public record ClaimTrialPayload() implements CustomPayload {
    public static final CustomPayload.Id<ClaimTrialPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "claim_trial"));
    public static final PacketCodec<PacketByteBuf, ClaimTrialPayload> CODEC =
            PacketCodec.unit(new ClaimTrialPayload());
    @Override public CustomPayload.Id<ClaimTrialPayload> getId() { return ID; }
}
