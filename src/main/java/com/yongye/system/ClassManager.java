package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.PlayerClass;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** 职业系统:属性修饰、等级门控(降级失去)、武僧拳意叠加、刺客夜视。 */
public final class ClassManager {
    private ClassManager() {}

    private record AttrSlot(RegistryEntry<EntityAttribute> attr, String key) {}
    private static final AttrSlot[] ATTRS = {
            new AttrSlot(EntityAttributes.GENERIC_MAX_HEALTH, "hp"),       // 0
            new AttrSlot(EntityAttributes.GENERIC_ARMOR, "armor"),        // 1
            new AttrSlot(EntityAttributes.GENERIC_ATTACK_DAMAGE, "atk"),  // 2
            new AttrSlot(EntityAttributes.GENERIC_MOVEMENT_SPEED, "speed"),// 3
            new AttrSlot(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, "breach"),  // 4
            new AttrSlot(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, "ereach"), // 5
    };
    private record CMod(int idx, double value, Operation op) {}

    private static List<CMod> mods(PlayerClass c) {
        return switch (c) {
            case TANK -> List.of(new CMod(0, 20, Operation.ADD_VALUE), new CMod(1, 8, Operation.ADD_VALUE),
                    new CMod(2, -0.3, Operation.ADD_MULTIPLIED_TOTAL), new CMod(3, -0.15, Operation.ADD_MULTIPLIED_BASE));
            case WARRIOR -> List.of(new CMod(0, 10, Operation.ADD_VALUE), new CMod(1, 3, Operation.ADD_VALUE),
                    new CMod(2, 1, Operation.ADD_VALUE));
            case WARLOCK -> List.of(new CMod(0, -10, Operation.ADD_VALUE), new CMod(4, 2.5, Operation.ADD_VALUE),
                    new CMod(5, 1, Operation.ADD_VALUE), new CMod(2, 1, Operation.ADD_VALUE));
            case SWORDSMAN -> List.of(new CMod(2, 4, Operation.ADD_VALUE), new CMod(1, -2, Operation.ADD_VALUE));
            case MONK -> List.of(new CMod(0, 10, Operation.ADD_VALUE), new CMod(1, 5, Operation.ADD_VALUE),
                    new CMod(4, 1.5, Operation.ADD_VALUE), new CMod(5, 1.5, Operation.ADD_VALUE),
                    new CMod(3, -0.1, Operation.ADD_MULTIPLIED_BASE));
            case ASSASSIN -> List.of(new CMod(3, 0.2, Operation.ADD_MULTIPLIED_BASE), new CMod(1, -3, Operation.ADD_VALUE),
                    new CMod(2, 2, Operation.ADD_VALUE));
        };
    }

    private static Identifier idOf(PlayerClass c, String key) {
        return Identifier.of(Yongye.MOD_ID, "class_" + c.id + "_" + key);
    }
    private static final Identifier MONK_FIST_ID = Identifier.of(Yongye.MOD_ID, "class_monk_fist");

    public static List<String> learnedList(ServerPlayerEntity p) {
        return new ArrayList<>(p.getAttachedOrElse(ModAttachments.LEARNED_CLASSES, List.of()));
    }

    /** 纯查询:该职业是否已习得且当前等级达标(无副作用,供技能系统判定)。 */
    public static boolean isActive(ServerPlayerEntity p, PlayerClass c) {
        List<String> learned = learnedList(p);
        int idx = learned.indexOf(c.id);
        if (idx < 0) return false;
        YongyeConfig cfg = YongyeConfig.get();
        int need = idx == 0 ? cfg.classLevel1 : cfg.classLevel2;
        return p.experienceLevel >= need;
    }

    public static boolean learn(ServerPlayerEntity p, PlayerClass type) {
        YongyeConfig cfg = YongyeConfig.get();
        List<String> learned = learnedList(p);
        if (learned.contains(type.id)) {
            p.sendMessage(Text.literal("你已经学过【" + type.cn + "】了").formatted(Formatting.YELLOW), true);
            return false;
        }
        if (learned.size() >= 2) {
            p.sendMessage(Text.literal("最多只能拥有 2 个职业").formatted(Formatting.RED), true);
            return false;
        }
        int need = learned.isEmpty() ? cfg.classLevel1 : cfg.classLevel2;
        if (p.experienceLevel < need) {
            p.sendMessage(Text.literal("需要等级 " + need + " 才能习得(当前 " + p.experienceLevel + ")").formatted(Formatting.RED), true);
            return false;
        }
        learned.add(type.id);
        p.setAttached(ModAttachments.LEARNED_CLASSES, learned);
        applyClasses(p);
        p.sendMessage(Text.literal("习得职业【" + type.cn + "】!").formatted(Formatting.GOLD), false);
        return true;
    }

