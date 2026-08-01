package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:FX 测试触发(m411,`/yongye fxtest` 专用)。
 * 只戳客户端演出管理器的测试入口,**不碰任何真实状态**(永夜等级基线/BOSS 数据一概不动)。
 * kind:1=永夜转场(value=等级,>0 升级观感/0 降级观感,text=阶段名) 2=讨伐演出(text=BOSS名) 3=大招起手光晕。
 * text 恒非空串兜底(writeString(null) 会 EncoderException 踢连接——踩坑记录第 5 条)。
 */
public record FxTestPayload(int kind, int value, String text) implements CustomPayload {

    public static final int NIGHTFALL = 1, BOSSKILL = 2, CAST = 3;

    public static final CustomPayload.Id<FxTestPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "fx_test"));

    public static final PacketCodec<PacketByteBuf, FxTestPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.kind);
                buf.writeVarInt(value.value);
                buf.writeString(value.text == null ? "" : value.text);
            },
            buf -> new FxTestPayload(buf.readVarInt(), buf.readVarInt(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
