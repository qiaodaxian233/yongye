package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:玩家在选职界面选定的本命职业 id。 */
public record ChooseClassPayload(String classId) implements CustomPayload {
    public static final CustomPayload.Id<ChooseClassPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "choose_class"));

    public static final PacketCodec<PacketByteBuf, ChooseClassPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.classId),
            buf -> new ChooseClassPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
