package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:对当前强化窗口里的装备执行升级(消耗全部材料、按数量×单值加等级)。 */
public record EnhanceApplyPayload() implements CustomPayload {
    public static final CustomPayload.Id<EnhanceApplyPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "enhance_apply"));
    public static final PacketCodec<PacketByteBuf, EnhanceApplyPayload> CODEC =
            PacketCodec.unit(new EnhanceApplyPayload());

    @Override
    public CustomPayload.Id<EnhanceApplyPayload> getId() {
        return ID;
    }
}
