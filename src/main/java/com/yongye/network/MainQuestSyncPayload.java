package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C:主线任务进度快照(m328)。stage=当前阶段号;complete=当前阶段已达成(服务端判);计数供进度条显示。 */
public record MainQuestSyncPayload(int stage, boolean complete, long kills, int eliteKills, int bossKills,
                                   boolean painSlain, boolean dragonSlain) implements CustomPayload {
    public static final CustomPayload.Id<MainQuestSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "main_quest_sync"));
    public static final PacketCodec<PacketByteBuf, MainQuestSyncPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeVarInt(v.stage); buf.writeBoolean(v.complete); buf.writeVarLong(v.kills);
                buf.writeVarInt(v.eliteKills); buf.writeVarInt(v.bossKills);
                buf.writeBoolean(v.painSlain); buf.writeBoolean(v.dragonSlain);
            },
            buf -> new MainQuestSyncPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean())
    );
    @Override public CustomPayload.Id<MainQuestSyncPayload> getId() { return ID; }
}
