package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:仓库界面打开/点刷新时请求快照(m356);服务端回 {@link VaultSyncPayload}。 */
public record RequestVaultPayload() implements CustomPayload {
    public static final CustomPayload.Id<RequestVaultPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "request_vault"));
    public static final PacketCodec<PacketByteBuf, RequestVaultPayload> CODEC =
            PacketCodec.unit(new RequestVaultPayload());
    @Override public CustomPayload.Id<RequestVaultPayload> getId() { return ID; }
}
