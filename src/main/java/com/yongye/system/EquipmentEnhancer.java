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

    public enum Kind { WEAPON, ARMOR, HYBRID, NONE }

    public static Kind kindOf(Item item) {
        AttributeModifiersComponent def = new ItemStack(item)
                .getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT);
        boolean hasArmor = false, hasDmg = false;
        for (AttributeModifiersComponent.Entry e : def.modifiers()) {
            if (e.attribute().equals(EntityAttributes.GENERIC_ARMOR)) hasArmor = true;
            if (e.attribute().equals(EntityAttributes.GENERIC_ATTACK_DAMAGE)) hasDmg = true;
        }
        if (hasDmg && hasArmor) return Kind.HYBRID; // 攻击+护甲兼具(如坦克武器镇魂):攻防双修强化,攻击打折
        if (hasArmor) return Kind.ARMOR;
        if (hasDmg) return Kind.WEAPON;
        if (item instanceof net.minecraft.item.ArmorItem) return Kind.ARMOR; // 兜底:属性组件没暴露 generic.armor 的盔甲
        if (item instanceof net.minecraft.item.ElytraItem) return Kind.ARMOR; // 鞘翅/永夜之翼:无属性,归护甲类可强化(强化加生命/护甲)
        return Kind.NONE;
    }

    public static boolean isEnhanceable(Item item) {
        return YongyeConfig.get().enableEquipmentEnhance && kindOf(item) != Kind.NONE;
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Kind k = kindOf(stack.getItem());
        return k == Kind.WEAPON || k == Kind.HYBRID; // hybrid(攻防双修)也算武器,可暴击、走武器结算
    }

    /**
     * 是否「可被守护」(守护附魔书的施加门槛)。
     * 凡是会被精英缴械夺走、或玩家有投入想保护的随身装备都算:武器(含攻防双修)、盔甲、盾牌。
     * 盾牌:自定义磐盾因带 GENERIC_ARMOR 已归 ARMOR;原版盾无属性会归 NONE,故额外用 instanceof ShieldItem 显式放行。
     */
    public static boolean isWardable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Kind k = kindOf(stack.getItem());
        if (k == Kind.WEAPON || k == Kind.HYBRID || k == Kind.ARMOR) return true;
        return stack.getItem() instanceof net.minecraft.item.ShieldItem; // 兜底:无属性的原版盾
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
        // hybrid 武器攻击成长被打折,暴击额外伤害同比例缩减
        double dmgPerLvl = c.enhanceDamagePerLevel
                * (kindOf(stack.getItem()) == Kind.HYBRID ? c.enhanceHybridDamageFraction : 1.0);
        return (float) (lvl * dmgPerLvl * c.enhanceCritBonusMultiplier);
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
        // 强化过的装备无法因耐久损坏(保护玩家投入;被夺/夺回也不会被打坏)
        if (level > 0) {
            out.set(DataComponentTypes.UNBREAKABLE, new net.minecraft.component.type.UnbreakableComponent(false));
        }

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
            } else if (kind == Kind.HYBRID) {
                // 攻防双修(如坦克武器镇魂):攻击按 enhanceHybridDamageFraction 打折,
                // 护甲/韧性/生命照盔甲成长一起涨;全部挂主手槽(武器在手才生效)。
                AttributeModifierSlot M = AttributeModifierSlot.MAINHAND;
                result = result
                        .with(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                                new EntityAttributeModifier(DMG_ID,
                                        level * c.enhanceDamagePerLevel * c.enhanceHybridDamageFraction,
                                        EntityAttributeModifier.Operation.ADD_VALUE), M)
                        .with(EntityAttributes.GENERIC_ARMOR,
                                new EntityAttributeModifier(ARMOR_ID, level * c.enhanceArmorPerLevel,
                                        EntityAttributeModifier.Operation.ADD_VALUE), M)
                        .with(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                                new EntityAttributeModifier(TOUGH_ID, level * c.enhanceToughnessPerLevel,
                                        EntityAttributeModifier.Operation.ADD_VALUE), M)
                        .with(EntityAttributes.GENERIC_MAX_HEALTH,
                                new EntityAttributeModifier(HP_ID, level * c.enhanceHealthPerLevel,
                                        EntityAttributeModifier.Operation.ADD_VALUE), M);
                if (q.attackSpeed > 0) {
                    result = result.with(EntityAttributes.GENERIC_ATTACK_SPEED,
                            new EntityAttributeModifier(SPD_ID, q.attackSpeed,
                                    EntityAttributeModifier.Operation.ADD_VALUE), M);
                }
            } else if (kind == Kind.ARMOR) {
                AttributeModifierSlot slot = slotForItem(pristine.getItem(), result);
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

    /**
     * 把装备从 fromLevel 升到 fromLevel+1 的成功率(m158)。
     *   level < enhanceFailStartLevel(默认1000):必成功(返回 1.0);
     *   [start, end] 之间:从 100% 线性降到 enhanceFailEndRate(默认10%);
     *   ≥ enhanceFailEndLevel:封底为 enhanceFailEndRate。
     * 总开关 enableEnhanceFailure 关闭时恒为 1.0。
     */
    public static double successRate(int level) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableEnhanceFailure) return 1.0;
        if (level < c.enhanceFailStartLevel) return 1.0;
        int span = c.enhanceFailEndLevel - c.enhanceFailStartLevel;
        if (span <= 0) return c.enhanceFailEndRate;
        double t = (double) (level - c.enhanceFailStartLevel) / span;
        if (t > 1.0) t = 1.0;
        double rate = 1.0 - t * (1.0 - c.enhanceFailEndRate);
        return Math.max(c.enhanceFailEndRate, Math.min(1.0, rate));
    }

    /** attempt() 的结果。stack 为最终装备(碎裂时为 ItemStack.EMPTY)。 */
    public static final class EnhanceResult {
        public final ItemStack stack;
        public final int startLevel, endLevel, succeeded, failed;
        public final boolean broke;        // 装备是否碎裂(销毁)
        public final boolean usedProtect;  // 本次是否消耗保护卷挡下了一次碎裂
        EnhanceResult(ItemStack stack, int startLevel, int endLevel,
                      int succeeded, int failed, boolean broke, boolean usedProtect) {
            this.stack = stack; this.startLevel = startLevel; this.endLevel = endLevel;
            this.succeeded = succeeded; this.failed = failed; this.broke = broke; this.usedProtect = usedProtect;
        }
    }

    /**
     * 逐级尝试强化 budget 次(m158)。失败只消耗本次预算、等级不变;等级 ≥ enhanceBreakLevel 的失败
     * 有 enhanceBreakChance 概率令装备碎裂(stack=EMPTY、broke=true),除非玩家持有生效中的保护卷
     * (ENHANCE_PROTECTED)——则消耗保护、挡下这次碎裂(usedProtect=true)。
     * p 可为 null(无保护判定,用独立随机源)。材料由调用方在调用前一次性扣除。
     */
    public static EnhanceResult attempt(net.minecraft.server.network.ServerPlayerEntity p,
                                        ItemStack equipment, int budget) {
        YongyeConfig c = YongyeConfig.get();
        int startLevel = getLevel(equipment);
        if (budget < 0) budget = 0;
        if (!c.enableEnhanceFailure) {
            int end = startLevel + budget;
            return new EnhanceResult(withLevel(equipment, end), startLevel, end, budget, 0, false, false);
        }
        net.minecraft.util.math.random.Random rng =
                (p != null) ? p.getRandom() : net.minecraft.util.math.random.Random.create();
        int level = startLevel, ok = 0, fail = 0, remain = budget;
        boolean broke = false, usedProtect = false;
        // 安全段(必成功)批量推进,避免大量 RNG 空转
        if (level < c.enhanceFailStartLevel && remain > 0) {
            int bulk = Math.min(remain, c.enhanceFailStartLevel - level);
            level += bulk; remain -= bulk; ok += bulk;
        }
        while (remain > 0 && !broke) {
            remain--;
            if (rng.nextDouble() < successRate(level)) {
                level++; ok++;
            } else {
                fail++;
                if (level >= c.enhanceBreakLevel) {
                    boolean prot = (p != null)
                            && p.getAttachedOrElse(com.yongye.registry.ModAttachments.ENHANCE_PROTECTED, false);
                    if (prot) {
                        p.setAttached(com.yongye.registry.ModAttachments.ENHANCE_PROTECTED, false);
                        usedProtect = true;
                    } else if (rng.nextDouble() < c.enhanceBreakChance) {
                        broke = true;
                    }
                }
            }
        }
        ItemStack out = broke ? ItemStack.EMPTY : withLevel(equipment, level);
        return new EnhanceResult(out, startLevel, level, ok, fail, broke, usedProtect);
    }


    private static AttributeModifierSlot armorSlotOf(AttributeModifiersComponent base) {
        for (AttributeModifiersComponent.Entry e : base.modifiers()) {
            if (e.attribute().equals(EntityAttributes.GENERIC_ARMOR)) return e.slot();
        }
        return AttributeModifierSlot.ARMOR;
    }

    /** 鞘翅/永夜之翼专用:强化属性绑定到 CHEST 槽(背饰穿胸甲槽,确保生效)。 */
    private static AttributeModifierSlot slotForItem(Item item, AttributeModifiersComponent base) {
        if (item instanceof net.minecraft.item.ElytraItem) return AttributeModifierSlot.CHEST;
        return armorSlotOf(base);
    }

    /** 背包里所有强化材料合计能提供的强化级数(数量×单值)。 */
    public static int totalMaterialLevels(net.minecraft.entity.player.PlayerInventory inv) {
        int sum = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && isMaterial(s.getItem())) sum += s.getCount() * materialValue(s.getItem());
        }
        return sum;
    }

    /**
     * 一键强化:对玩家背包第 slot 件装备,用背包里「全部」强化材料升级(级数 = 各材料 数量×单值 之和),
     * 并清空所用材料。由 EnhanceSelectPayload 接收器调用,服务端权威。
     */
    public static void enhanceFromInventory(net.minecraft.server.network.ServerPlayerEntity p, int slot) {
        net.minecraft.entity.player.PlayerInventory inv = p.getInventory();
        if (slot < 0 || slot >= inv.size()) return;
        ItemStack target = inv.getStack(slot);
        if (target.isEmpty() || !isEnhanceable(target.getItem())) {
            p.sendMessage(net.minecraft.text.Text.literal("该物品不可强化").formatted(net.minecraft.util.Formatting.RED), true);
            return;
        }
        int add = totalMaterialLevels(inv);
        if (add <= 0) {
            p.sendMessage(net.minecraft.text.Text.literal("背包里没有强化材料(生命碎片/结晶/核心/血核)")
                    .formatted(net.minecraft.util.Formatting.YELLOW), true);
            return;
        }
        // 扣掉所有强化材料
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && isMaterial(s.getItem())) inv.setStack(i, ItemStack.EMPTY);
        }
        EnhanceResult res = attempt(p, target, add);
        if (res.broke) {
            inv.setStack(slot, ItemStack.EMPTY);
            p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    net.minecraft.sound.SoundEvents.ENTITY_ITEM_BREAK,
                    net.minecraft.sound.SoundCategory.PLAYERS, 1.0f, 0.7f);
            p.sendMessage(net.minecraft.text.Text.literal(
                    "强化失败!装备在 Lv." + res.startLevel + " 时碎裂了(成功 " + res.succeeded
                    + " / 失败 " + res.failed + ")")
                    .formatted(net.minecraft.util.Formatting.DARK_RED), true);
            return;
        }
        inv.setStack(slot, res.stack);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                net.minecraft.sound.SoundEvents.BLOCK_ANVIL_USE,
                net.minecraft.sound.SoundCategory.PLAYERS, 0.7f, 1.2f);
        StringBuilder msg = new StringBuilder("强化:成功 " + res.succeeded + " 级");
        if (res.failed > 0) msg.append(" / 失败 ").append(res.failed).append(" 次");
        msg.append(",当前 Lv.").append(res.endLevel).append("(").append(qualityOf(res.stack).cn).append(")");
        if (res.usedProtect) msg.append(" [保护卷已抵挡碎裂]");
        p.sendMessage(net.minecraft.text.Text.literal(msg.toString())
                .formatted(net.minecraft.util.Formatting.GOLD), true);
    }
}
