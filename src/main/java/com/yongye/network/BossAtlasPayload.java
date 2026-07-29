package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:Boss 图鉴数据(m351,任务书「BOSS」页)。
 * 槽位固定 7 个,顺序与客户端展示数组一一对应(改动必须两端同步!):
 *   0 红蜘蛛 red_spider · 1 死亡法师 death_mage · 2 浴火凤凰 fire_phoenix ·
 *   3 托罗龙 toro_dragon · 4 阿努比斯 anubis · 5 佩恩 pain · 6 末影龙 ender_dragon
 * kills=玩家个人击杀次数(BOSS_KILL_MAP 附件,persistent+copyOnDeath);
 * days=解锁天数门槛(服务端**实时配置**minDay,展示=minDay+1 照 m289 口径;-1=无天数门槛/末地)。
 * 跟随 MainQuestSyncPayload 同一触发点下发(Request/领奖/开书),零额外请求包。
 */
public record BossAtlasPayload(int[] kills, int[] days) implements CustomPayload {

    public static final int SLOTS = 7;

    public static final CustomPayload.Id<BossAtlasPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "boss_atlas"));

    public static final PacketCodec<PacketByteBuf, BossAtlasPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                for (int i = 0; i < SLOTS; i++) buf.writeVarInt(value.kills[i]);
                for (int i = 0; i < SLOTS; i++) buf.writeVarInt(value.days[i]);
            },
            buf -> {
                int[] k = new int[SLOTS], d = new int[SLOTS];
                for (int i = 0; i < SLOTS; i++) k[i] = buf.readVarInt();
                for (int i = 0; i < SLOTS; i++) d[i] = buf.readVarInt();
                return new BossAtlasPayload(k, d);
            }
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
