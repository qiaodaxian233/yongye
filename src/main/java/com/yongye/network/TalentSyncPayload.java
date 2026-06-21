package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * S2C:同步玩家天赋状态给客户端(天赋界面读取)。
 *  points:  当前可用天赋点。
 *  classes: 已习得职业 id 列表(决定显示哪些天赋树)。
 *  learned: 各天赋节点已点等级(节点 id -> rank)。
 */
public record TalentSyncPayload(int points, List<String> classes, Map<String, Integer> learned) implements CustomPayload {

    public static final CustomPayload.Id<TalentSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "talent_sync"));

    public static final PacketCodec<PacketByteBuf, TalentSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.points);
                buf.writeVarInt(value.classes.size());
                for (String s : value.classes) buf.writeString(s);
                buf.writeVarInt(value.learned.size());
                for (Map.Entry<String, Integer> e : value.learned.entrySet()) {
                    buf.writeString(e.getKey());
                    buf.writeVarInt(e.getValue());
                }
            },
            buf -> {
                int points = buf.readVarInt();
                int nc = buf.readVarInt();
                List<String> classes = new ArrayList<>();
                for (int i = 0; i < nc; i++) classes.add(buf.readString());
                int nl = buf.readVarInt();
                Map<String, Integer> learned = new HashMap<>();
                for (int i = 0; i < nl; i++) {
                    String k = buf.readString();
                    int v = buf.readVarInt();
                    learned.put(k, v);
                }
                return new TalentSyncPayload(points, classes, learned);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
