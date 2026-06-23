package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 客户端 → 服务端:对背包第 {@code slot} 个槽位的装备施加守护。
 *  slot:玩家背包槽位索引(PlayerInventory.getStack(slot) 的下标,含主背包/护甲/副手)。
 * 服务端会重新校验该槽确是「可守护装备且未守护」、且背包内有守护书,再施加并扣 1 本书。
 */
public record WardApplyPayload(int slot) implements CustomPayload {

    public static final CustomPayload.Id<WardApplyPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "ward_apply"));

    public static final PacketCodec<PacketByteBuf, WardApplyPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.slot),
            buf -> new WardApplyPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
