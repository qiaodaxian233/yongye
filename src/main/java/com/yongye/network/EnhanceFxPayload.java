package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:强化结果演出(m409,3A 路线图第 20 项)。
 * onEnhance 结算完把结果发给操作者本人,客户端在强化界面上播演出——
 * 成功=等级数字滚动+金色粒子柱;碎裂=红闪+震屏加强;保护卷=金字提示。
 * **纯视觉层:强化逻辑/概率/落袋一个字不碰**(结果算完才发包)。
 *
 * @param startLevel 强化前等级
 * @param endLevel   强化后等级(碎裂=startLevel 原样带回,客户端只看 broke)
 * @param succeeded  本次成功次数
 * @param failed     本次失败次数(不碎的失败)
 * @param broke      是否碎裂
 * @param protect    保护卷是否抵挡了碎裂
 */
public record EnhanceFxPayload(int startLevel, int endLevel, int succeeded, int failed,
                               boolean broke, boolean protect) implements CustomPayload {

    public static final CustomPayload.Id<EnhanceFxPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "enhance_fx"));

    public static final PacketCodec<PacketByteBuf, EnhanceFxPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.startLevel);
                buf.writeVarInt(value.endLevel);
                buf.writeVarInt(value.succeeded);
                buf.writeVarInt(value.failed);
                buf.writeBoolean(value.broke);
                buf.writeBoolean(value.protect);
            },
            buf -> new EnhanceFxPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean(), buf.readBoolean())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
