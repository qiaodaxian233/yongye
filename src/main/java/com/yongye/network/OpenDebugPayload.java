package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:通知客户端打开「调试菜单」界面(DebugScreen)。
 *
 * 为什么要走网络包:命令 `/yongye debug` 在服务端执行,而界面只能在客户端打开,
 * 所以由服务端发这个空包给玩家,客户端收到后 setScreen(new DebugScreen())。
 * 写法照搬同为「空包 + S2C 开界面」的 OpenClassSelectPayload。
 */
public record OpenDebugPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenDebugPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_debug"));
    public static final PacketCodec<PacketByteBuf, OpenDebugPayload> CODEC =
            PacketCodec.unit(new OpenDebugPayload());

    @Override
    public CustomPayload.Id<OpenDebugPayload> getId() {
        return ID;
    }
}
