package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.network.OpenMedalChoicePayload;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/**
 * 猎杀勋章(m366,作者定稿:「升级三选一保留,但触发是击杀数里程碑不是经验;加在长线上的新东西」,甲案):
 *  - 累计击杀达到里程碑 → 弹三选一卡 → 选一枚**永久小加成**勋章,层数可无限叠;
 *  - 阈值线性递增(第 k 次 = base + k×growth,默认 10/6 → 10,16,22,28…),后期越杀越久才弹,不像经验刷屏;
 *  - 触发口径 = Monster + creditedKiller(m300 统一归属,召唤物击杀记主人);
 *  - HUD 看板常显「再杀 N 只」牵引(HudInfoPayload 尾加 huntRemain);掉线不丢(HUNT_PENDING 持久,JOIN 补推)。
 *
 * 隔离(作者最在意的「不互相污染」)怎么落地:
 *  1. 独立记账——层数存 HUNT_MEDALS 附件,绝不写 WEAPON_SKILL_LV / ENHANCE_LEVEL / LEARNED_* 那些旧成长数据;
 *  2. 独立修饰符——medal_* 前缀 ADD_MULTIPLIED_TOTAL,addTemporaryModifier 不持久化,每秒重挂(照 BlightSetHandler,
 *     值不变不重挂防血条闪;重生/登录由周期 tick 自动补,零额外钩子);
 *  3. 动态对位剔除——DynamicScaling 按玩家攻击/血量对位怪时,把勋章乘区从基准中**除掉**(attackMultOf/healthMultOf),
 *     怪不因勋章跟涨 → 勋章是实打实的「净收益」,这才是奖励的意义;也反向保证勋章不喂养怪物曲线。
 */
public final class HuntMedalHandler {
    private HuntMedalHandler() {}

    // 六种勋章的展示元数据(并行数组;坑清单铁律:跨类访问不用 record)。客户端 MedalChoiceScreen 直接引用。
    public static final String[] IDS    = {"attack", "health", "speed", "armor", "atkspeed", "tough"};
    public static final String[] NAMES  = {"猛攻勋章", "体魄勋章", "迅捷勋章", "坚壁勋章", "疾手勋章", "不屈勋章"};
    public static final String[] STATS  = {"攻击伤害", "最大生命", "移动速度", "护甲值", "攻击速度", "护甲韧性"};
    /** 卡面主题色(ARGB,客户端画卡框/标题用)。 */
    public static final int[] COLORS   = {0xFFFF5555, 0xFF55FF55, 0xFF55FFFF, 0xFFC0C0C0, 0xFFFFFF55, 0xFF2EC4B6};

    private static final Identifier[] MOD_IDS = {
            Identifier.of(Yongye.MOD_ID, "medal_attack"),
            Identifier.of(Yongye.MOD_ID, "medal_health"),
            Identifier.of(Yongye.MOD_ID, "medal_speed"),
            Identifier.of(Yongye.MOD_ID, "medal_armor"),
            Identifier.of(Yongye.MOD_ID, "medal_atkspeed"),
            Identifier.of(Yongye.MOD_ID, "medal_tough")
    };

