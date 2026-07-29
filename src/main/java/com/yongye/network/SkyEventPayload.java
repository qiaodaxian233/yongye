package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:永夜天象事件状态(m352,事件限定天象视觉)。
 * event 与 NightfallWeatherHandler.Event 序数契约一致:0 无 / 1 血月 / 2 酸雨 / 3 流星雨。
 * 事件开始/结束全服广播,玩家登录补发当前状态;客户端据此在 SkyTextureMixin 里把
 * 月亮/雨贴图运行时换成红月/绿雨(贴图不再常驻覆盖原版,治「天天血月」)。
 */
public record SkyEventPayload(int event) implements CustomPayload {

    public static final CustomPayload.Id<SkyEventPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "sky_event"));

    public static final PacketCodec<PacketByteBuf, SkyEventPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.event),
            buf -> new SkyEventPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
