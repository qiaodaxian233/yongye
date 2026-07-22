package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:玩家按下职业小技能键(m232,默认 C),请求施放本命职业的小技能。 */
public record ClassMinorSkillPayload() implements CustomPayload {
    public static final CustomPayload.Id<ClassMinorSkillPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "class_minor_skill"));
    public static final PacketCodec<PacketByteBuf, ClassMinorSkillPayload> CODEC =
            PacketCodec.unit(new ClassMinorSkillPayload());

    @Override
    public CustomPayload.Id<ClassMinorSkillPayload> getId() {
        return ID;
    }
}
