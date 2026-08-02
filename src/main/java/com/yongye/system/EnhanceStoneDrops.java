package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

/**
 * m296 强化石滑动窗掉落(定稿设计):按进度算基准档 t,三类怪围着 t 开一扇小窗往上够。
 * 基准档 t:
 *   第 1~5 天(未进永夜)= 1;佩恩降临后(gameDay ≥ painSpawnMinDay)= 2;
 *   进入永夜 I = 4,永夜每升一层 +1(即 3+永夜级,深渊 N 层继续 +1);
 *   每次怪物进化(每 evolutionEveryDays 天)再 +1;封顶 stoneTierCap(默 10)。
 * 掉法:普通怪 stoneDropChanceNormal 概率掉一颗(窗 t/t+1/t+2 = 70/25/5);
 *       精英必掉一颗(窗 t+1/t+2/t+3 = 50/40/10);
 *       BOSS 必掉 stoneBossMinCount~Max 颗(档均匀 t+2~t+4)。
 * 封顶期收口:普通怪掉到最高档时降为次档(stoneTopTierEliteOnly,10 亿档只从精英/BOSS 出)。
 * 概率/权重/颗数全配置;掉率**不乘动态爆率**——档位窗本身就是节奏闸,叠一层衰减会互相打架。
 */
public final class EnhanceStoneDrops {
    private EnhanceStoneDrops() {}

    /** 当前基准档 t(1..stoneTierCap)。 */
    public static int baseTier(ServerWorld world) {
        YongyeConfig c = YongyeConfig.get();
        long day = ProgressionManager.gameDay(world);
        long t = 1;
        if (day >= c.painSpawnMinDay) t = 2;              // 佩恩降临后
        int nf = NightfallManager.getLevel();
        if (nf >= 1) t = 3L + nf;                         // 永夜 I=4,每层 +1
        t += day / Math.max(1, c.evolutionEveryDays);     // 每次怪物进化 +1
        return (int) Math.max(1, Math.min(c.stoneTierCap, t));
    }

    /**
     * m301 天数硬顶(作者实机:第 2 天任务失败把永夜提早推到 I,基准档直接=4,普通怪开出 1万级石——
     * 「怎么也得十天以后才会爆 1000 级以上」):无论永夜/进化把档位信号推多高,
     * **最终掉落档 ≤ 1 + 游戏天数 / stoneDaysPerTier**(默 3:第 0~2 天只出 1 档,第 9 天起才可能 4 档=1000 级,
     * 第 12 天起 5 档=1万,第 27 天起才摸 10 档)。窗口偏移(+1~+4)同样被顶住。0=关闭天数墙。
     */
    private static int dayCeiling(ServerWorld world) {
        int per = YongyeConfig.get().stoneDaysPerTier;
        if (per <= 0) return Integer.MAX_VALUE;
        return (int) Math.min(10, 1 + ProgressionManager.gameDay(world) / per);
    }

    /** 普通怪:窗 t/t+1/t+2,权重 stoneNormalWeightT0/T1/T2;封顶期最高档降为次档。 */
    public static ItemStack rollNormal(ServerWorld world, Random r) {
        YongyeConfig c = YongyeConfig.get();
        int tier = baseTier(world) + pick(r, c.stoneNormalWeightT0, c.stoneNormalWeightT1, c.stoneNormalWeightT2);
        tier = Math.min(tier, dayCeiling(world)); // m301 天数硬顶
        tier = clamp(c, tier);
        if (c.stoneTopTierEliteOnly && tier >= c.stoneTierCap) {
            tier = Math.max(1, c.stoneTierCap - 1); // 最高档只从精英/BOSS 出,普通怪主掉次档
        }
        return new ItemStack(ModItems.enhanceStone(tier));
    }

    /** 精英:必掉一颗,窗 t+1/t+2/t+3,权重 stoneEliteWeightT1/T2/T3。 */
    public static ItemStack rollElite(ServerWorld world, Random r) {
        YongyeConfig c = YongyeConfig.get();
        int tier = baseTier(world) + 1 + pick(r, c.stoneEliteWeightT1, c.stoneEliteWeightT2, c.stoneEliteWeightT3);
        tier = Math.min(tier, dayCeiling(world)); // m301 天数硬顶
        return new ItemStack(ModItems.enhanceStone(clamp(c, tier)));
    }

