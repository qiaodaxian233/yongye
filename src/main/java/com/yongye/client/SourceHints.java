package com.yongye.client;

import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.item.EnhanceStoneItem;
import com.yongye.registry.ModItems;
import net.minecraft.item.Item;

import java.util.HashMap;
import java.util.Map;

/**
 * m322:无直接配方物品的「获取:」说明(作者:「给无法直接获取的物品添加获取说明」)。
 * 集中式一张表+两个 instanceof(强化石十档/技能书多类型),零 per-item 改动;
 * 只覆盖**没有工作台配方**的掉落/兑换/发放类物品——有配方的(神器/夜蚀套装/混沌之刃/结界书/职业武器)
 * 玩家在配方书里就能看到,不加噪音。文案与实际掉落口径逐条对过 config 注释与 handler。
 */
public final class SourceHints {
    private SourceHints() {}

    private static Map<Item, String> MAP;

    /** 返回该物品的获取说明;无需说明(可合成/未收录)返回 null。懒加载:首次调用时物品注册已完成。 */
    public static String of(Item item) {
        if (item instanceof EnhanceStoneItem) return "击杀怪物掉落(强度越高档位越高);无法合成";
        if (item instanceof SkillBookItem)   return "怪物/精英/BOSS 掉落(永夜等级提升掉率);两本同类可合成,等级相加";
        if (item instanceof HealthSkillBookItem) return "怪物/精英/BOSS 掉落(永夜等级提升掉率);两本可合成,等级相加";
        if (MAP == null) {
            Map<Item, String> m = new HashMap<>();
            m.put(ModItems.LIFE_SHARD,             "击杀怪物概率掉落(永夜越深越多)·精英必爆·任务奖励");
            m.put(ModItems.LIFE_CRYSTAL,           "材料兑换(10 碎片=1)·怪物小概率(精英翻倍)·BOSS 掉落");
            m.put(ModItems.LIFE_CORE,              "材料兑换(10 结晶=1)·精英/BOSS 掉落");
            m.put(ModItems.CATASTROPHE_BLOOD_CORE, "材料兑换(10 核心=1)·精英小概率·BOSS 掉落");
            m.put(ModItems.ENDING_ESSENCE,         "精英/BOSS/佩恩掉落(材料链最高档)·任务;用于武器技能升级");
            m.put(ModItems.ENHANCE_PROTECT_SCROLL, "无法合成:怪物极低概率掉落·杀怪自动累积兑换");
            m.put(ModItems.AUTO_ENHANCE_SCROLL,    "怪物掉落·任务奖励(概率随永夜等级上浮)");
            m.put(ModItems.AUTO_BOOK_SCROLL,       "怪物掉落·任务奖励(概率随永夜等级上浮)");
            m.put(ModItems.ENDLESS_NIGHT_DUST,     "永夜生存收益·苦修·材料兑换");
            m.put(ModItems.RIFT_FRAGMENT,          "灾变核心事件·苦修·材料兑换");
            m.put(ModItems.ABYSS_SOUL_CRYSTAL,     "永夜生存收益·苦修·材料兑换");
            m.put(ModItems.CLASS_SELECT_BOOK,      "首次进入世界自动发放;更换职业时消耗");
            MAP = m;
        }
        return MAP.get(item);
    }
}
