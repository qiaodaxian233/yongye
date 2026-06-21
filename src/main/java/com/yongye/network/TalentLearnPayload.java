package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:玩家在天赋界面点击某节点,请求加点(节点 id)。 */
public record TalentLearnPayload(String nodeId) implements CustomPayload {
    public static final CustomPayload.Id<TalentLearnPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "talent_learn"));

    public static final PacketCodec<PacketByteBuf, TalentLearnPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.nodeId),
            buf -> new TalentLearnPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
