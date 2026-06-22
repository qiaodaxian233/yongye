package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * S2C:把当前可编辑配置(爆率/经验等)的值回传客户端,供「爆率编辑器」预填输入框。
 *  - data 格式:每行 "key=value",行间用 \n 分隔;客户端按行解析。
 *  - {@link #EDITABLE_KEYS} 是服务端/客户端**共用**的可编辑字段清单(同一份 jar,定义在此处单一来源):
 *    服务端据此读取并填充 data;客户端据此渲染输入框(标签用 LABELS,缺失则用 key 原文)。
 */
public record ConfigValuesPayload(String data) implements CustomPayload {

    public static final CustomPayload.Id<ConfigValuesPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "config_values"));

    public static final PacketCodec<PacketByteBuf, ConfigValuesPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeString(v.data),
            buf -> new ConfigValuesPayload(buf.readString())
    );

    /** 爆率编辑器里可直接输入修改的字段(顺序即显示顺序)。要加减字段只改这一处。 */
    public static final String[] EDITABLE_KEYS = new String[]{
            "lifeShardDropChance",
            "lifeCrystalDropChance",
            "lifeCoreDropChance",
            "bloodCoreDropChanceElite",
            "skillBookDropChanceElite",
            "skillBookDropChanceNormal",
            "classBookDropChance",
            "classWeaponDropChanceElite",
            "eliteChance",
            "mobBossChance",
            "xpBonusElite",
            "xpBonusMobBoss",
            "xpBonusVanillaBoss",
            "xpBonusPain",
    };

    /** 字段 → 中文标签(客户端显示用);未列出的字段直接显示 key 原文。 */
    public static String labelOf(String key) {
        return switch (key) {
            case "lifeShardDropChance" -> "生命碎片爆率";
            case "lifeCrystalDropChance" -> "生命结晶爆率";
            case "lifeCoreDropChance" -> "生命核心爆率(精英)";
            case "bloodCoreDropChanceElite" -> "灾厄血核爆率(精英)";
            case "skillBookDropChanceElite" -> "技能书爆率(精英)";
            case "skillBookDropChanceNormal" -> "技能书爆率(普通)";
            case "classBookDropChance" -> "职业书爆率(精英)";
            case "classWeaponDropChanceElite" -> "职业武器爆率(精英)";
            case "eliteChance" -> "精英化概率";
            case "mobBossChance" -> "怪物BOSS化概率";
            case "xpBonusElite" -> "精英额外经验";
            case "xpBonusMobBoss" -> "怪物BOSS额外经验";
            case "xpBonusVanillaBoss" -> "原版Boss额外经验";
            case "xpBonusPain" -> "长门额外经验";
            default -> key;
        };
    }

    @Override
    public CustomPayload.Id<ConfigValuesPayload> getId() {
        return ID;
    }
}
