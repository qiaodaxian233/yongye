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
    private static final Identifier ID_SWIFT_MOVE = Identifier.of(Yongye.MOD_ID, "skill_swift_move");   // m291
    private static final Identifier ID_SWIFT_ASPD = Identifier.of(Yongye.MOD_ID, "skill_swift_aspd");   // m291
    private static final Identifier ID_STEADFAST = Identifier.of(Yongye.MOD_ID, "skill_steadfast");     // m291

    private static int tickCounter = 0;
    private static boolean procApplying = false;                       // m291 暴击/破甲追加伤害防重入
    private static final Map<java.util.UUID, Long> REJUV_LAST_COMBAT = new HashMap<>(); // m291 回春:最近入战时刻

    public static int getLearnedLevel(ServerPlayerEntity player, SkillType type) {
        return player.getAttachedOrElse(ModAttachments.LEARNED_SKILLS, Map.of()).getOrDefault(type.id, 0);
    }

    public static LearnResult learn(ServerPlayerEntity player, SkillType type, int level) {
        int max = YongyeConfig.get().skillBookMaxLevel;
        int cur = getLearnedLevel(player, type);
        if (cur >= max) return LearnResult.CAPPED;
        int next = (int) Math.min(max, (long) cur + Math.max(1, level)); // m293:先 long 后钳,防 cur+level 溢出
        Map<String, Integer> copy = new HashMap<>(player.getAttachedOrElse(ModAttachments.LEARNED_SKILLS, Map.of()));
        copy.put(type.id, next);
        player.setAttached(ModAttachments.LEARNED_SKILLS, copy);
        applyAttributes(player);
        com.yongye.network.YongyeNet.sendStats(player);
        return LearnResult.OK;
    }

    /**
     * 一键学书:扫描背包,把所有属性技能书 + 血量书一次性全部学掉(按 等级×数量 累加)并清空对应栈。
     * 由背包「学书」按钮经 UseAllBooksPayload 触发。
     */
    public static void useAllBooks(ServerPlayerEntity player) {
        net.minecraft.entity.player.PlayerInventory inv = player.getInventory();
        int booksUsed = 0;
        int healthGained = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof com.yongye.item.SkillBookItem sb) {
                // m293:等级近 10 亿 × 一叠 64 本 = 6.4e10,int 乘法先溢出,改 long 再钳
                int total = (int) Math.min(Integer.MAX_VALUE,
                        (long) com.yongye.item.SkillBookItem.getLevel(s) * s.getCount());
                learn(player, sb.getType(), total);
                booksUsed += s.getCount();
                inv.setStack(i, ItemStack.EMPTY);
            } else if (s.getItem() instanceof com.yongye.item.HealthSkillBookItem) {
                int total = (int) Math.min(Integer.MAX_VALUE,
                        (long) com.yongye.item.HealthSkillBookItem.getLevel(s) * s.getCount()); // m293 同上
                PlayerSkillManager.learnHealth(player, total);
                healthGained += total;
                booksUsed += s.getCount();
                inv.setStack(i, ItemStack.EMPTY);
            }
        }
        if (booksUsed == 0) {
            player.sendMessage(Text.literal("背包里没有可学的技能书").formatted(Formatting.YELLOW), true);
            return;
        }
        player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
        player.sendMessage(Text.literal("一键学书:消耗 " + booksUsed + " 本"
                + (healthGained > 0 ? "(含血量 +V" + healthGained + ")" : "")).formatted(Formatting.AQUA), true);
        com.yongye.network.YongyeNet.sendStats(player);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < 20) return; // 每秒一次
            tickCounter = 0;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                applyAttributes(p);
                applyRegen(p);
                applyRejuvenate(p);
                applyResistance(p);
                applySatiety(p);
            }
        });
        // 饱食:每 tick 钉住食物/饱和度/耗竭,避免秒间被原版缓慢扣减
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (getLearnedLevel(p, SkillType.SATIETY) <= 0) continue;
                var hm = p.getHungerManager();
                if (hm.getFoodLevel() < 20) hm.setFoodLevel(20);
                if (hm.getExhaustion() > 0f) hm.setExhaustion(0f);
                if (hm.getSaturationLevel() < 5f) hm.setSaturationLevel(5f);
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

        // —— 吸血(m290,作者点名「不能太高」):亲手近战命中,按造成伤害比例回血 ——
        // 观察者口径:挂 ALLOW_DAMAGE 永远放行只旁听;只认「玩家直接近战」(source.getSource()==攻击者,
        // 与处决同口径,弓/魔法/召唤物不吸);不吸玩家。回血走 heal(),禁疗系统照常拦得住。
        // 数值:每级 skillLifestealPerLevel(默 0.4%),封顶 skillLifestealMax(默 8%)——
        // 技能书等级上限 10 亿,必须靠封顶封死,不能按级裸乘。
        // m291:该监听扩为「近战触发合流」——暴击 / 破甲 / 吸血共用一次判定,并顺带维护回春的战斗计时。
        // 触发追加伤害时置 procApplying 防重入(追加伤害再进本监听直接放行,不二次触发/不二次吸血,口径保守)。
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (amount <= 0 || procApplying) return true;
            // 回春战斗计时:玩家挨打也算入战(攻击者是生物才算,摔落/环境伤不打断脱战)
            if (entity instanceof ServerPlayerEntity hurt
                    && source.getAttacker() instanceof net.minecraft.entity.LivingEntity a && a != hurt) {
                REJUV_LAST_COMBAT.put(hurt.getUuid(), hurt.getWorld().getTime());
            }
            if (!(source.getAttacker() instanceof ServerPlayerEntity p)) return true;
            REJUV_LAST_COMBAT.put(p.getUuid(), p.getWorld().getTime()); // 出手也算入战
            if (source.getSource() != p) return true; // 只认亲手近战(与处决同口径,弓/魔法/召唤物不触发)
            if (entity instanceof net.minecraft.entity.player.PlayerEntity || entity == p) return true;
            YongyeConfig cfg = YongyeConfig.get();

            // 暴击(m291):概率触发,追加 (倍率-1)×原伤,走玩家名义伤害(吃护甲,能触发击杀归属)
            int critLv = getLearnedLevel(p, SkillType.CRIT);
            if (critLv > 0 && entity.getWorld() instanceof net.minecraft.server.world.ServerWorld sw) {
                double cc = Math.min(cfg.skillCritChanceMax, critLv * cfg.skillCritChancePerLevel);
                double extraMult = Math.max(0, cfg.skillCritMultiplier - 1.0);
                if (extraMult > 0 && p.getRandom().nextDouble() < cc) {
                    procApplying = true;
                    entity.damage(p.getDamageSources().playerAttack(p), (float) (amount * extraMult));
                    procApplying = false;
                    sw.spawnParticles(net.minecraft.particle.ParticleTypes.CRIT,
                            entity.getX(), entity.getBodyY(0.6), entity.getZ(), 12, 0.35, 0.4, 0.35, 0.25);
                    sw.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                            SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 0.9f, 1.25f);
                }
            }

            // 破甲(m291):按比例追加无视护甲伤害(魔法伤,口径同高血量反制的穿甲刀)
            int pierceLv = getLearnedLevel(p, SkillType.PIERCE);
            if (pierceLv > 0) {
                double pf = Math.min(cfg.skillPierceMax, pierceLv * cfg.skillPiercePerLevel);
                if (pf > 0) {
                    procApplying = true;
                    entity.damage(p.getDamageSources().magic(), (float) (amount * pf));
                    procApplying = false;
                }
            }

            // 吸血(m290)
            int lvl = getLearnedLevel(p, SkillType.LIFESTEAL);
            if (lvl > 0) {
                // m292 修正:m290 注释声称「禁疗照常拦」但当时并没有接检查(禁疗是逐入口手动判的,
                // heal() 本身不拦)——本轮接上 healFactor,重创期间按比例减疗。
                double hf = ArtifactManager.healFactor(p);
                double pct = Math.min(cfg.skillLifestealMax, lvl * cfg.skillLifestealPerLevel) * hf;
                if (pct > 0 && p.getHealth() < p.getMaxHealth()) p.heal((float) (amount * pct));
            }
            return true;
        });

        // m291 回春:玩家下线清战斗计时,防长开服累积(m286 同款口径)
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                REJUV_LAST_COMBAT.remove(handler.player.getUuid()));

        Yongye.LOGGER.info("[夜蚀] 技能书(护甲/恢复/闪避/反伤/抗性/饱食/抢夺/吸血/暴击/迅捷/破甲/屹立/贪婪/回春)系统已挂载");
    }

    private static void applyAttributes(ServerPlayerEntity p) {
        int armor = getLearnedLevel(p, SkillType.ARMOR);
        setModifier(p, EntityAttributes.GENERIC_ARMOR, ID_ARMOR, armor * 0.5);
        setModifier(p, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, ID_TOUGH, armor * 0.25);

        int attack = getLearnedLevel(p, SkillType.ATTACK);
        setModifier(p, EntityAttributes.GENERIC_ATTACK_DAMAGE, ID_ATTACK, attack * 0.5);

        // m291 迅捷:移速/攻速百分比(封顶);屹立:击退抗性(属性 0~1,封顶 0.6)
        YongyeConfig cfg = YongyeConfig.get();
        int swift = getLearnedLevel(p, SkillType.SWIFT);
        setModifier(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, ID_SWIFT_MOVE,
                Math.min(cfg.skillSwiftMoveMax, swift * cfg.skillSwiftMovePerLevel),
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(p, EntityAttributes.GENERIC_ATTACK_SPEED, ID_SWIFT_ASPD,
                Math.min(cfg.skillSwiftAtkSpeedMax, swift * cfg.skillSwiftAtkSpeedPerLevel),
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        int steadfast = getLearnedLevel(p, SkillType.STEADFAST);
        setModifier(p, EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, ID_STEADFAST,
                Math.min(cfg.skillSteadfastMax, steadfast * cfg.skillSteadfastPerLevel),
                EntityAttributeModifier.Operation.ADD_VALUE);
    }

    private static void setModifier(ServerPlayerEntity p,
                                    RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                    Identifier id, double value) {
        setModifier(p, attr, id, value, EntityAttributeModifier.Operation.ADD_VALUE);
    }

    private static void setModifier(ServerPlayerEntity p,
                                    RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                                    Identifier id, double value,
                                    EntityAttributeModifier.Operation op) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        if (value > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
        }
    }

    private static void applyRegen(ServerPlayerEntity p) {
        int regen = getLearnedLevel(p, SkillType.REGEN);
        if (regen <= 0) return;
        if (p.getHealth() >= p.getMaxHealth()) return;
        double hf = ArtifactManager.healFactor(p); // m292 重创期间减疗
        if (hf <= 0) return;
        p.heal((float) (regen * 0.1 * hf)); // 每秒回 等级×0.1 点
    }

    /** m291 回春:脱战满 skillRejuvenateDelayTicks 后,每秒回 最大生命×min(封顶, 等级×每级值)。 */
    private static void applyRejuvenate(ServerPlayerEntity p) {
        int lv = getLearnedLevel(p, SkillType.REJUVENATE);
        if (lv <= 0) return;
        if (p.getHealth() >= p.getMaxHealth()) return;
        YongyeConfig cfg = YongyeConfig.get();
        long now = p.getWorld().getTime();
        long last = REJUV_LAST_COMBAT.getOrDefault(p.getUuid(), 0L);
        if (now - last < cfg.skillRejuvenateDelayTicks) return; // 仍在战斗中
        double hf = ArtifactManager.healFactor(p); // m292 重创期间减疗
        if (hf <= 0) return;
        double pct = Math.min(cfg.skillRejuvenateMax, lv * cfg.skillRejuvenatePerLevel);
        if (pct > 0) p.heal((float) (p.getMaxHealth() * pct * hf));
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
        // 直接钉满饱食度 + 留足饱和度缓冲 + 清零耗竭 → 食物条不再抖动
        var hm = p.getHungerManager();
        hm.setFoodLevel(20);
        hm.setSaturationLevel(Math.min(20f, 8f + s * 0.2f));
        hm.setExhaustion(0f);
        // 饱食充盈时缓慢回血(m292:重创期间按系数减疗),让血量也能动
        double hf = ArtifactManager.healFactor(p);
        if (hf > 0 && p.getHealth() < p.getMaxHealth()) {
            p.heal((float) (Math.min(3f, 0.5f + s * 0.02f) * hf));
        }
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