    private static List<PlayerClass> enforceAndGet(ServerPlayerEntity p) {
        YongyeConfig cfg = YongyeConfig.get();
        List<String> learned = learnedList(p);
        int lvl = p.experienceLevel;
        boolean changed = false;
        while (learned.size() >= 2 && lvl < cfg.classLevel2) {
            PlayerClass lost = PlayerClass.byId(learned.remove(learned.size() - 1));
            changed = true;
            p.sendMessage(Text.literal("等级跌破 " + cfg.classLevel2 + ",失去职业【" + (lost != null ? lost.cn : "?") + "】").formatted(Formatting.DARK_RED), false);
        }
        while (!learned.isEmpty() && lvl < cfg.classLevel1) {
            PlayerClass lost = PlayerClass.byId(learned.remove(learned.size() - 1));
            changed = true;
            p.sendMessage(Text.literal("等级跌破 " + cfg.classLevel1 + ",失去职业【" + (lost != null ? lost.cn : "?") + "】").formatted(Formatting.DARK_RED), false);
        }
        if (changed) p.setAttached(ModAttachments.LEARNED_CLASSES, learned);
        List<PlayerClass> active = new ArrayList<>();
        for (String id : learned) {
            PlayerClass c = PlayerClass.byId(id);
            if (c != null) active.add(c);
        }
        return active;
    }

    public static void applyClasses(ServerPlayerEntity p) {
        // 清除所有职业修饰符
        for (PlayerClass c : PlayerClass.values()) {
            for (CMod m : mods(c)) {
                EntityAttributeInstance inst = p.getAttributeInstance(ATTRS[m.idx].attr());
                if (inst != null) inst.removeModifier(idOf(c, ATTRS[m.idx].key()));
            }
        }
        EntityAttributeInstance atk = p.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (atk != null) atk.removeModifier(MONK_FIST_ID);

        List<PlayerClass> active = enforceAndGet(p);
        for (PlayerClass c : active) {
            for (CMod m : mods(c)) {
                EntityAttributeInstance inst = p.getAttributeInstance(ATTRS[m.idx].attr());
                if (inst == null) continue;
                Identifier id = idOf(c, ATTRS[m.idx].key());
                inst.removeModifier(id);
                inst.addTemporaryModifier(new EntityAttributeModifier(id, m.value, m.op));
            }
        }
        // 武僧:空手时叠加拳击伤害
        if (active.contains(PlayerClass.MONK) && p.getMainHandStack().isEmpty() && atk != null) {
            int bonus = p.getAttachedOrElse(ModAttachments.MONK_FIST_BONUS, 0);
            if (bonus > 0) atk.addTemporaryModifier(new EntityAttributeModifier(MONK_FIST_ID, bonus, Operation.ADD_VALUE));
        }
        // 刺客:夜视
        if (active.contains(PlayerClass.ASSASSIN)) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, true, false, false));
        }
        // 天赋:仅对当前生效职业应用其天赋修饰 / 增益
        TalentManager.applyTalents(p, active);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) applyClasses(p);
        });
        // 武僧:空手击杀生物 → 永久 +1 拳击伤害
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
            if (!killer.getMainHandStack().isEmpty()) return;
            if (!killer.getAttachedOrElse(ModAttachments.LEARNED_CLASSES, List.of()).contains(PlayerClass.MONK.id)) return;
            int b = killer.getAttachedOrElse(ModAttachments.MONK_FIST_BONUS, 0) + 1;
            killer.setAttached(ModAttachments.MONK_FIST_BONUS, b);
            killer.sendMessage(Text.literal("拳意 +1(当前 +" + b + " 拳击伤害)").formatted(Formatting.GOLD), true);
        });
        Yongye.LOGGER.info("[亡途荒夜] 职业系统已挂载");
    }
}
