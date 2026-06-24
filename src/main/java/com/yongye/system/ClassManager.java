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
    private static final Identifier MONK_HP_ID = Identifier.of(Yongye.MOD_ID, "class_monk_hp");

    public static List<String> learnedList(ServerPlayerEntity p) {
        return new ArrayList<>(p.getAttachedOrElse(ModAttachments.LEARNED_CLASSES, List.of()));
    }

    /** 纯查询:该职业是否已习得且当前生效(无副作用,供技能系统判定)。
     *  第一(本命)职业 0 级即生效;第二职业需达到 classLevel2。 */
    public static boolean isActive(ServerPlayerEntity p, PlayerClass c) {
        List<String> learned = learnedList(p);
        int idx = learned.indexOf(c.id);
        if (idx < 0) return false;
        if (idx == 0) return true;
        return p.experienceLevel >= YongyeConfig.get().classLevel2;
    }

    /** 开局选职:把所选职业作为第一(本命)职业授予,出生即生效;可选附赠专属武器。
     *  已选过 / 已有职业则不再授予(防重复与防刷)。 */
    public static boolean chooseStartingClass(ServerPlayerEntity p, PlayerClass c) {
        if (p.getAttachedOrElse(ModAttachments.STARTING_CLASS_CHOSEN, false)) return false;
        List<String> learned = learnedList(p);
        if (!learned.isEmpty()) {           // 老玩家已有职业:只补标记,不再弹窗
            p.setAttached(ModAttachments.STARTING_CLASS_CHOSEN, true);
            return false;
        }
        learned.add(c.id);
        p.setAttached(ModAttachments.LEARNED_CLASSES, learned);
        p.setAttached(ModAttachments.STARTING_CLASS_CHOSEN, true);
        applyClasses(p);
        if (YongyeConfig.get().startingClassGiveWeapon && c != PlayerClass.MONK) {
            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.getClassWeapon(c)));
        }
        p.sendMessage(Text.literal("你选择了本命职业【" + c.cn + "】,出生即生效!").formatted(Formatting.GOLD), false);
        com.yongye.network.YongyeNet.sendStats(p);   // 同步职业到客户端(背包显示用)
        com.yongye.network.YongyeNet.sendTalents(p); // 同步天赋状态(天赋面板即时显示该职业,不用重进)
        return true;
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
        com.yongye.network.YongyeNet.sendStats(p);   // 刷新背包职业显示
        com.yongye.network.YongyeNet.sendTalents(p); // 刷新天赋面板(新职业的天赋行即时出现)
        return true;
    }

    /**
     * 替换职业:丢弃 discardId 对应的职业,在其原槽位换上 newId(任选丢弃本命或第二,新职业沿用被丢弃者的槽位)。
     * 需:仍满 2 职业、等级达到 classLevel2、背包里确有该新职业书(创造模式免扣)。由替换界面确认后调用。
     * 注:被丢弃职业已投入的天赋点不退还;若替换的是本命槽,不会重新发放开局专属武器。
     */
    public static boolean replaceClass(ServerPlayerEntity p, String discardId, String newId) {
        YongyeConfig cfg = YongyeConfig.get();
        List<String> learned = learnedList(p);
        PlayerClass nc = PlayerClass.byId(newId);
        if (nc == null) return false;
        if (learned.size() < 2) {                       // 防御:不足 2 职业不该走替换流程
            p.sendMessage(Text.literal("当前职业不足 2 个,无需替换").formatted(Formatting.YELLOW), true);
            return false;
        }
        if (learned.contains(newId)) {
            p.sendMessage(Text.literal("你已经学过【" + nc.cn + "】了").formatted(Formatting.YELLOW), true);
            return false;
        }
        int slot = learned.indexOf(discardId);
        if (slot < 0) {
            p.sendMessage(Text.literal("要丢弃的职业不存在").formatted(Formatting.RED), true);
            return false;
        }
        if (p.experienceLevel < cfg.classLevel2) {
            p.sendMessage(Text.literal("替换职业需要等级 " + cfg.classLevel2 + "(当前 " + p.experienceLevel + ")").formatted(Formatting.RED), true);
            return false;
        }
        // 校验背包确有该新职业书(防止界面停留期间书被丢弃/换走才扣书)
        net.minecraft.item.Item bookItem = com.yongye.registry.ModItems.getClassBook(nc);
        if (!p.isCreative() && !hasItem(p, bookItem)) {
            p.sendMessage(Text.literal("职业书不在背包里了").formatted(Formatting.RED), true);
            return false;
        }
        PlayerClass dc = PlayerClass.byId(discardId);
        learned.set(slot, newId);                       // 新职业占据被丢弃职业的原槽位(保持另一职业槽位不变)
        p.setAttached(ModAttachments.LEARNED_CLASSES, learned);
        if (!p.isCreative()) removeOne(p, bookItem);
        applyClasses(p);
        p.sendMessage(Text.literal("已用【" + nc.cn + "】替换【" + (dc != null ? dc.cn : "?") + "】!").formatted(Formatting.GOLD), false);
        com.yongye.network.YongyeNet.sendStats(p);
        com.yongye.network.YongyeNet.sendTalents(p);
        return true;
    }

    /** 背包里是否至少有 1 个指定物品。 */
    private static boolean hasItem(ServerPlayerEntity p, net.minecraft.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).getItem() == item) return true;
        }
        return false;
    }

    /** 从背包扣除 1 个指定物品(从前往后找第一栈)。 */
    private static void removeOne(ServerPlayerEntity p, net.minecraft.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            net.minecraft.item.ItemStack s = inv.getStack(i);
            if (s.getItem() == item) { s.decrement(1); return; }
        }
    }

    private static List<PlayerClass> enforceAndGet(ServerPlayerEntity p) {
        YongyeConfig cfg = YongyeConfig.get();
        List<String> learned = learnedList(p);
        int lvl = p.experienceLevel;
        boolean changed = false;
        // 本命职业(index 0)永久;仅第二职业在等级跌破 classLevel2 时失去
        while (learned.size() >= 2 && lvl < cfg.classLevel2) {
            PlayerClass lost = PlayerClass.byId(learned.remove(learned.size() - 1));
            changed = true;
            p.sendMessage(Text.literal("等级跌破 " + cfg.classLevel2 + ",失去第二职业【" + (lost != null ? lost.cn : "?") + "】").formatted(Formatting.DARK_RED), false);
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
        EntityAttributeInstance hpInst = p.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (hpInst != null) hpInst.removeModifier(MONK_HP_ID);

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
            if (bonus > 0) {
                if (atk.getModifier(MONK_FIST_ID) != null) atk.removeModifier(MONK_FIST_ID);
                atk.addTemporaryModifier(new EntityAttributeModifier(MONK_FIST_ID, bonus, Operation.ADD_VALUE));
            }
        }
        // 武僧:吃材料攒的生命上限(不限空手,越吃越肥一直在)
        if (active.contains(PlayerClass.MONK) && hpInst != null) {
            int hpBonus = p.getAttachedOrElse(ModAttachments.MONK_HP_BONUS, 0);
            if (hpBonus > 0) {
                if (hpInst.getModifier(MONK_HP_ID) != null) hpInst.removeModifier(MONK_HP_ID);
                hpInst.addTemporaryModifier(new EntityAttributeModifier(MONK_HP_ID, hpBonus, Operation.ADD_VALUE));
            }
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
        Yongye.LOGGER.info("[永夜] 职业系统已挂载");
    }
}
