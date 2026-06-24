package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:通知客户端打开「难度选择」界面(DifficultyScreen)。
 * 写法照搬同为「空包 + S2C 开界面」的 OpenDebugPayload / OpenClassSelectPayload。
 */
public record OpenDifficultyPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenDifficultyPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_difficulty"));
    public static final PacketCodec<PacketByteBuf, OpenDifficultyPayload> CODEC =
            PacketCodec.unit(new OpenDifficultyPayload());

    @Override
    public CustomPayload.Id<OpenDifficultyPayload> getId() {
        return ID;
    }
}
