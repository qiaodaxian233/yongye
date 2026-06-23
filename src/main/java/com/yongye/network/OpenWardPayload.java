package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:通知客户端打开「守护」界面(WardScreen)。
 *
 * 为什么走网络包:守护书 use() 在服务端执行,而界面只能在客户端打开,
 * 故服务端发此空包给玩家,客户端收到后 setScreen(new WardScreen())。
 * 写法照搬同为「空包 + S2C 开界面」的 OpenDebugPayload。
 */
public record OpenWardPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenWardPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_ward"));
    public static final PacketCodec<PacketByteBuf, OpenWardPayload> CODEC =
            PacketCodec.unit(new OpenWardPayload());

    @Override
    public CustomPayload.Id<OpenWardPayload> getId() {
        return ID;
    }
}
