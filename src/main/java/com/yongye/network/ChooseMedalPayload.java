package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S(m366 猎杀勋章):玩家在三选一界面选定的勋章 id。服务端权威复核(必须在 HUNT_PENDING 候选里)。 */
public record ChooseMedalPayload(String medalId) implements CustomPayload {
    public static final CustomPayload.Id<ChooseMedalPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "choose_medal"));

    public static final PacketCodec<PacketByteBuf, ChooseMedalPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.medalId == null ? "" : value.medalId),
            buf -> new ChooseMedalPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
