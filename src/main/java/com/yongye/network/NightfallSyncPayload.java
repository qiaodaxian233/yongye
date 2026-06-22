package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:把当前永夜等级、阶段名、视野压缩强度同步给客户端。
 * 永夜是服务端状态,客户端 HUD / 暗角需要它;在 setLevel 变更时 + 玩家进入时下发。
 * vision:暗角强度(0=不压缩,>0 越大越暗),由服务端按配置(enableNightfallDarkness/minLevel)算好下发。
 */
public record NightfallSyncPayload(int level, String name, int vision) implements CustomPayload {

    public static final CustomPayload.Id<NightfallSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "nightfall_sync"));

    public static final PacketCodec<PacketByteBuf, NightfallSyncPayload> CODEC = PacketCodec.of(
            (v, buf) -> { buf.writeVarInt(v.level); buf.writeString(v.name); buf.writeVarInt(v.vision); },
            buf -> new NightfallSyncPayload(buf.readVarInt(), buf.readString(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<NightfallSyncPayload> getId() {
        return ID;
    }
}
