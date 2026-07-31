package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:伤害飘字(m373,3A 打磨路线图第 1 项)。
 * 玩家命中怪物时把这一下的落点与数值发给攻击者本人,客户端在世界内弹出漂浮数字
 * (普通=白字,重击=金色大字),弹出过冲→上浮→淡出。纯视觉层,零伤害改动。
 * 位置发怪物身体上沿一点(bodyY 0.9),随机散布偏移由客户端自己加(省 6 字节且不影响观感)。
 *
 * @param x/y/z  数字出生点(怪物身上)
 * @param amount 这一下的伤害值(服务端真值,客户端只负责显示)
 * @param kind   0=普通命中 1=重击(单刀≥怪最大生命 25%,与 CombatFxPayload.HEAVY 同口径)
 * @param targetId 被命中实体的网络 id(m385 微型血条追踪用;顺手为飘字同目标合并留口)
 */
public record DamageNumberPayload(double x, double y, double z, float amount, int kind, int targetId)
        implements CustomPayload {

    public static final int HIT = 0, HEAVY = 1;

    public static final CustomPayload.Id<DamageNumberPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "damage_number"));

    public static final PacketCodec<PacketByteBuf, DamageNumberPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeDouble(value.x);
                buf.writeDouble(value.y);
                buf.writeDouble(value.z);
                buf.writeFloat(value.amount);
                buf.writeInt(value.kind);
                buf.writeInt(value.targetId);
            },
            buf -> new DamageNumberPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readInt(), buf.readInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
