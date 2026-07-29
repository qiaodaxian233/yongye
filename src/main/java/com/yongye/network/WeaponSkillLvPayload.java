package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:三个武器技能的升级等级/升级花费/生效冷却 + 等级上限(m347,装备介绍面板显示)。
 * 花费与冷却在服务端按**服务端配置**算好再发(WeaponSkillManager.syncLevels),
 * 专用服上客户端本地配置与服务端不同也不会显示错值;lv0/1/2 对应 WeaponSkill 枚举序
 * (SLASH/DEVOUR/FINALITY),cd 为已按技能等级缩减后的生效值(tick)。
 */
public record WeaponSkillLvPayload(int lv0, int lv1, int lv2,
                                   int cost0, int cost1, int cost2,
                                   int cd0, int cd1, int cd2,
                                   int maxLevel) implements CustomPayload {

    public static final CustomPayload.Id<WeaponSkillLvPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "weapon_skill_lv"));

    public static final PacketCodec<PacketByteBuf, WeaponSkillLvPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.lv0);
                buf.writeVarInt(value.lv1);
                buf.writeVarInt(value.lv2);
                buf.writeVarInt(value.cost0);
                buf.writeVarInt(value.cost1);
                buf.writeVarInt(value.cost2);
                buf.writeVarInt(value.cd0);
                buf.writeVarInt(value.cd1);
                buf.writeVarInt(value.cd2);
                buf.writeVarInt(value.maxLevel);
            },
            buf -> new WeaponSkillLvPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
