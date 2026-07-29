package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:仓库快照(m356)。data="键=数量\n" 多行(照 ConfigValuesPayload 在树先例,
 * 零新 codec 面);键=物品 id 或 id#技能书等级,客户端 VaultManager.stackFor 重建展示用 stack。
 */
public record VaultSyncPayload(String data) implements CustomPayload {
    public static final CustomPayload.Id<VaultSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "vault_sync"));
    public static final PacketCodec<PacketByteBuf, VaultSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.data == null ? "" : value.data),   // 空值兜底(m336 铁律)
            buf -> new VaultSyncPayload(buf.readString())
    );
    @Override public CustomPayload.Id<VaultSyncPayload> getId() { return ID; }
}