    /** BOSS:单颗档位在 t+stoneBossMinOffset ~ t+stoneBossMaxOffset 间均匀。颗数由调用方定。 */
    public static ItemStack rollBoss(ServerWorld world, Random r) {
        YongyeConfig c = YongyeConfig.get();
        int lo = Math.min(c.stoneBossMinOffset, c.stoneBossMaxOffset);
        int hi = Math.max(c.stoneBossMinOffset, c.stoneBossMaxOffset);
        int tier = baseTier(world) + lo + (hi > lo ? r.nextInt(hi - lo + 1) : 0);
        tier = Math.min(tier, dayCeiling(world)); // m301 天数硬顶
        return new ItemStack(ModItems.enhanceStone(clamp(c, tier)));
    }

    // ============ m297:技能书 ×100 分五档,随同一进度基准档爬 ============
    // 书档 b 面值 = 100^(b-1):1 / 100 / 1万 / 100万 / 1亿(不做 10亿 单本——一本满级会把成长线一刀砍死,
    // 留「十本 1亿 大书」的收集过程)。书档随石档折半爬:b = (t+1)/2(石 ×10 一档、书 ×100 一档,量级对齐)。
    // 十几种书里只有攻击书吃得下大数(0.5/级平铺无封顶);百分比类封顶都极低(吸血 8%/暴击 25%/破甲 30%…),
    // 掉百万级纯浪费掉落位——只掉前 skillBookPercentTierCap(默 2)档,攻击书独占高档。

    /** 书档面值:100^(bookTier-1),long 计算钳 int(5 档 = 1 亿,在 int 内)。 */
    public static int bookTierLevel(int bookTier) {
        long v = 1L;
        for (int i = 1; i < bookTier; i++) v *= 100L;
        return (int) Math.min(Integer.MAX_VALUE, v);
    }

    /** 当前书基准档 b = (石基准档 t + 1) / 2,钳 [1,5]。m301:石档先吃天数硬顶,书随之被顶住。 */
    public static int stageBookTier(ServerWorld world) {
        int t = Math.min(baseTier(world), dayCeiling(world));
        return Math.max(1, Math.min(5, (t + 1) / 2));
    }

    /** 按类型给书等级:攻击书吃满当前书档,百分比类钳到 skillBookPercentTierCap。 */
    public static int bookLevelFor(ServerWorld world, com.yongye.item.SkillType type) {
        YongyeConfig c = YongyeConfig.get();
        int b = stageBookTier(world);
        if (type != com.yongye.item.SkillType.ATTACK) {
            b = Math.min(b, Math.max(1, c.skillBookPercentTierCap));
        }
        return bookTierLevel(b);
    }

    /**
     * m432 血量书阶段档(m302 遗留「血量书未入分档体系,后期血量成长明显慢于攻击,待作者定」清账)。
     * <p>入档理由:血量书 +10/级是**平铺无封顶**的加法,与攻击书 0.5/级同属「吃得下大数」那一类
     * ——百分比类书之所以钳前两档是因为它们封顶都极低(吸血 8%/暴击 25%),血量没有这个问题。
     * 攻击走 m298 超线性曲线跑得更快,血量还卡在 V1~V30 的固定小等级,后期就是被怪一刀带走。
     * <p>档位独立可调:healthBookTierCap(默 5=与攻击书同梯,调 2 即回「只到 100 档」的保守口径),
     * 总闸仍是 enableStagedSkillBooks(关=各掉落点回各自的旧固定等级)。
     */
    public static int healthBookLevelFor(ServerWorld world) {
        YongyeConfig c = YongyeConfig.get();
        int b = Math.min(stageBookTier(world), Math.max(1, c.healthBookTierCap));
        return bookTierLevel(b);
    }

    /** 三权重选 0/1/2 偏移(权重和不必为 1,按比例;全 0 时落 0 偏移)。 */
    private static int pick(Random r, double w0, double w1, double w2) {
        double total = w0 + w1 + w2;
        if (total <= 0) return 0;
        double roll = r.nextDouble() * total;
        if (roll < w0) return 0;
        if (roll < w0 + w1) return 1;
        return 2;
    }

    private static int clamp(YongyeConfig c, int tier) {
        int cap = Math.max(1, Math.min(10, c.stoneTierCap)); // 只有 10 档物品,硬上限 10
        return Math.max(1, Math.min(cap, tier));
    }
}
