package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.WeaponQuality;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItems;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

/**
 * 装备无限强化:把武器/盔甲拿材料一直喂,强化等级无上限;品质(普通→至尊)由等级换算。
 * 加成绑在物品上(跟随物品)。每次重算从「物品默认属性」起算,避免重复叠加。
 *   武器:+攻击力、+攻击速度(按品质)、+暴击率(按品质,见 WeaponCombatHandler)、+耐久
 *   盔甲:+护甲、+韧性、+最大生命、+耐久
 */
public final class EquipmentEnhancer {
    private EquipmentEnhancer() {}

    private static final Identifier DMG_ID = Identifier.of(Yongye.MOD_ID, "enhance_damage");
    private static final Identifier SPD_ID = Identifier.of(Yongye.MOD_ID, "enhance_speed");
    private static final Identifier ARMOR_ID = Identifier.of(Yongye.MOD_ID, "enhance_armor");
    private static final Identifier TOUGH_ID = Identifier.of(Yongye.MOD_ID, "enhance_toughness");
    private static final Identifier HP_ID = Identifier.of(Yongye.MOD_ID, "enhance_health");

    public enum Kind { WEAPON, ARMOR, NONE }

    public static Kind kindOf(Item item) {
        AttributeModifiersComponent def = new ItemStack(item)
                .getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        boolean hasArmor = false, hasDmg = false;
        for (AttributeModifiersComponent.Entry e : def.modifiers()) {
            if (e.attribute().equals(EntityAttributes.GENERIC_ARMOR)) hasArmor = true;
            if (e.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) hasDmg = true;
        }
        if (hasArmor) return Kind.ARMOR;
        if (hasDmg) return Kind.WEAPON;
        return Kind.NONE;
    }

    public static boolean isEnhanceable(Item item) {
        return YongyeConfig.get().enableEquipmentEnhance && kindOf(item) != Kind.NONE;
    }

    public static boolean isWeapon(ItemStack stack) {
        return !stack.isEmpty() && kindOf(stack.getItem()) == Kind.WEAPON;
    }

    public static int getLevel(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ENHANCE_LEVEL, 0);
    }

    public static WeaponQuality qualityOf(ItemStack stack) {
        return WeaponQuality.forLevel(getLevel(stack));
    }

    /** 武器当前暴击额外伤害(数值);非武器或未强化返回 0。 */
    public static float critBonusDamage(ItemStack stack) {
        if (!isWeapon(stack)) return 0f;
        int lvl = getLevel(stack);
        if (lvl <= 0) return 0f;
        YongyeConfig c = YongyeConfig.get();
        return (float) (lvl * c.enhanceDamagePerLevel * c.enhanceCritBonusMultiplier);
    }

    public static int materialValue(Item item) {
        YongyeConfig c = YongyeConfig.get();
        if (item == ModItems.LIFE_SHARD) return c.enhanceShardValue;
        if (item == ModItems.LIFE_CRYSTAL) return c.enhanceCrystalValue;
        if (item == ModItems.LIFE_CORE) return c.enhanceCoreValue;
        if (item == ModItems.CATASTROPHE_BLOOD_CORE) return c.enhanceBloodCoreValue;
        return 0;
    }

    public static boolean isMaterial(Item item) {
        return materialValue(item) > 0;
    }

    /** 复制原装备,设为指定强化等级并重算属性/耐久,返回新 stack(数量 1)。 */
    public static ItemStack withLevel(ItemStack equipment, int level) {
        ItemStack out = equipment.copy();
        out.setCount(1);
        if (level < 0) level = 0;
        out.set(ModComponents.ENHANCE_LEVEL, level);

        Item item = out.getItem();
        Kind kind = kindOf(item);
        YongyeConfig c = YongyeConfig.get();
        WeaponQuality q = WeaponQuality.forLevel(level);

        ItemStack pristine = new ItemStack(item);

        // 从物品默认属性起算(不在已强化基础上继续叠)
        AttributeModifiersComponent result = pristine
                .getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);

        if (level > 0) {
            if (kind == Kind.WEAPON) {
                result = result.with(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                        new EntityAttributeModifier(DMG_ID, level * c.enhanceDamagePerLevel,
                                EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND);
                if (q.attackSpeed > 0) {
                    result = result.with(EntityAttributes.GENERIC_ATTACK_SPEED,
                            new EntityAttributeModifier(SPD_ID, q.attackSpeed,
                                    EntityAttributeModifier.Operation.ADD_VALUE),
                            AttributeModifierSlot.MAINHAND);
                }
            } else if (kind == Kind.ARMOR) {
                AttributeModifierSlot slot = armorSlotOf(result);
                result = result
                        .with(EntityAttributes.GENERIC_ARMOR,
                                new EntityAttributeModifier(ARMOR_ID, level * c.enhanceArmorPerLevel,
                                        EntityAttributeModifier.Operation.ADD_VALUE), slot)
                        .with(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                                new EntityAttributeModifier(TOUGH_ID, level * c.enhanceToughnessPerLevel,
                                        EntityAttributeModifier.Operation.ADD_VALUE), slot)
                        .with(EntityAttributes.GENERIC_MAX_HEALTH,
                                new EntityAttributeModifier(HP_ID, level * c.enhanceHealthPerLevel,
                                        EntityAttributeModifier.Operation.ADD_VALUE), slot);
            }
        }
        out.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, result);

        // 耐久:按等级提升上限(从原始最大耐久起算),并修满
        if (level > 0 && pristine.isDamageable()) {
            int baseMax = pristine.getMaxDamage();
            int newMax = baseMax + level * c.enhanceDurabilityPerLevel;
            int curDamage = out.getDamage(); // 保留已损耗,不免费修复
            out.set(DataComponentTypes.MAX_DAMAGE, newMax);
            out.set(DataComponentTypes.DAMAGE, Math.min(curDamage, newMax - 1));
        }
        return out;
    }

    public static ItemStack addLevels(ItemStack equipment, int delta) {
        return withLevel(equipment, getLevel(equipment) + delta);
    }

    private static AttributeModifierSlot armorSlotOf(AttributeModifiersComponent base) {
        for (AttributeModifiersComponent.Entry e : base.modifiers()) {
            if (e.attribute().equals(EntityAttributes.GENERIC_ARMOR)) return e.slot();
        }
        return AttributeModifierSlot.ARMOR;
    }
}