    public static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return -1;
    }

    /** 每层百分比(读实时配置)。 */
    public static double pctOf(YongyeConfig c, String id) {
        return switch (id) {
            case "attack"   -> c.huntMedalAttackPct;
            case "health"   -> c.huntMedalHealthPct;
            case "speed"    -> c.huntMedalSpeedPct;
            case "armor"    -> c.huntMedalArmorPct;
            case "atkspeed" -> c.huntMedalAtkSpeedPct;
            case "tough"    -> c.huntMedalToughnessPct;
            default -> 0;
        };
    }

    /** 第 milestone 次(0 起)里程碑所需击杀数:base + k×growth,线性递增。 */
    public static int intervalOf(YongyeConfig c, int milestone) {
        return Math.max(1, c.huntMilestoneBase + milestone * c.huntMilestoneGrowth);
    }

    private static int medalLevel(PlayerEntity p, String id) {
        return p.getAttachedOrElse(ModAttachments.HUNT_MEDALS, java.util.Map.of()).getOrDefault(id, 0);
    }

    public static void register() {
        // 击杀累计:口径与看板/保护卷一致(Monster + m300 统一归属)
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity instanceof Monster)) return;
            if (!YongyeConfig.get().enableHuntMedal) return;
            ServerPlayerEntity killer = SummonKillCredit.creditedKiller(source);
            if (killer == null) return;
            onKill(killer);
        });

        // 属性周期重挂(照 BlightSetHandler:每秒/值不变不重挂;temporary 修饰符重生登录自动由此补上)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            YongyeConfig c = YongyeConfig.get();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) applyAll(p, c);
        });

        // 掉线/重启不丢三选一:登录时有待选就补推弹屏
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity p = handler.getPlayer();   // 在树先例写法(Yongye.java JOIN 同款)
            if (!YongyeConfig.get().enableHuntMedal) return;
            if (!p.getAttachedOrElse(ModAttachments.HUNT_PENDING, "").isEmpty()) {
                ServerPlayNetworking.send(p, new OpenMedalChoicePayload(buildData(p)));
            }
        });

        Yongye.LOGGER.info("[夜蚀] 猎杀勋章已挂载(击杀里程碑三选一,独立成长线)");
    }

    private static void onKill(ServerPlayerEntity p) {
        YongyeConfig c = YongyeConfig.get();
        int kills = p.getAttachedOrElse(ModAttachments.HUNT_KILLS, 0) + 1;
        p.setAttached(ModAttachments.HUNT_KILLS, kills);
        // 有待选卡时只累计不再弹(一次处理一张;余数留到选完后连锁判定)
        if (!p.getAttachedOrElse(ModAttachments.HUNT_PENDING, "").isEmpty()) return;
        int ms = p.getAttachedOrElse(ModAttachments.HUNT_MILESTONE, 0);
        int need = intervalOf(c, ms);
        if (kills >= need) {
            p.setAttached(ModAttachments.HUNT_KILLS, kills - need);   // 扣本档阈值,余数滚入下一周期
            rollChoices(p);
        }
    }

    /** 从六种里抽三种不重复,存 HUNT_PENDING 并推弹屏 + 播报。 */
    private static void rollChoices(ServerPlayerEntity p) {
        int[] idx = {0, 1, 2, 3, 4, 5};
        for (int i = idx.length - 1; i > 0; i--) {           // Fisher-Yates
            int j = p.getRandom().nextInt(i + 1);
            int t = idx[i]; idx[i] = idx[j]; idx[j] = t;
        }
        String pending = IDS[idx[0]] + "," + IDS[idx[1]] + "," + IDS[idx[2]];
        p.setAttached(ModAttachments.HUNT_PENDING, pending);
        ServerPlayNetworking.send(p, new OpenMedalChoicePayload(buildData(p)));
        p.sendMessage(Text.literal("⚔ 猎杀里程碑达成!选择一枚永久勋章").formatted(Formatting.GOLD), false);
        p.getWorld().playSound(null, p.getBlockPos(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.9f, 1.2f);
    }

    /** 拼三选一展示串 "id:当前层数:每层pct|…"(pct 用服务端配置,客户端纯解析)。 */
    public static String buildData(ServerPlayerEntity p) {
        YongyeConfig c = YongyeConfig.get();
        String pending = p.getAttachedOrElse(ModAttachments.HUNT_PENDING, "");
        if (pending.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String id : pending.split(",")) {
            if (indexOf(id) < 0) continue;   // 防脏数据
            if (sb.length() > 0) sb.append('|');
            sb.append(id).append(':').append(medalLevel(p, id)).append(':').append(pctOf(c, id));
        }
        return sb.toString();
    }

    /** 玩家选定(YongyeNet C2S 调):服务端权威复核候选 → 层数+1 → 即时重挂 → 连锁判定。 */
    public static void choose(ServerPlayerEntity p, String id) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableHuntMedal) return;
        String pending = p.getAttachedOrElse(ModAttachments.HUNT_PENDING, "");
        if (pending.isEmpty()) return;
        boolean legal = false;
        for (String s : pending.split(",")) if (s.equals(id)) { legal = true; break; }
        if (!legal) return;   // 不在候选里=客户端造假,静默忽略
        int i = indexOf(id);
        if (i < 0) return;
        // 层数+1(拷贝新 Map 再 setAttached,保证落盘)
        java.util.HashMap<String, Integer> m =
                new java.util.HashMap<>(p.getAttachedOrElse(ModAttachments.HUNT_MEDALS, java.util.Map.of()));
        int lv = m.getOrDefault(id, 0) + 1;
        m.put(id, lv);
        p.setAttached(ModAttachments.HUNT_MEDALS, m);
        p.setAttached(ModAttachments.HUNT_PENDING, "");
        int ms = p.getAttachedOrElse(ModAttachments.HUNT_MILESTONE, 0) + 1;
        p.setAttached(ModAttachments.HUNT_MILESTONE, ms);
        applyAll(p, c);   // 即时生效,不等下个整秒
        p.sendMessage(Text.literal("◆ 获得【" + NAMES[i] + "】Lv." + lv + " —— "
                        + STATS[i] + " +" + trim(pctOf(c, id) * lv) + "%(永久)")
                .formatted(Formatting.GOLD), false);
        p.getWorld().playSound(null, p.getBlockPos(),
                SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.6f, 1.6f);
        // 连锁:选卡期间攒的击杀若已够下一档,立刻再弹一张(玩家逐张选,不会漏)
        int kills = p.getAttachedOrElse(ModAttachments.HUNT_KILLS, 0);
        int need = intervalOf(c, ms);
        if (kills >= need) {
            p.setAttached(ModAttachments.HUNT_KILLS, kills - need);
            rollChoices(p);
        }
    }

    /** 六项属性重挂(独立 medal_* 修饰符;开关关闭=全 0 即卸下)。 */
    public static void applyAll(ServerPlayerEntity p, YongyeConfig c) {
        boolean on = c.enableHuntMedal;
        apply(p, EntityAttributes.GENERIC_ATTACK_DAMAGE,  MOD_IDS[0], on ? medalLevel(p, "attack")   * c.huntMedalAttackPct    / 100.0 : 0);
        apply(p, EntityAttributes.GENERIC_MAX_HEALTH,     MOD_IDS[1], on ? medalLevel(p, "health")   * c.huntMedalHealthPct    / 100.0 : 0);
        apply(p, EntityAttributes.GENERIC_MOVEMENT_SPEED, MOD_IDS[2], on ? medalLevel(p, "speed")    * c.huntMedalSpeedPct     / 100.0 : 0);
        apply(p, EntityAttributes.GENERIC_ARMOR,          MOD_IDS[3], on ? medalLevel(p, "armor")    * c.huntMedalArmorPct     / 100.0 : 0);
        apply(p, EntityAttributes.GENERIC_ATTACK_SPEED,   MOD_IDS[4], on ? medalLevel(p, "atkspeed") * c.huntMedalAtkSpeedPct  / 100.0 : 0);
        apply(p, EntityAttributes.GENERIC_ARMOR_TOUGHNESS, MOD_IDS[5], on ? medalLevel(p, "tough")   * c.huntMedalToughnessPct / 100.0 : 0);
    }

    private static void apply(ServerPlayerEntity p,
                              net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> attr,
                              Identifier id, double frac) {
        EntityAttributeInstance inst = p.getAttributeInstance(attr);
        if (inst == null) return;
        EntityAttributeModifier old = inst.getModifier(id);
        if (old != null && Math.abs(old.value() - frac) < 1e-9) return;   // 值没变不重挂(生命上限重挂会闪血条)
        inst.removeModifier(id);
        if (frac > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    id, frac, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** 动态对位剔除:玩家攻击终值 ÷ 此乘数 = 剔掉勋章后的基准(勋章走 MULTIPLIED_TOTAL 独立因子,除法精确还原)。 */
    public static double attackMultOf(PlayerEntity p) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableHuntMedal) return 1.0;
        return 1.0 + medalLevel(p, "attack") * c.huntMedalAttackPct / 100.0;
    }

    /** 动态对位剔除(血量)。 */
    public static double healthMultOf(PlayerEntity p) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableHuntMedal) return 1.0;
        return 1.0 + medalLevel(p, "health") * c.huntMedalHealthPct / 100.0;
    }

    /** 看板 HUD 字段:-1=系统关闭(整行不画) / -2=有待选卡(金字提醒) / ≥0=距下次里程碑剩余击杀。 */
    public static int hudRemain(ServerPlayerEntity p) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableHuntMedal) return -1;
        if (!p.getAttachedOrElse(ModAttachments.HUNT_PENDING, "").isEmpty()) return -2;
        int ms = p.getAttachedOrElse(ModAttachments.HUNT_MILESTONE, 0);
        int kills = p.getAttachedOrElse(ModAttachments.HUNT_KILLS, 0);
        return Math.max(0, intervalOf(c, ms) - kills);
    }

    /** 百分比显示去尾零(2.0→2、1.5→1.5)。 */
    public static String trim(double v) {
        return (v == Math.floor(v)) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
