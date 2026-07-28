package com.yongye.item;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;

/**
 * m331:龙魂之刃——杀龙的数值意义(作者拍板方案②)。龙魂+夜蚀锭+混沌之刃锻成的终极武器:
 * 基础攻击约 +64(混沌之刃两倍档)、攻速更快、自带最大生命 +40;继承混沌之刃的三大主动技能
 * 免解锁特判(WeaponSkillManager 已加)与破蛛网;仍可无限强化继续叠。
 */
public class DragonBladeItem extends ChaosBladeItem {
    public DragonBladeItem(Settings settings) { super(settings); }

    /** 固定基础属性:攻击 +63(总显示 64)、攻速 -1.4(快于混沌)、最大生命 +40。 */
    public static AttributeModifiersComponent dragonAttributes() {
        return AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                        new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, 63.0,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED,
                        new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID, -1.4,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(net.minecraft.util.Identifier.of(com.yongye.Yongye.MOD_ID, "dragon_blade_health"), 40.0,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .build();
    }
}
