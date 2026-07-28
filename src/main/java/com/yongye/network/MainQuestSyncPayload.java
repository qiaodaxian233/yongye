package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C:主线/试炼/图鉴数据快照(m328,m332 扩展)。stage/trialStage=当前阶段;complete=达成可领;统计供图鉴页。 */
public record MainQuestSyncPayload(int stage, boolean complete, long kills, int eliteKills, int bossKills,
                                   boolean painSlain, boolean dragonSlain,
                                   int trialStage, boolean trialComplete,
                                   int maxEnhance, long totalSkill, int nightfall, long day,
                                   boolean ngPlus) implements CustomPayload {
    public static final CustomPayload.Id<MainQuestSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "main_quest_sync"));
    public static final PacketCodec<PacketByteBuf, MainQuestSyncPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeVarInt(v.stage); buf.writeBoolean(v.complete); buf.writeVarLong(v.kills);
                buf.writeVarInt(v.eliteKills); buf.writeVarInt(v.bossKills);
                buf.writeBoolean(v.painSlain); buf.writeBoolean(v.dragonSlain);
                buf.writeVarInt(v.trialStage); buf.writeBoolean(v.trialComplete);
                buf.writeVarInt(v.maxEnhance); buf.writeVarLong(v.totalSkill);
                buf.writeVarInt(v.nightfall); buf.writeVarLong(v.day); buf.writeBoolean(v.ngPlus);
            },
            buf -> new MainQuestSyncPayload(buf.readVarInt(), buf.readBoolean(), buf.readVarLong(),
                    buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                    buf.readVarInt(), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readBoolean())
    );
    @Override public CustomPayload.Id<MainQuestSyncPayload> getId() { return ID; }
}
