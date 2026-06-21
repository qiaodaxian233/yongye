package com.yongye.item;

import com.yongye.Yongye;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 坦克专属盾·磐盾(m44)。继承原版 ShieldItem,自带举盾格挡;
 * 副手装备时给予强防御属性,坦克持盾另有护盾强化与格挡反震(见 ClassSkillHandler)。
 * 与主手「镇魂」成套(锤+盾)。
 */
public class TankShieldItem extends ShieldItem {

    public TankShieldItem(Settings settings) {
        super(settings);
    }

    private static Identifier id(String s) {
        return Identifier.of(Yongye.MOD_ID, s);
    }

    /** 副手槽生效:装备(副手)时即加成。 */
    public static AttributeModifiersComponent offhandAttributes() {
        AttributeModifierSlot O = AttributeModifierSlot.OFFHAND;
        return AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ARMOR,
                        new EntityAttributeModifier(id("shield_tank_armor"), 6.0, Operation.ADD_VALUE), O)
                .add(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                        new EntityAttributeModifier(id("shield_tank_tough"), 4.0, Operation.ADD_VALUE), O)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                        new EntityAttributeModifier(id("shield_tank_kbr"), 0.3, Operation.ADD_VALUE), O)
                .add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(id("shield_tank_hp"), 10.0, Operation.ADD_VALUE), O)
                .build();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("职业专属 · 【肉盾】磐盾").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("立于阵前,为众人挡下灾厄。").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("✦ 坦克副手装备时:").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("  护盾(吸收)+1 级 · 格挡时反震伤害").formatted(Formatting.WHITE));
    }
}
