package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:把当前永夜等级与阶段名同步给客户端(供屏幕中上 HUD 显示)。
 * 永夜是服务端状态,客户端 HUD 需要它才能显示;在 setLevel 变更时 + 玩家进入时下发。
 */
public record NightfallSyncPayload(int level, String name) implements CustomPayload {

    public static final CustomPayload.Id<NightfallSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "nightfall_sync"));

    public static final PacketCodec<PacketByteBuf, NightfallSyncPayload> CODEC = PacketCodec.of(
            (v, buf) -> { buf.writeVarInt(v.level); buf.writeString(v.name); },
            buf -> new NightfallSyncPayload(buf.readVarInt(), buf.readString())
    );

    @Override
    public CustomPayload.Id<NightfallSyncPayload> getId() {
        return ID;
    }
}
