package com.yongye.item;

import com.yongye.Yongye;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * 职业专属武器(m42)。强力差异化基础属性;手持且对应职业生效时,
 * 由 ClassSkillHandler 强化该职业的签名技能(协同)。武僧那把是「拳套」,
 * 手持时仍按空手计、可触发连击。普通玩家也能拿来当高属性武器用,但吃不到专属协同。
 */
public class ClassWeaponItem extends Item {

    public final PlayerClass playerClass;

    public ClassWeaponItem(PlayerClass playerClass, Settings settings) {
        super(settings);
        this.playerClass = playerClass;
    }

    /** 主手持有该职业专属武器? */
    public static boolean held(PlayerEntity p, PlayerClass c) {
        return p.getMainHandStack().getItem() instanceof ClassWeaponItem w && w.playerClass == c;
    }

    private static Identifier id(String s) {
        return Identifier.of(Yongye.MOD_ID, s);
    }

    private static EntityAttributeModifier baseAtk(double v) {
        return new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, v, Operation.ADD_VALUE);
    }
    private static EntityAttributeModifier baseAspd(double v) {
        return new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID, v, Operation.ADD_VALUE);
    }

    public static AttributeModifiersComponent baseAttributes(PlayerClass c) {
        AttributeModifiersComponent.Builder b = AttributeModifiersComponent.builder();
        AttributeModifierSlot M = AttributeModifierSlot.MAINHAND;
        switch (c) {
            case WARRIOR -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(14.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-2.8), M);
                b.add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(id("weapon_warrior_hp"), 8.0, Operation.ADD_VALUE), M);
            }
            case TANK -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(9.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-3.0), M);
                b.add(EntityAttributes.GENERIC_ARMOR,
                        new EntityAttributeModifier(id("weapon_tank_armor"), 6.0, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                        new EntityAttributeModifier(id("weapon_tank_kbr"), 0.4, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(id("weapon_tank_hp"), 14.0, Operation.ADD_VALUE), M);
            }
            case ASSASSIN -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(8.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(0.5), M);
                b.add(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                        new EntityAttributeModifier(id("weapon_assassin_speed"), 0.05, Operation.ADD_MULTIPLIED_BASE), M);
            }
            case WARLOCK -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(11.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-2.0), M);
                b.add(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
                        new EntityAttributeModifier(id("weapon_warlock_reach"), 3.0, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_LUCK,
                        new EntityAttributeModifier(id("weapon_warlock_luck"), 3.0, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(id("weapon_warlock_hp"), -4.0, Operation.ADD_VALUE), M);
            }
            case MONK -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(7.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(1.2), M);
                b.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                        new EntityAttributeModifier(id("weapon_monk_kbr"), 0.3, Operation.ADD_VALUE), M);
            }
            case SWORDSMAN -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(12.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-1.0), M);
                b.add(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
                        new EntityAttributeModifier(id("weapon_swordsman_reach"), 1.0, Operation.ADD_VALUE), M);
            }
        }
        return b.build();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("职业专属 · 【" + playerClass.cn + "】").formatted(Formatting.GOLD));
        tooltip.add(flavor(playerClass).formatted(Formatting.GRAY));
        tooltip.add(Text.literal("✦ 手持且本职业生效时强化:").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(synergy(playerClass).formatted(Formatting.WHITE));
    }

    private static MutableText flavor(PlayerClass c) {
        return Text.literal(switch (c) {
            case WARRIOR -> "巨阙 —— 重劈裂阵,愈战愈勇。";
            case TANK -> "镇魂 —— 以血肉筑墙,挡在众人之前。";
            case ASSASSIN -> "影刺 —— 出于暗,归于暗。";
            case WARLOCK -> "噬魂杖 —— 以命为薪,焚尽周遭。";
            case MONK -> "鬼神拳套 —— 拳意不绝,连环不止。";
            case SWORDSMAN -> "流光 —— 剑气纵横,格挡即反击。";
        });
    }

    private static MutableText synergy(PlayerClass c) {
        return Text.literal(switch (c) {
            case WARRIOR -> "  斩杀阈值提升、吸血翻倍";
            case TANK -> "  护盾+1级、嘲讽范围扩大";
            case ASSASSIN -> "  背刺伤害翻倍、闪避几率提升";
            case WARLOCK -> "  范围更大、伤害更高、耗血更少";
            case MONK -> "  视为空手可连击、连击更狠";
            case SWORDSMAN -> "  剑气更广更痛、格挡反击增强";
        });
    }
}
