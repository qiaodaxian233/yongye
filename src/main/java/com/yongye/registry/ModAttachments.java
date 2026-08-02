package com.yongye.registry;

import com.mojang.serialization.Codec;
import com.yongye.Yongye;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/**
 * 玩家数据附着。
 * LEARNED_HEALTH: 玩家累计学习的血量强化等级总和(V值)。
 *   实际额外最大生命 = LEARNED_HEALTH * 10。
 *   persistent: 存档保留; copyOnDeath: 死亡不丢失(永久成长)。
 */
public final class ModAttachments {
    private ModAttachments() {}

    /** m276 自动强化卷:生效截止的世界时间(0=未激活)。persistent:下线重连不清零。 */
    public static final AttachmentType<Long> AUTO_ENHANCE_UNTIL =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "auto_enhance_until"));

    /** m276 自动吃书卷:生效截止的世界时间(0=未激活)。 */
    public static final AttachmentType<Long> AUTO_BOOK_UNTIL =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "auto_book_until"));

    // ============ m328 主线任务书 ============
    public static final AttachmentType<Integer> MAIN_QUEST_STAGE =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "main_quest_stage"));
    public static final AttachmentType<Long> MAIN_KILLS =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG).initializer(() -> 0L).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "main_kills"));
    public static final AttachmentType<Integer> MAIN_ELITE_KILLS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "main_elite_kills"));
    public static final AttachmentType<Integer> MAIN_BOSS_KILLS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "main_boss_kills"));
    public static final AttachmentType<Boolean> MAIN_PAIN_SLAIN =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL).initializer(() -> false).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "main_pain_slain"));
    public static final AttachmentType<Boolean> MAIN_DRAGON_SLAIN =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL).initializer(() -> false).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "main_dragon_slain"));
    public static final AttachmentType<Integer> CLASS_TRIAL_STAGE =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "class_trial_stage"));
    public static final AttachmentType<Boolean> GOT_QUEST_BOOK =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL).initializer(() -> false).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "got_quest_book"));

    public static final AttachmentType<Integer> LEARNED_HEALTH =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "learned_health"));

    /** LEARNED_SKILLS: 其它技能书(护甲/恢复/闪避/反伤/抗性)的累计等级,键为类型 id。 */
    public static final AttachmentType<java.util.Map<String, Integer>> LEARNED_SKILLS =
            AttachmentRegistry.<java.util.Map<String, Integer>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .initializer(java.util.HashMap::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "learned_skills"));

    /** SOULBOUND_STASH(m265):死亡瞬间从掉落流程截走的灵魂绑定物品,重生归还后清空。
     *  Fabric 附件持久化用 RegistryOps 编解码(官方源码已核),ItemStack 列表可安全存档。 */
    public static final AttachmentType<java.util.List<net.minecraft.item.ItemStack>> SOULBOUND_STASH =
            AttachmentRegistry.<java.util.List<net.minecraft.item.ItemStack>>builder()
                    .persistent(net.minecraft.item.ItemStack.OPTIONAL_CODEC.listOf())
                    .initializer(java.util.ArrayList::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "soulbound_stash"));

    /** LEARNED_CLASSES: 玩家已学职业(有序,最多2),按学习顺序;槽位等级门控。 */
    public static final AttachmentType<java.util.List<String>> LEARNED_CLASSES =
            AttachmentRegistry.<java.util.List<String>>builder()
                    .persistent(Codec.STRING.listOf())
                    .initializer(java.util.ArrayList::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "learned_classes"));

    /** MONK_FIST_BONUS: 武僧空手击杀+吃材料累计的额外拳击伤害。 */
    public static final AttachmentType<Integer> MONK_FIST_BONUS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "monk_fist_bonus"));

    /** MONK_HP_BONUS: 武僧吃材料累计的额外生命上限(越吃越肥)。 */
    public static final AttachmentType<Integer> MONK_HP_BONUS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "monk_hp_bonus"));

    /**
     * MOB_ENHANCED: 标记某个怪物是否已被增强,避免反复 re-roll 随机药水。
     */
    public static final AttachmentType<Boolean> MOB_ENHANCED =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "mob_enhanced"));

    /** IS_ELITE: 该怪物为精英怪。 */
    public static final AttachmentType<Boolean> IS_ELITE =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_elite"));

    /** IS_BOSS: 该实体为(翻倍)Boss。 */
    public static final AttachmentType<Boolean> IS_BOSS =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_boss"));

    /** IS_MOB_BOSS: 该实体为"普通怪 BOSS 版"(区别于原版 Boss;仅此类挂自定义红色血条)。 */
    public static final AttachmentType<Boolean> IS_MOB_BOSS =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_mob_boss"));

    /** IS_PAIN: 该实体为长门(佩恩)Boss。 */
    public static final AttachmentType<Boolean> IS_PAIN =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_pain"));

    /** IS_HIM: 该实体为 HIM 突脸惊吓体(无 AI、无敌、无伤害、短暂存在)。 */
    public static final AttachmentType<Boolean> IS_HIM =
            AttachmentRegistry.<Boolean>builder()
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_him"));

    /** ACCESSORIES: 玩家饰品栏(神器),以 NBT 存档。死亡保留(与其它成长一致,否则死一次神器全没)。 */
    public static final AttachmentType<NbtCompound> ACCESSORIES =
            AttachmentRegistry.<NbtCompound>builder()
                    .persistent(NbtCompound.CODEC)
                    .initializer(NbtCompound::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "accessories"));

    /** NO_HEAL_UNTIL: 玩家禁疗截止的游戏时刻(world time)。 */
    public static final AttachmentType<Long> NO_HEAL_UNTIL =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "no_heal_until"));

    /** DISARM_COOLDOWN_UNTIL: 玩家被精英缴械的冷却截止游戏时刻。 */
    public static final AttachmentType<Long> DISARM_COOLDOWN_UNTIL =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "disarm_cooldown_until"));

    /** EMBER_READY_AT: 不灭余烬下一次可触发的游戏时刻。 */
    public static final AttachmentType<Long> EMBER_READY_AT =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "ember_ready_at"));

    /** TALENT_POINTS: 可用天赋点(随等级发放,死亡保留)。 */
    public static final AttachmentType<Integer> TALENT_POINTS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "talent_points"));

    /** TALENT_CLAIMED_LEVEL: 已据此发放过天赋点的最高等级(掉级后不重复发放)。 */
    public static final AttachmentType<Integer> TALENT_CLAIMED_LEVEL =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "talent_claimed_level"));

    /** TALENTS: 已点天赋节点 → 等级(rank),键为节点 id。 */
    public static final AttachmentType<java.util.Map<String, Integer>> TALENTS =
            AttachmentRegistry.<java.util.Map<String, Integer>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .initializer(java.util.HashMap::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "talents"));

    /** STARTING_CLASS_CHOSEN: 是否已完成开局选职(死亡保留,避免重复弹窗)。 */
    public static final AttachmentType<Boolean> STARTING_CLASS_CHOSEN =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "starting_class_chosen"));

    /** GOT_STARTING_KIT: 是否已领取开局赠礼(下界合金背包),每人只发一次(死亡保留,避免刷取)。 */
    public static final AttachmentType<Boolean> GOT_STARTING_KIT =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "got_starting_kit"));

    /** GOT_WELCOME_BOOKS: 是否已领取开局两本书(剧情/手册),每人只发一次(死亡保留,避免重复塞包)。 */
    public static final AttachmentType<Boolean> GOT_WELCOME_BOOKS =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "got_welcome_books"));

    /** GOT_STARTING_FOOD: 是否已领取开局口粮(面包),每人只发一次(死亡保留,避免刷取)。 */
    public static final AttachmentType<Boolean> GOT_STARTING_FOOD =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "got_starting_food"));

    /** GOT_STARTING_UPGRADES: 是否已领取开局背包升级(高级磁铁 + 高级喂食),每人只发一次(死亡保留)。
     *  独立于 GOT_STARTING_KIT —— 这样已进过服的老玩家下次登录仍能补发这两个升级。 */
    public static final AttachmentType<Boolean> GOT_STARTING_UPGRADES =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "got_starting_upgrades"));

    /** CREATIVE_ENTRIES(m155): 该玩家累计进入创造模式的次数(死亡保留,跨登录累计)。
     *  非豁免玩家第 2 次进入创造即被强制改回生存(见 CreativeWatchHandler)。 */
    public static final AttachmentType<Integer> CREATIVE_ENTRIES =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "creative_entries"));

    /** FIRST_HINTS(m420): 新手首次提示位掩码(1=永夜/2=神器/4=精英;死亡保留,只发一次)。 */
    public static final AttachmentType<Integer> FIRST_HINTS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "first_hints"));

    /** LOST_WEAPON_ENHANCE: 被夺且未找回的武器强化等级(供 /yongye recover 转移到新武器,损失 1/3)。 */
    public static final AttachmentType<Integer> LOST_WEAPON_ENHANCE =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "lost_weapon_enhance"));

    /** STOLE_GEAR: 该怪是否已抢过装备(防止一只怪累计抢走多名玩家的装备造成丢失)。 */
    public static final AttachmentType<Boolean> STOLE_GEAR =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "stole_gear"));

    /** ELITE_AFFIX: 精英词缀位掩码(1爆裂/2分裂/4嗜血/8剧毒光环/16召唤)。 */
    public static final AttachmentType<Integer> ELITE_AFFIX =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "elite_affix"));

    /** BEST_NIGHTFALL: 玩家曾达到的最高永夜层数(排行用,终身最佳)。 */
    public static final AttachmentType<Integer> BEST_NIGHTFALL =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "best_nightfall"));

    /** BEST_DAY: 玩家曾达到的最高游戏天数(排行用,终身最佳)。 */
    public static final AttachmentType<Integer> BEST_DAY =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "best_day"));

    /** GOT_CLASS_BOOK: 是否已发过「职业选择书」,每人只发一次(死亡保留,避免刷取)。 */
    public static final AttachmentType<Boolean> GOT_CLASS_BOOK =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL).initializer(() -> false).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "got_class_book"));

    /** WEAPON_SKILL_LV: 武器主动技能的升级等级,键为 WeaponSkill 枚举名(SLASH/DEVOUR/FINALITY)。死亡保留。 */
    public static final AttachmentType<java.util.Map<String, Integer>> WEAPON_SKILL_LV =
            AttachmentRegistry.<java.util.Map<String, Integer>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .initializer(java.util.HashMap::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "weapon_skill_lv"));

    /** VAULT_ITEMS(m356): 材料仓库——键=物品 id(技能书追加 #等级),值=数量(long 无限堆叠)。死亡保留。 */
    public static final AttachmentType<java.util.Map<String, Long>> VAULT_ITEMS =
            AttachmentRegistry.<java.util.Map<String, Long>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG))
                    .initializer(java.util.HashMap::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "vault_items"));

    /** BOSS_KILL_MAP(m351): Boss 图鉴逐 BOSS 击杀计数,键=BossAtlasPayload 槽位 id
     *  (red_spider/death_mage/fire_phoenix/toro_dragon/anubis/pain/ender_dragon)。死亡保留。 */
    public static final AttachmentType<java.util.Map<String, Integer>> BOSS_KILL_MAP =
            AttachmentRegistry.<java.util.Map<String, Integer>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .initializer(java.util.HashMap::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "boss_kill_map"));

    /** BOUNTY_STATE(m364): 每日悬赏状态串「day;streak;type,target,prog,done;×3」。死亡保留(坚守进度另行清零)。 */
    public static final AttachmentType<String> BOUNTY_STATE =
            AttachmentRegistry.<String>builder()
                    .persistent(Codec.STRING)
                    .initializer(() -> "")
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "bounty_state"));

    /** ENHANCE_PROTECTED(m158): 玩家已使用强化保护卷、待生效中——下一次「会碎裂的强化失败」将被抵挡并清除本标志。 */
    public static final AttachmentType<Boolean> ENHANCE_PROTECTED =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL).initializer(() -> false).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "enhance_protected"));

    /** TOTAL_KILLS(m288): 累计杀怪总数(战况看板显示;跨登录累计、死亡保留;口径=Monster+玩家击杀,同保护卷)。 */
    public static final AttachmentType<Long> TOTAL_KILLS =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG).initializer(() -> 0L).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "total_kills"));

    /** SCROLL_KILLS(m159): 朝下一个保护卷累计的击杀数(每次兑换后扣除当前阈值,跨登录累计、死亡保留)。 */
    public static final AttachmentType<Integer> SCROLL_KILLS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "scroll_kills"));

    /** SCROLL_EXCHANGES(m159): 已通过击杀兑换的保护卷个数;当前阈值 = protectScrollKillBase × 2^此值(翻倍)。 */
    public static final AttachmentType<Integer> SCROLL_EXCHANGES =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "scroll_exchanges"));

    /** 末地末影龙已烧掉的命数(m183 三条命;挂在龙实体上持久,水晶重召的新龙从 0 开始)。 */
    public static final AttachmentType<Integer> DRAGON_LIVES_USED =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "dragon_lives_used"));

    /** 末地末影龙已完成首次强化(m183;门住"重载再补满血",属性修饰本身幂等不用门)。 */
    public static final AttachmentType<Boolean> END_DRAGON_BUFFED =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL).initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "end_dragon_buffed"));

    /** HUNT_MEDALS(m366): 猎杀勋章层数表(键=勋章id attack/health/speed/armor/atkspeed/tough,值=层数)。
     *  独立于技能书/强化/职业的第四条永久成长线——不写 WEAPON_SKILL_LV / ENHANCE_LEVEL / LEARNED_*,
     *  属性走独立 medal_* 修饰符,且动态对位(DynamicScaling)把勋章乘区从基准中剔除=真收益不喂怪。 */
    public static final AttachmentType<java.util.Map<String, Integer>> HUNT_MEDALS =
            AttachmentRegistry.<java.util.Map<String, Integer>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .initializer(java.util.HashMap::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "hunt_medals"));

    /** HUNT_KILLS(m366): 当前里程碑周期内已累计的击杀数(达标触发三选一后扣除本档阈值,余数滚入下一周期)。
     *  刻意不复用 TOTAL_KILLS——老存档玩家已有几千击杀,直接复用会开局连弹几十次三选一;新线从 0 起最干净。 */
    public static final AttachmentType<Integer> HUNT_KILLS =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "hunt_kills"));

    /** HUNT_MILESTONE(m366): 已完成(选完卡)的里程碑次数;第 k 次所需击杀 = base + k×growth(线性递增,后期不刷屏)。 */
    public static final AttachmentType<Integer> HUNT_MILESTONE =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT).initializer(() -> 0).copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "hunt_milestone"));

    /** HUNT_PENDING(m366): 当前待选的三张勋章 id(逗号拼;""=无待选)。persistent=掉线/重启不丢,JOIN 时补推弹屏。 */
    public static final AttachmentType<String> HUNT_PENDING =
            AttachmentRegistry.<String>builder()
                    .persistent(Codec.STRING).initializer(() -> "").copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "hunt_pending"));

    public static void init() {
        Yongye.LOGGER.info("[夜蚀] 数据附着已注册");
    }
}
