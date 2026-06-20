package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:同步玩家已学技能。
 *  health: 血量强化累计等级。
 *  levels: 属性技能等级,按 SkillType.values() 顺序(攻击/护甲/恢复/闪避/反伤/抗性)。
 */
public record StatsPayload(int health, int[] levels) implements CustomPayload {

    public static final CustomPayload.Id<StatsPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "stats"));

    public static final PacketCodec<PacketByteBuf, StatsPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.health);
                buf.writeIntArray(value.levels);
            },
            buf -> new StatsPayload(buf.readVarInt(), buf.readIntArray())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
