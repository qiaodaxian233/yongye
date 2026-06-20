package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 → 服务端:请求施放武器主动技能。
 *  index: 技能索引(0 混沌斩 / 1 深渊吞噬 / 2 终焉降临)。
 */
public record SkillUsePayload(int index) implements CustomPayload {

    public static final CustomPayload.Id<SkillUsePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "skill_use"));

    public static final PacketCodec<PacketByteBuf, SkillUsePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new SkillUsePayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
