package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:已满 2 职业时右键新职业书,通知客户端弹出替换界面。
 * 携带「将学习的新职业 id」+「当前两个职业 id(槽0=本命、槽1=第二)」,供界面展示可丢弃的两张卡。
 */
public record OpenClassReplacePayload(String newClassId, String slot0Id, String slot1Id) implements CustomPayload {
    public static final CustomPayload.Id<OpenClassReplacePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_class_replace"));

    public static final PacketCodec<PacketByteBuf, OpenClassReplacePayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeString(value.newClassId);
                buf.writeString(value.slot0Id);
                buf.writeString(value.slot1Id);
            },
            buf -> new OpenClassReplacePayload(buf.readString(), buf.readString(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
