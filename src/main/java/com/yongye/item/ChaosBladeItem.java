package com.yongye.item;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.List;

/**
 * 混沌之刃:专属传说武器(非靠强化获得)。固定高基础属性,且三大主动技能
 * (混沌斩 / 深渊吞噬 / 终焉降临)无需品质解锁即可施放(由 WeaponSkillManager 特判)。
 * 仍可继续用材料强化以叠更高属性与品质。
 */
public class ChaosBladeItem extends Item {

    public ChaosBladeItem(Settings settings) {
        super(settings);
    }

    /** 像剑一样快速破坏蜘蛛网。1.21.x 把 1.20 的 getMiningSpeedMultiplier 重命名为 getMiningSpeed(已核实 yarn 1.21.1)。 */
    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0F;
        return super.getMiningSpeed(stack, state);
    }

    /** 固定基础属性:攻击力约 +30,攻速较快。用基础修饰符 ID 让 tooltip 显示为总值。 */
    public static AttributeModifiersComponent baseAttributes() {
        return AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                        new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, 29.0,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED,
                        new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID, -1.6,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .build();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("来自混沌深渊的终极武器,掌控毁灭与秩序的力量。").formatted(Formatting.DARK_PURPLE));
        tooltip.add(Text.literal("✦ 神器技能(无需解锁):").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.literal("  混沌斩 · 深渊吞噬 · 终焉降临").formatted(Formatting.WHITE));
        tooltip.add(Text.literal("  按 R / G / V 施放").formatted(Formatting.GRAY));
    }
}
