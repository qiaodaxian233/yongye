package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S:在难度选择界面点选难度(GameDifficulty 的 ordinal),请求确定本局难度。
 * 服务端校验未选过才写入 ModAttachments.DIFFICULTY。
 */
public record ChooseDifficultyPayload(int index) implements CustomPayload {
    public static final CustomPayload.Id<ChooseDifficultyPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "choose_difficulty"));
    public static final PacketCodec<PacketByteBuf, ChooseDifficultyPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new ChooseDifficultyPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<ChooseDifficultyPayload> getId() {
        return ID;
    }
}
