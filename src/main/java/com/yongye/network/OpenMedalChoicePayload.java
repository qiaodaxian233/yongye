package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C(m366 猎杀勋章):通知客户端弹「击杀里程碑三选一」界面。
 * data = "id:当前层数:每层百分比|id:lv:pct|id:lv:pct"(三张卡,pct 用服务端配置值拼好,
 * 客户端纯解析展示——专用服上客户端本地 config 是默认值,不能拿来当展示依据)。
 */
public record OpenMedalChoicePayload(String data) implements CustomPayload {
    public static final CustomPayload.Id<OpenMedalChoicePayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_medal_choice"));

    public static final PacketCodec<PacketByteBuf, OpenMedalChoicePayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.data == null ? "" : value.data),   // 空值兜底(m336 铁律)
            buf -> new OpenMedalChoicePayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
