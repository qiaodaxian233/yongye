package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:BOSS 讨伐终结演出(m387,3A 打磨路线图第 16 项)。
 * 玩家击杀 BOSS 瞬间发给击杀者本人:金色闪光 + 「◆ 讨伐成功 ◆」大字幕 + BOSS 名副标 + 凯旋音。
 * 顿帧/震屏走既有 CombatFxPayload 加强档另发(m275 通道,只作用击杀者客户端,
 * 绝不冻结服务端 tick 或影响他人输入——评审 16 号红线)。
 *
 * @param bossName BOSS 显示名(已剥 ‖ 血量后缀;空值兜底为空串,防 writeString(null) 踢连接老坑)
 */
public record BossKillFxPayload(String bossName) implements CustomPayload {

    public static final CustomPayload.Id<BossKillFxPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "boss_kill_fx"));

    public static final PacketCodec<PacketByteBuf, BossKillFxPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeString(value.bossName == null ? "" : value.bossName),
            buf -> new BossKillFxPayload(buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
