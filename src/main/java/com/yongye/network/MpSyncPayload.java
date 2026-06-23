package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:同步玩家职业资源条(MP)。
 * mp: 当前资源值 0.0~1.0;每 10 tick 下发一次。
 * 各职业含义:
 *   术士=灵力(血量比), 刺客=暗能(脱战时间), 战士=怒气(受伤积累),
 *   剑客=剑气(命中积累), 肉盾=坚守(静止时间), 武僧=拳意(连击数)
 */
public record MpSyncPayload(float mp) implements CustomPayload {

    public static final CustomPayload.Id<MpSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "mp_sync"));

    public static final PacketCodec<PacketByteBuf, MpSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeFloat(value.mp),
            buf -> new MpSyncPayload(buf.readFloat())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
