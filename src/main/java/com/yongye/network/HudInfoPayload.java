package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:战况看板数据(m288,左上角信息块)。
 * kills:      累计杀怪总数(TOTAL_KILLS 附件,跨登录/死亡累计);
 * nextName:   下一阶段名(""=已至上限,不显示预告行);
 * nextSeconds:距久留自动升层剩余秒数(-1=不适用,只显示阶段名不显示倒计时)。
 * 天数不走包——昼夜时钟原版就同步到客户端,ProgressionManager.gameDay 客户端直接算。
 * 每 20 tick 下发一次(KillStatsHandler)。
 */
public record HudInfoPayload(long kills, String nextName, int nextSeconds) implements CustomPayload {

    public static final CustomPayload.Id<HudInfoPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "hud_info"));

    public static final PacketCodec<PacketByteBuf, HudInfoPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarLong(value.kills);
                buf.writeString(value.nextName);
                buf.writeVarInt(value.nextSeconds);
            },
            buf -> new HudInfoPayload(buf.readVarLong(), buf.readString(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
