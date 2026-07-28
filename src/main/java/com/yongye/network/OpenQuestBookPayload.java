package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** S2C:通知客户端打开「任务书」界面(m328,右键任务书触发;照 OpenWardPayload 口径)。 */
public record OpenQuestBookPayload() implements CustomPayload {
    public static final CustomPayload.Id<OpenQuestBookPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "open_quest_book"));
    public static final PacketCodec<PacketByteBuf, OpenQuestBookPayload> CODEC =
            PacketCodec.unit(new OpenQuestBookPayload());
    @Override public CustomPayload.Id<OpenQuestBookPayload> getId() { return ID; }
}
