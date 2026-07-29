package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:仓库界面「存入全部材料」(m356);服务端 VaultManager.depositAll 扫主背包入库并回同步。 */
public record VaultDepositPayload() implements CustomPayload {
    public static final CustomPayload.Id<VaultDepositPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "vault_deposit"));
    public static final PacketCodec<PacketByteBuf, VaultDepositPayload> CODEC =
            PacketCodec.unit(new VaultDepositPayload());
    @Override public CustomPayload.Id<VaultDepositPayload> getId() { return ID; }
}
