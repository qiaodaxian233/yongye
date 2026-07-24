package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:同步武器格挡状态(m278,进血条面板)。
 * cur/max: 当前格挡值 / 上限(上限=最大生命×比例,随成长变);
 * broken:  破防剩余 tick(>0=破防硬直中,HUD 整条红闪+倒计时);
 * holding: 是否正持有可格挡武器(不持械且满值时 HUD 隐藏格挡条,不占地方)。
 * 每 5 tick 下发一次(WeaponGuardHandler);客户端本地每 tick 递减 broken 保证倒计时平滑。
 */
public record GuardSyncPayload(float cur, float max, int broken, boolean holding) implements CustomPayload {

    public static final CustomPayload.Id<GuardSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "guard_sync"));

    public static final PacketCodec<PacketByteBuf, GuardSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeFloat(value.cur);
                buf.writeFloat(value.max);
                buf.writeVarInt(value.broken);
                buf.writeBoolean(value.holding);
            },
            buf -> new GuardSyncPayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
