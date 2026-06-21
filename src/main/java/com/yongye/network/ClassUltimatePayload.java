package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:玩家按下职业大招键,请求施放本命职业的主动技能。 */
public record ClassUltimatePayload() implements CustomPayload {
    public static final CustomPayload.Id<ClassUltimatePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "class_ultimate"));
    public static final PacketCodec<PacketByteBuf, ClassUltimatePayload> CODEC =
            PacketCodec.unit(new ClassUltimatePayload());

    @Override
    public CustomPayload.Id<ClassUltimatePayload> getId() {
        return ID;
    }
}
