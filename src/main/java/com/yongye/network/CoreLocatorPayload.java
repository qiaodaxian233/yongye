package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:把"距离该玩家最近的灾厄核心位置"同步给客户端(供 HUD 方向箭头显示)。
 *  - has=false 表示范围内当前没有核心 → 客户端不画箭头。
 *  - 核心是服务端状态且方块本身不动,故每 2 秒同步一次足够;
 *    箭头朝向由客户端用"玩家当前视角 + 这个坐标"逐帧计算,所以转视角时箭头是平滑转的。
 */
public record CoreLocatorPayload(boolean has, double x, double y, double z) implements CustomPayload {

    public static final CustomPayload.Id<CoreLocatorPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "core_locator"));

    public static final PacketCodec<PacketByteBuf, CoreLocatorPayload> CODEC = PacketCodec.of(
            (v, buf) -> {
                buf.writeBoolean(v.has);
                buf.writeDouble(v.x);
                buf.writeDouble(v.y);
                buf.writeDouble(v.z);
            },
            buf -> new CoreLocatorPayload(buf.readBoolean(), buf.readDouble(), buf.readDouble(), buf.readDouble())
    );

    @Override
    public CustomPayload.Id<CoreLocatorPayload> getId() {
        return ID;
    }
}
