package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:连击计数同步(m273)。
 * 命中累计 +1 即发,断连清零发 0;客户端据此画 HUD 连击数(ComboHud)。
 *
 * @param count 当前连击数(0=清零收起)
 */
public record ComboPayload(int count) implements CustomPayload {

    public static final CustomPayload.Id<ComboPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "combo"));

    public static final PacketCodec<PacketByteBuf, ComboPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeInt(value.count),
            buf -> new ComboPayload(buf.readInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
