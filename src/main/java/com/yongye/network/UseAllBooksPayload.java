package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:背包「学书」按钮——请求把背包里所有技能书/血量书一键学掉。 */
public record UseAllBooksPayload() implements CustomPayload {
    public static final CustomPayload.Id<UseAllBooksPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "use_all_books"));
    public static final PacketCodec<PacketByteBuf, UseAllBooksPayload> CODEC =
            PacketCodec.unit(new UseAllBooksPayload());

    @Override
    public CustomPayload.Id<UseAllBooksPayload> getId() {
        return ID;
    }
}
