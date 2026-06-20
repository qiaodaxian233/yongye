package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.SkillType;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 技能书(护甲/恢复/闪避/反伤/抗性)效果管理。
 *  - 学习等级存于 LEARNED_SKILLS 映射(跨死亡保留)。
 *  - 护甲走属性修饰符;恢复走定时回血;抗性走状态效果与负面削弱。
 *  - 闪避/反伤由 HighHpCounterHandler 在伤害事件里调用本类的判定。
 */
public final class SkillEffectManager {
    private SkillEffectManager() {}

    public enum LearnResult { OK, CAPPED }

    private static final Identifier ID_ARMOR = Identifier.of(Yongye.MOD_ID, "skill_armor");
    private static final Identifier ID_TOUGH = Identifier.of(Yongye.MOD_ID, "skill_toughness");
    private static final Identifier ID_ATTACK = Identifier.of(Yongye.MOD_ID, "skill_attack");

    private static int tickCounter = 0;

    public static int getLearnedLevel(ServerPlayerEntity player, SkillType type) {
        return player.getAttachedOrElse(ModAttachments.LEARNED_SKILLS, Map.of()).getOrDefault(type.id, 0);
    }

    public static LearnResult learn(ServerPlayerEntity player, SkillType type, int level) {
        int max = YongyeConfig.get().skillBookMaxLevel;
        int cur = getLearnedLevel(player, type);
        if (cur >= max) return LearnResult.CAPPED;
        int next = Math.min(max, cur + Math.max(1, level));
        Map<String, Integer> copy = new HashMap<>(player.getAttachedOrElse(ModAttachments.LEARNED_SKILLS, Map.of()));
        copy.put(type.id, next);
        player.setAttached(ModAttachments.LEARNED_SKILLS, copy);
        applyAttributes(player);
        com.yongye.network.YongyeNet.sendStats(player);
        return LearnResult.OK;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < 20) return; // 每秒一次
            tickCounter = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                applyAttributes(p);
                applyRegen(p);
                applyResistance(p);
                applySatiety(p);
            }
        });
        // 抢夺:命中怪物按等级概率夺走其手持物品,给玩家(背包满则掉落)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) return ActionResult.PASS;
            int lvl = getLearnedLevel(sp, SkillType.STEAL);
            if (lvl <= 0) return ActionResult.PASS;
            ItemStack loot = mob.getMainHandStack();
            if (loot.isEmpty()) return ActionResult.PASS;
            YongyeConfig cfg = YongyeConfig.get();
            double chance = Math.min(cfg.skillStealMaxChance, lvl * cfg.skillStealChancePerLevel);
            if (sp.getRandom().nextDouble() >= chance) return ActionResult.PASS;
            ItemStack stolen = loot.copy();
            mob.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            if (!sp.getInventory().insertStack(stolen) && !stolen.isEmpty()) {
                sp.dropItem(stolen, false);
            }
            sp.sendMessage(Text.literal("抢夺成功!夺得 ").formatted(Formatting.GOLD).append(loot.getName()), true);
            world.playSound(null, mob.getX(), mob.getY(), mob.getZ(),
                    SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
            return ActionResult.PASS;
        });

        Yongye.LOGGER.info("[亡途荒夜] 技能书(护甲/恢复/闪避/反伤/抗性/饱食/抢夺)系统已挂载");
    }

    private static void applyAttributes(ServerPlayerEntity p) {
        int armor = getLearnedLevel(p, SkillType.ARMOR);
        setModifier(p, EntityAttributes.GENERIC_ARMOR, ID_ARMOR, armor * 0.5);
        setModifier(p, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, ID_TOUGH, armor * 0.25);

        int attack = getLearnedLevel(p, SkillType.ATTACK);
        setModifier(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, attack * 0.5);
    }

    private static void setModifier(ServerPlayerEntity p,
                                    RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                    Identifier id, double value) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (value > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(id, value, EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }

    private static void applyRegen(ServerPlayerEntity p) {
        int regen = getLearnedLevel(p, SkillType.REGEN);
        if (regen <= 0) return;
        if (p.getHealth() >= p.getMaxHealth()) return;
        // 禁疗期间不回血
        long until = p.getAttachedOrElse(ModAttachments.NO_HEAL_UNTIL, 0L);
        if (p.getWorld().getTime() < until) return;
        p.heal((float) (regen * 0.1)); // 每秒回 等级×0.1 点
    }

    private static void applyResistance(ServerPlayerEntity p) {
        int res = getLearnedLevel(p, SkillType.RESISTANCE);
        if (res <= 0) return;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, true, false, false));

        // 削弱/清除负面状态
        List<RegistryEntry<net.minecraft.entity.effect.StatusEffect>> harmful = new ArrayList<>();
        for (StatusEffectInstance inst : p.getStatusEffects()) {
            if (inst.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL) {
                harmful.add(inst.getEffectType());
            }
        }
        double clearChance = Math.min(0.8, res * 0.01);
        for (var eff : harmful) {
            if (p.getRandom().nextDouble() < clearChance) {
                p.removeStatusEffect(eff);
            }
        }
    }

    private static void applySatiety(ServerPlayerEntity p) {
        int s = getLearnedLevel(p, SkillType.SATIETY);
        if (s <= 0) return;
        // 饱食度强化:每秒补充饱食度+饱和度(add 自动封顶 饱食度20/饱和度<=饱食度),等级越高越不会饿
        p.getHungerManager().add(Math.max(1, s / 8), 0.8f);
    }

    /** 闪避:返回 true 表示本次伤害被完全闪避(上限 50%)。 */
    public static boolean rollEvasion(ServerPlayerEntity p) {
        int ev = getLearnedLevel(p, SkillType.EVASION);
        if (ev <= 0) return false;
        double chance = Math.min(0.5, ev * 0.01);
        return p.getRandom().nextDouble() < chance;
    }

    /** 反伤系数:受到的伤害 × 此系数 反弹给攻击者(0 表示无)。 */
    public static double thornsFactor(ServerPlayerEntity p) {
        int th = getLearnedLevel(p, SkillType.THORNS);
        if (th <= 0) return 0.0;
        return Math.min(3.0, th * 0.05);
    }
}
