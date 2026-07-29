package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** C2S:装备介绍界面打开时请求武器技能等级(m347);服务端回 {@link WeaponSkillLvPayload}。 */
public record RequestWeaponSkillPayload() implements CustomPayload {
    public static final CustomPayload.Id<RequestWeaponSkillPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "request_weapon_skill"));
    public static final PacketCodec<PacketByteBuf, RequestWeaponSkillPayload> CODEC =
            PacketCodec.unit(new RequestWeaponSkillPayload());
    @Override public CustomPayload.Id<RequestWeaponSkillPayload> getId() { return ID; }
}
