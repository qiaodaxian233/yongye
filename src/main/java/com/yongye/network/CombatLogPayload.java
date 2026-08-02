package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:战斗日志·承伤条目(m415,路线图第 24 项)。
 * 玩家挨打时把来源名与伤量发给受害者本人(enableCombatLog 开才发,零常态流量);
 * 输出侧(暴击/处决)客户端直接从 DamageNumberPayload 就地取材不新增协议;
 * 异常状态客户端自扫,也不走网络。source 恒非空兜底(writeString(null) 踩坑第 5 条)。
 */
public record CombatLogPayload(float amount, String source) implements CustomPayload {

    public static final CustomPayload.Id<CombatLogPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "combat_log"));

    public static final PacketCodec<PacketByteBuf, CombatLogPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeFloat(value.amount);
                buf.writeString(value.source == null ? "?" : value.source);
            },
            buf -> new CombatLogPayload(buf.readFloat(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
