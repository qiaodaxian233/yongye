package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:背包「合书」按钮——请求把背包里所有技能书/血量书按类型一键合并(等级相加,自动扣阶段材料)。 */
public record MergeBooksPayload() implements CustomPayload {
    public static final CustomPayload.Id<MergeBooksPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "merge_books"));
    public static final PacketCodec<PacketByteBuf, MergeBooksPayload> CODEC =
            PacketCodec.unit(new MergeBooksPayload());

    @Override
    public CustomPayload.Id<MergeBooksPayload> getId() {
        return ID;
    }
}
