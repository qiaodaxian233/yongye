package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:技能冷却剩余 tick(m346,技能 CD 常显 HUD)。
 * 五个槽位与按键一一对应:武器技能 R/G/V(WeaponSkillManager)、大招 X(ClassUltimateManager)、
 * 小技能 C(ClassMinorSkillManager);三套冷却 m321 起统一 server.getTicks() 时基,此处读成剩余量下发。
 * 全 0=全就绪;SkillCdSyncHandler 每 10 tick 发一次,全就绪期间静默(见其边沿补零逻辑),
 * 客户端本地每 tick 递减保平滑(HUD 秒数不跳格)。
 */
public record SkillCdPayload(int slash, int devour, int finality, int ultimate, int minor) implements CustomPayload {

    public static final CustomPayload.Id<SkillCdPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "skill_cd"));

    public static final PacketCodec<PacketByteBuf, SkillCdPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarInt(value.slash);
                buf.writeVarInt(value.devour);
                buf.writeVarInt(value.finality);
                buf.writeVarInt(value.ultimate);
                buf.writeVarInt(value.minor);
            },
            buf -> new SkillCdPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
