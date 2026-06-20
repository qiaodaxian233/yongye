package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
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

            if (elite) {
                // 精英专属:保底一本技能书 + 一件稀有以上战利品 + 概率材料
                drop(world, entity, HealthSkillBookItem.create(1 + r.nextInt(3))); // V1~V3
                List<LootFactory> hi = switch (r.nextInt(3)) {
                    case 0 -> RARE;
                    case 1 -> EPIC;
                    default -> GODLY;
                };
                drop(world, entity, hi.get(r.nextInt(hi.size())).make(r));
                if (r.nextDouble() < 0.5) drop(world, entity, new ItemStack(ModItems.LIFE_CRYSTAL));
                if (r.nextDouble() < 0.2) drop(world, entity, new ItemStack(ModItems.LIFE_CORE));
            } else {
                // 普通怪:按品质表单选
                double roll = r.nextDouble();
                List<LootFactory> pool = pickPool(roll, cfg);
                if (pool != null) {
                    LootFactory f = pool.get(r.nextInt(pool.size()));
                    drop(world, entity, f.make(r));
                }
            }

            // 独立的"生命碎片"掉落(文档 15.1),精英翻倍掉率
            double shardChance = cfg.lifeShardDropChance * (elite ? 2.0 : 1.0);
            if (r.nextDouble() < shardChance) {
                drop(world, entity, new ItemStack(ModItems.LIFE_SHARD, 1));
            }
        });
        Yongye.LOGGER.info("[亡途荒夜] 随机掉落系统已挂载");
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

    private static void drop(ServerWorld world, LivingEntity entity, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity ie = new ItemEntity(world, entity.getX(), entity.getY() + 0.5, entity.getZ(), stack);
        ie.setToDefaultPickupDelay();
        world.spawnEntity(ie);
    }
}
