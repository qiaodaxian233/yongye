package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S:玩家在替换界面点选要丢弃的职业后提交。
 * discardClassId=要丢弃的职业 id;newClassId=要换上的新职业 id(服务端会校验背包确有该职业书后才扣书生效)。
 */
public record ClassReplacePayload(String discardClassId, String newClassId) implements CustomPayload {
    public static final CustomPayload.Id<ClassReplacePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "class_replace"));

    public static final PacketCodec<PacketByteBuf, ClassReplacePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.discardClassId);
                buf.writeString(value.newClassId);
            },
            buf -> new ClassReplacePayload(buf.readString(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
