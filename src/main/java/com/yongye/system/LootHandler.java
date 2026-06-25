package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

import java.util.List;

/**
 * 随机掉落系统(文档第 11 章)。
 * 敌对怪死亡时,按品质概率表随机产出战利品;稀有及以上品质会掺入技能书与超稀有材料。
 * Phase 1 使用"普通怪"概率表;精英/Boss 专属掉落留待 Phase 2。
 */
public final class LootHandler {
    private LootHandler() {}

    /** 单条掉落:根据随机数生成一个 ItemStack。 */
    @FunctionalInterface
    private interface LootFactory {
        ItemStack make(Random r);
    }

    private static LootFactory item(Item item, int min, int max) {
        return r -> new ItemStack(item, min + (max > min ? r.nextInt(max - min + 1) : 0));
    }

    private static LootFactory book(int level) {
        return r -> HealthSkillBookItem.create(level);
    }

    private static LootFactory bookRange(int min, int max) {
        return r -> HealthSkillBookItem.create(min + (max > min ? r.nextInt(max - min + 1) : 0));
    }

    /** 随机一本属性技能书(攻击/护甲/恢复/闪避/反伤/抗性),等级 [minV, maxV]。 */
    private static ItemStack randomSkillBook(Random r, int minV, int maxV) {
        SkillType[] types = SkillType.values();
        SkillType t = types[r.nextInt(types.length)];
        int lvl = minV + (maxV > minV ? r.nextInt(maxV - minV + 1) : 0);
        return SkillBookItem.create(t, lvl);
    }

    /**
     * 随机一本「任意」技能书:从 [血量技能书] + [6 种属性技能书] 中等概率选一类,等级 [minV, maxV]。
     * 用于精英必爆套餐——比只出属性书更全面(也可能出血量书)。
     */
    private static ItemStack randomAnySkillBook(Random r, int minV, int maxV) {
        SkillType[] types = SkillType.values();
        int pick = r.nextInt(types.length + 1); // 0 = 血量书,其余 = 属性书
        int lvl = minV + (maxV > minV ? r.nextInt(maxV - minV + 1) : 0);
        if (pick == 0) return HealthSkillBookItem.create(lvl);
        return SkillBookItem.create(types[pick - 1], lvl);
    }

    private static final List<LootFactory> COMMON = List.of(
            item(Items.DIRT, 1, 4), item(Items.COBBLESTONE, 1, 4), item(Items.STICK, 1, 3),
            item(Items.ROTTEN_FLESH, 1, 3), item(Items.WHEAT_SEEDS, 1, 3), item(Items.STRING, 1, 3),
            item(Items.BONE, 1, 3), item(Items.GUNPOWDER, 1, 2), item(Items.GRAVEL, 1, 3),
            item(Items.COAL, 1, 3), item(Items.APPLE, 1, 2), item(Items.BREAD, 1, 2)
    );

    private static final List<LootFactory> USEFUL = List.of(
            item(Items.IRON_NUGGET, 1, 5), item(Items.IRON_INGOT, 1, 2), item(Items.COAL_BLOCK, 1, 1),
            item(Items.COOKED_BEEF, 1, 3), item(Items.SHIELD, 1, 1), item(Items.BOW, 1, 1),
            item(Items.ARROW, 4, 12), item(Items.BUCKET, 1, 1), item(Items.TORCH, 2, 6),
            item(Items.GOLDEN_APPLE, 1, 1)
    );

    private static final List<LootFactory> RARE = List.of(
            item(Items.DIAMOND, 1, 2), item(Items.EMERALD, 1, 3), item(Items.EXPERIENCE_BOTTLE, 1, 4),
            item(Items.IRON_CHESTPLATE, 1, 1), item(Items.DIAMOND_PICKAXE, 1, 1), item(Items.ENDER_PEARL, 1, 2),
            item(Items.SADDLE, 1, 1), item(Items.GOLDEN_CARROT, 1, 3), book(1)
    );

    private static final List<LootFactory> EPIC = List.of(
            item(Items.DIAMOND_CHESTPLATE, 1, 1), item(Items.TOTEM_OF_UNDYING, 1, 1),
            item(Items.NETHERITE_SCRAP, 1, 1), item(Items.ANCIENT_DEBRIS, 1, 1), item(Items.TRIDENT, 1, 1),
            bookRange(2, 3), item(ModItems.LIFE_CRYSTAL, 1, 1)
    );

