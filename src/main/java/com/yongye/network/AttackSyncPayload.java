package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:同步玩家最终「攻击伤害」属性值。
 * 原版的 GENERIC_ATTACK_DAMAGE 不是 tracked 属性,永远不会下发到客户端
 * (客户端本地读到的一直是基础值 1.0);而攻击速度是 tracked 的、能同步——
 * 这就是成长面板"攻击伤害 1 / 攻击速度却正常"的根因。
 * 每 10 tick 在服务端读一次 getAttributeValue(GENERIC_ATTACK_DAMAGE)
 * (含手持武器 + 强化 + 职业修饰符),数值变化时才下发,由成长面板显示。
 */
public record AttackSyncPayload(double atk) implements CustomPayload {

    public static final CustomPayload.Id<AttackSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "attack_sync"));

    public static final PacketCodec<PacketByteBuf, AttackSyncPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeDouble(value.atk),
            buf -> new AttackSyncPayload(buf.readDouble())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
