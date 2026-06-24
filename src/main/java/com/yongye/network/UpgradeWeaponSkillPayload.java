package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S:在装备介绍界面点「升级技能」按钮(index=WeaponSkill 序号),请求用背包终焉精华升一级。
 * 服务端 WeaponSkillManager.upgradeSkill 校验+扣料+写回等级。
 */
public record UpgradeWeaponSkillPayload(int index) implements CustomPayload {
    public static final CustomPayload.Id<UpgradeWeaponSkillPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "upgrade_weapon_skill"));
    public static final PacketCodec<PacketByteBuf, UpgradeWeaponSkillPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeVarInt(value.index),
            buf -> new UpgradeWeaponSkillPayload(buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<UpgradeWeaponSkillPayload> getId() {
        return ID;
    }
}
