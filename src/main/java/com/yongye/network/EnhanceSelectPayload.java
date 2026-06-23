package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S:在强化界面点选某件装备(背包槽位 slot),请求用背包里全部强化材料一键强化它。
 * 服务端 EquipmentEnhancer.enhanceFromInventory 校验+扣料+升级。
 */
public record EnhanceSelectPayload(int slot) implements CustomPayload {
    public static final CustomPayload.Id<EnhanceSelectPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "enhance_select"));
    public static final PacketCodec<PacketByteBuf, EnhanceSelectPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.slot),
            buf -> new EnhanceSelectPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<EnhanceSelectPayload> getId() {
        return ID;
    }
}
