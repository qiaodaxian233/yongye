package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:仓库界面按行「取出」一叠(m356);key=VaultManager 键(id 或 id#等级),服务端权威复核。 */
public record VaultWithdrawPayload(String key) implements CustomPayload {
    public static final CustomPayload.Id<VaultWithdrawPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "vault_withdraw"));
    public static final PacketCodec<PacketByteBuf, VaultWithdrawPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.key == null ? "" : value.key),   // 空值兜底(m336 铁律)
            buf -> new VaultWithdrawPayload(buf.readString())
    );
    @Override public CustomPayload.Id<VaultWithdrawPayload> getId() { return ID; }
}
