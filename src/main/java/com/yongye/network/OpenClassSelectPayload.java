package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C:登录时若尚未选择本命职业,通知客户端弹出选职界面。 */
public record OpenClassSelectPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenClassSelectPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_class_select"));
    public static final PacketCodec<PacketByteBuf, OpenClassSelectPayload> CODEC =
            PacketCodec.unit(new OpenClassSelectPayload());

    @Override
    public CustomPayload.Id<OpenClassSelectPayload> getId() {
        return ID;
    }
}