    private static final List<LootFactory> GODLY = List.of(
            item(Items.DIAMOND_BLOCK, 1, 1), item(Items.ENCHANTED_GOLDEN_APPLE, 1, 1),
            item(Items.NETHERITE_INGOT, 1, 1), item(Items.TOTEM_OF_UNDYING, 1, 1),
            book(5), item(ModItems.LIFE_CORE, 1, 1)
    );

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableRandomLoot) return;
            if (!(entity instanceof Monster)) return;
            if (!(entity.getWorld() instanceof ServerWorld world)) return;
            if (cfg.lootRequirePlayerKill && !(damageSource.getAttacker() instanceof PlayerEntity)) return;
            // Boss 掉落由 BossHandler 负责,避免重复
            if (entity.getAttachedOrElse(com.yongye.registry.ModAttachments.IS_BOSS, false)) return;

            Random r = entity.getRandom();
            boolean elite = entity.getAttachedOrElse(com.yongye.registry.ModAttachments.IS_ELITE, false);

            // —— 动态爆率:按击杀者强度算掉率倍率(玩家越强越低) ——
            double baseLm = (damageSource.getAttacker() instanceof ServerPlayerEntity killer)
                    ? PlayerPower.lootMultiplier(killer) : 1.0;
            // —— 难度奖励(m150):世界难度越高掉落越丰厚;保底 difficultyLootFloor(默认 1.0 即低难度不减) ——
            double dm = cfg.enableDifficultyLootBonus
                    ? Math.max(cfg.difficultyLootFloor, DifficultyManager.mobMult()) : 1.0;
            // 下文所有概率掉落用的综合倍率 = 动态爆率 × 难度奖励(因此每处 *lm 都自动含难度加成,无需逐处改)
            double lm = baseLm * dm;

            if (elite) {
                // —— 精英必爆套餐(m90):在下面的概率掉落之上额外保底,提高精英击杀收益 ——
                if (cfg.eliteGuaranteedDrops) {
                    // 动态缩减必爆数量(防滚雪球;可用 dynamicLootScaleGuaranteed 关闭)× 难度奖励(难度加成不受该开关约束)
                    double gm = (cfg.dynamicLootScaleGuaranteed ? baseLm : 1.0) * dm;
                    int gShards   = (int) Math.round(cfg.eliteGuaranteedShards   * gm);
                    int gCrystals = (int) Math.round(cfg.eliteGuaranteedCrystals * gm);
                    int gBooks    = (int) Math.round(cfg.eliteGuaranteedSkillBooks * gm);
                    if (gShards > 0) {
                        drop(world, entity, new ItemStack(ModItems.LIFE_SHARD, gShards));
                    }
                    if (gCrystals > 0) {
                        drop(world, entity, new ItemStack(ModItems.LIFE_CRYSTAL, gCrystals));
                    }
                    for (int i = 0; i < gBooks; i++) {
                        drop(world, entity, randomAnySkillBook(r,
                                cfg.eliteGuaranteedSkillBookMinLevel, cfg.eliteGuaranteedSkillBookMaxLevel));
                    }
                }
                // 精英:技能书改为按概率掉(skillBookDropChanceElite,默认已调极低)+ 一件稀有以上战利品 + 概率材料
                if (r.nextDouble() < cfg.skillBookDropChanceElite * lm) {
                    drop(world, entity, HealthSkillBookItem.create(1 + r.nextInt(3))); // V1~V3
                }
                // 概率掉一本属性技能书(V1~V3)
                if (r.nextDouble() < cfg.skillBookDropChanceElite * lm) {
                    drop(world, entity, randomSkillBook(r, 1, 3));
                }
                List<LootFactory> hi = switch (r.nextInt(3)) {
                    case 0 -> RARE;
                    case 1 -> EPIC;
                    default -> GODLY;
                };
                drop(world, entity, hi.get(r.nextInt(hi.size())).make(r));
                // (生命结晶改由下方统一规则按 lifeCrystalDropChance 产出,精英自动翻倍;此处不再额外写死掉落)
                // (生命核心及血核改由下方统一规则按"仅精英"产出)
                // 精英概率掉落职业书(随机职业)
                if (r.nextDouble() < cfg.classBookDropChance * lm) {
                    com.yongye.item.PlayerClass[] cls = com.yongye.item.PlayerClass.values();
                    drop(world, entity, new ItemStack(ModItems.getClassBook(cls[r.nextInt(cls.length)])));
                }
                // 精英小概率掉落职业专属武器(随机职业,EPIC)
                if (r.nextDouble() < cfg.classWeaponDropChanceElite * lm) {
                    com.yongye.item.PlayerClass[] cls = ModItems.WEAPON_CLASSES;
                    drop(world, entity, new ItemStack(ModItems.getClassWeapon(cls[r.nextInt(cls.length)])));
                }
            } else {
                // 普通怪:按品质表单选
                double roll = r.nextDouble();
                List<LootFactory> pool = pickPool(roll, cfg);
                // 动态爆率:玩家越强,普通池掉落越容易被跳过(保底 dynamicLootFloor)
                if (pool != null && (lm >= 1.0 || r.nextDouble() < lm)) {
                    LootFactory f = pool.get(r.nextInt(pool.size()));
                    ItemStack loot = f.make(r);
                    // 前期(游戏天数少)压制技能书爆率
                    if (!(isSkillBookStack(loot) && isEarlyGame(world, cfg) && r.nextDouble() >= cfg.skillBookEarlyGameChance)) {
                        drop(world, entity, loot);
                    }
                }
                // 普通怪小概率掉一本属性技能书(V1),永夜等级越高几率越大(封顶防深渊层失控);前期再乘压制系数
                double nfMult = Math.min(1.0 + NightfallManager.getLevel() * 0.5, cfg.skillBookNightfallMaxMult);
                double sbChance = cfg.skillBookDropChanceNormal * nfMult * lm;
                if (isEarlyGame(world, cfg)) sbChance *= cfg.skillBookEarlyGameChance;
                if (r.nextDouble() < sbChance) {
                    drop(world, entity, randomSkillBook(r, 1, 1));
                }
            }

            // 强化材料掉落(规则:生命核心及以上仅精英会爆,普通怪只出碎片/结晶)
            // 生命碎片:按 lifeShardDropChance 概率掉(普通 1 个;精英 1~2 个)
            if (r.nextDouble() < cfg.lifeShardDropChance * lm) {
                drop(world, entity, new ItemStack(ModItems.LIFE_SHARD, elite ? 1 + r.nextInt(2) : 1));
            }
            // 生命结晶:普通怪按 lifeCrystalDropChance(精英翻倍)
            if (r.nextDouble() < cfg.lifeCrystalDropChance * (elite ? 2.0 : 1.0) * lm) {
                drop(world, entity, new ItemStack(ModItems.LIFE_CRYSTAL, 1));
            }
            // 生命核心 + 灾厄血核:仅精英
            if (elite) {
                if (r.nextDouble() < cfg.lifeCoreDropChance * lm) {
                    drop(world, entity, new ItemStack(ModItems.LIFE_CORE, 1));
                }
                if (r.nextDouble() < cfg.bloodCoreDropChanceElite * lm) {
                    drop(world, entity, new ItemStack(ModItems.CATASTROPHE_BLOOD_CORE, 1));
                }
                if (r.nextDouble() < cfg.endingEssenceDropChanceElite * lm) {
                    drop(world, entity, new ItemStack(ModItems.ENDING_ESSENCE, 1));
                }
            }
        });
        Yongye.LOGGER.info("[永夜] 随机掉落系统已挂载");
    }

    private static List<LootFactory> pickPool(double roll, YongyeConfig cfg) {
        double c = cfg.lootChanceCommon;
        double u = c + cfg.lootChanceUseful;
        double ra = u + cfg.lootChanceRare;
        double e = ra + cfg.lootChanceEpic;
        double g = e + cfg.lootChanceGodly;
        if (roll < c) return COMMON;
        if (roll < u) return USEFUL;
        if (roll < ra) return RARE;
        if (roll < e) return EPIC;
        if (roll < g) return GODLY;
        return null; // 落空
    }

    private static boolean isSkillBookStack(ItemStack stack) {
        return stack.getItem() instanceof HealthSkillBookItem || stack.getItem() instanceof SkillBookItem;
    }

    private static boolean isEarlyGame(ServerWorld world, YongyeConfig cfg) {
        return (world.getTimeOfDay() / 24000L) < cfg.skillBookEarlyGameDays;
    }

    private static void drop(ServerWorld world, LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(world, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
        ie.setToDefaultPickupDelay();
        world.spawnEntity(ie);
    }
}
