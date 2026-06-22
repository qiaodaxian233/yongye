package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.PlayerClass;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天赋树系统。
 * 发点:等级达到 talentStartLevel 后,每升 1 级发 talentPointsPerLevel 点;
 *      用 TALENT_CLAIMED_LEVEL 记录最高已发等级,掉级不重复发、已得点不回收(死亡也保留)。
 * 加点:玩家用 /talent learn <id> 在【已习得职业】的天赋树里投点,带前置门控。
 * 生效:天赋的属性修饰 / 持续增益挂在 ClassManager.applyClasses 的重刷链路里,
 *      仅对【当前够等级生效】的职业应用;职业因掉级失效时其天赋休眠(点数仍保留)。
 */
public final class TalentManager {
    private TalentManager() {}

    // ===== 属性调色板(下标即节点效果引用的 idx)=====
    private record AttrSlot(RegistryEntry<EntityAttribute> attr, String key) {}
    private static final AttrSlot[] ATTRS = {
            new AttrSlot(EntityAttributes.GENERIC_MAX_HEALTH, "hp"),             // 0
            new AttrSlot(EntityAttributes.GENERIC_ARMOR, "armor"),              // 1
            new AttrSlot(EntityAttributes.GENERIC_ATTACK_DAMAGE, "atk"),        // 2
            new AttrSlot(EntityAttributes.GENERIC_MOVEMENT_SPEED, "speed"),     // 3
            new AttrSlot(EntityAttributes.GENERIC_ATTACK_SPEED, "aspd"),        // 4
            new AttrSlot(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, "kbr"), // 5
            new AttrSlot(EntityAttributes.GENERIC_ARMOR_TOUGHNESS, "tough"),    // 6
            new AttrSlot(EntityAttributes.GENERIC_LUCK, "luck"),                // 7
            new AttrSlot(EntityAttributes.PLAYER_BLOCK_INTERACTION_RANGE, "breach"),  // 8
            new AttrSlot(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE, "ereach"), // 9
    };

    private record Eff(int idx, double perRank, Operation op) {}

    /** 天赋节点。buff != null 表示"技能"型,学会后持续刷新对应增益(等级 = rank-1)。 */
    private record Node(String id, String cn, String desc, int maxRank, String prereq,
                        List<Eff> effs, RegistryEntry<StatusEffect> buff) {}

    private static Node attr(String id, String cn, String desc, int maxRank, String prereq, Eff... effs) {
        return new Node(id, cn, desc, maxRank, prereq, List.of(effs), null);
    }
    private static Node skill(String id, String cn, String desc, int maxRank, String prereq,
                              RegistryEntry<StatusEffect> buff, Eff... effs) {
        return new Node(id, cn, desc, maxRank, prereq, List.of(effs), buff);
    }

    private static final Map<PlayerClass, List<Node>> TREES = buildTrees();

    /** 供天赋界面渲染用的只读节点视图(TalentManager 是通用代码,客户端可直接调用)。 */
    public record NodeView(String id, String cn, String desc, int maxRank, String prereq, boolean isSkill) {}

    /** 取某职业的天赋树(展示用)。 */
    public static List<NodeView> treeView(PlayerClass c) {
        List<NodeView> out = new java.util.ArrayList<>();
        for (Node n : TREES.getOrDefault(c, List.of())) {
            out.add(new NodeView(n.id(), n.cn(), n.desc(), n.maxRank(), n.prereq(), n.buff() != null));
        }
        return out;
    }

    private static Map<PlayerClass, List<Node>> buildTrees() {
        Map<PlayerClass, List<Node>> m = new LinkedHashMap<>();
        m.put(PlayerClass.TANK, List.of(
                attr("t_hp", "铁壁", "+生命", 5, null, new Eff(0, 6, Operation.ADD_VALUE)),
                attr("t_armor", "重甲", "+护甲", 5, null, new Eff(1, 2, Operation.ADD_VALUE)),
                attr("t_tough", "坚韧", "+护甲韧性", 3, "t_armor", new Eff(6, 2, Operation.ADD_VALUE)),
                attr("t_kbr", "如山", "+击退抗性", 2, "t_hp", new Eff(5, 0.3, Operation.ADD_VALUE)),
                skill("t_guard", "守护者", "持续抗性(随等级提升)", 2, "t_tough", StatusEffects.RESISTANCE)
        ));
        m.put(PlayerClass.WARRIOR, List.of(
                attr("w_atk", "战意", "+攻击", 5, null, new Eff(2, 1, Operation.ADD_VALUE)),
                attr("w_hp", "血勇", "+生命", 5, null, new Eff(0, 4, Operation.ADD_VALUE)),
                attr("w_speed", "突进", "+移速", 3, "w_hp", new Eff(3, 0.05, Operation.ADD_MULTIPLIED_BASE)),
                attr("w_aspd", "连击", "+攻速", 3, "w_atk", new Eff(4, 0.1, Operation.ADD_MULTIPLIED_BASE)),
                skill("w_rage", "狂战", "持续力量", 2, "w_atk", StatusEffects.STRENGTH)
        ));
        m.put(PlayerClass.WARLOCK, List.of(
                attr("k_atk", "咒伤", "+攻击", 5, null, new Eff(2, 1, Operation.ADD_VALUE)),
                attr("k_ereach", "法力延伸", "+实体交互距离", 3, null, new Eff(9, 1, Operation.ADD_VALUE)),
                attr("k_luck", "诅咒", "+幸运", 3, "k_atk", new Eff(7, 1, Operation.ADD_VALUE)),
                attr("k_breach", "穿透", "+方块交互距离", 3, "k_ereach", new Eff(8, 1, Operation.ADD_VALUE)),
                skill("k_haste", "急速咏唱", "持续急迫", 2, "k_atk", StatusEffects.HASTE)
        ));
        m.put(PlayerClass.SWORDSMAN, List.of(
                attr("s_atk", "锋锐", "+攻击", 5, null, new Eff(2, 1.2, Operation.ADD_VALUE)),
                attr("s_aspd", "疾斩", "+攻速", 3, null, new Eff(4, 0.12, Operation.ADD_MULTIPLIED_BASE)),
                attr("s_speed", "身法", "+移速", 3, "s_aspd", new Eff(3, 0.05, Operation.ADD_MULTIPLIED_BASE)),
                attr("s_reach", "突刺", "+实体交互距离", 2, "s_atk", new Eff(9, 0.5, Operation.ADD_VALUE)),
                skill("s_qi", "剑气", "持续力量", 2, "s_atk", StatusEffects.STRENGTH)
        ));
        m.put(PlayerClass.MONK, List.of(
                attr("m_hp", "气血", "+生命", 5, null, new Eff(0, 5, Operation.ADD_VALUE)),
                attr("m_armor", "铁布衫", "+护甲", 4, null, new Eff(1, 2, Operation.ADD_VALUE)),
                attr("m_aspd", "连拳", "+攻速", 3, "m_hp", new Eff(4, 0.12, Operation.ADD_MULTIPLIED_BASE)),
                attr("m_kbr", "扎马", "+击退抗性", 2, "m_armor", new Eff(5, 0.3, Operation.ADD_VALUE)),
                skill("m_breath", "吐纳", "持续生命恢复", 2, "m_armor", StatusEffects.REGENERATION)
        ));
        m.put(PlayerClass.ASSASSIN, List.of(
                attr("a_atk", "暗袭", "+攻击", 5, null, new Eff(2, 1.2, Operation.ADD_VALUE)),
                attr("a_speed", "疾影", "+移速", 4, null, new Eff(3, 0.06, Operation.ADD_MULTIPLIED_BASE)),
                attr("a_aspd", "刺击", "+攻速", 3, "a_atk", new Eff(4, 0.12, Operation.ADD_MULTIPLIED_BASE)),
                attr("a_luck", "暗运", "+幸运", 3, "a_speed", new Eff(7, 1, Operation.ADD_VALUE)),
                skill("a_sprint", "疾风步", "持续迅捷", 2, "a_speed", StatusEffects.SPEED)
        ));
        return m;
    }

    private record Found(PlayerClass cls, Node node) {}
    private static Found findNode(String id) {
        for (Map.Entry<PlayerClass, List<Node>> e : TREES.entrySet()) {
            for (Node n : e.getValue()) {
                if (n.id().equals(id)) return new Found(e.getKey(), n);
            }
        }
        return null;
    }

    private static Identifier modId(PlayerClass cls, Node n, String attrKey) {
        return Identifier.of(Yongye.MOD_ID, "talent_" + cls.id + "_" + n.id() + "_" + attrKey);
    }

    private static void msg(ServerPlayerEntity p, String s, Formatting f) {
        p.sendMessage(Text.literal(s).formatted(f), false);
    }

    // ===== 发点(独立 tick)=====
    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 20 != 0) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableTalents) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) grantPoints(p, cfg);
        });
        Yongye.LOGGER.info("[永夜] 天赋系统已挂载");
    }

    private static void grantPoints(ServerPlayerEntity p, YongyeConfig cfg) {
        int lvl = p.experienceLevel;
        int claimed = p.getAttachedOrElse(ModAttachments.TALENT_CLAIMED_LEVEL, 0);
        if (lvl <= claimed) return;
        // 统计 (claimed, lvl] 区间内、且 >= 起始等级 的层数
        int from = Math.max(claimed + 1, cfg.talentStartLevel);
        int newLevels = Math.max(0, lvl - from + 1);
        int gain = newLevels * Math.max(0, cfg.talentPointsPerLevel);
        p.setAttached(ModAttachments.TALENT_CLAIMED_LEVEL, lvl);
        if (gain > 0) {
            int pts = p.getAttachedOrElse(ModAttachments.TALENT_POINTS, 0) + gain;
            p.setAttached(ModAttachments.TALENT_POINTS, pts);
            msg(p, "获得 " + gain + " 天赋点(共 " + pts + ")  打开背包点【天赋】或 /talent 加点", Formatting.AQUA);
            com.yongye.network.YongyeNet.sendTalents(p); // 同步给客户端天赋界面
        }
    }

    // ===== 生效(由 ClassManager.applyClasses 调用,active 为当前够等级的职业)=====
    public static void applyTalents(ServerPlayerEntity p, List<PlayerClass> active) {
        Map<String, Integer> learned = p.getAttachedOrElse(ModAttachments.TALENTS, Map.of());
        for (Map.Entry<PlayerClass, List<Node>> e : TREES.entrySet()) {
            PlayerClass cls = e.getKey();
            boolean on = active.contains(cls);
            for (Node n : e.getValue()) {
                int rank = learned.getOrDefault(n.id(), 0);
                for (Eff ef : n.effs()) {
                    EntityAttributeInstance inst = p.getAttributeInstance(ATTRS[ef.idx()].attr());
                    if (inst == null) continue;
                    Identifier id = modId(cls, n, ATTRS[ef.idx()].key());
                    inst.removeModifier(id); // 先清旧的,避免叠加
                    if (on && rank > 0) {
                        inst.addTemporaryModifier(new EntityAttributeModifier(id, ef.perRank() * rank, ef.op()));
                    }
                }
                if (n.buff() != null && on && rank > 0) {
                    p.addStatusEffect(new StatusEffectInstance(n.buff(), 40, rank - 1, true, false, false));
                }
            }
        }
    }

    // ===== /talent 命令逻辑(玩家自身)=====
    public static int overview(ServerPlayerEntity p) {
        if (!YongyeConfig.get().enableTalents) { msg(p, "天赋系统未启用", Formatting.RED); return 0; }
        int pts = p.getAttachedOrElse(ModAttachments.TALENT_POINTS, 0);
        msg(p, "== 天赋 ==  可用点数: " + pts, Formatting.GOLD);
        List<String> classes = ClassManager.learnedList(p);
        if (classes.isEmpty()) {
            msg(p, "尚未习得职业(精英掉职业书,等级 " + YongyeConfig.get().classLevel1 + " 习得)", Formatting.GRAY);
            return 1;
        }
        Map<String, Integer> learned = p.getAttachedOrElse(ModAttachments.TALENTS, Map.of());
        for (String cid : classes) {
            PlayerClass c = PlayerClass.byId(cid);
            if (c == null) continue;
            int spent = 0;
            StringBuilder sb = new StringBuilder();
            for (Node n : TREES.getOrDefault(c, List.of())) {
                int r = learned.getOrDefault(n.id(), 0);
                if (r > 0) {
                    spent += r;
                    sb.append(n.cn()).append(" ").append(r).append("/").append(n.maxRank()).append("  ");
                }
            }
            msg(p, "【" + c.cn + "】已投入 " + spent + " 点: " + (spent > 0 ? sb.toString() : "—"), Formatting.AQUA);
        }
        msg(p, "/talent list 看天赋  ·  /talent learn <id> 加点  ·  /talent reset 重置", Formatting.DARK_GRAY);
        return 1;
    }

    public static int list(ServerPlayerEntity p) {
        if (!YongyeConfig.get().enableTalents) { msg(p, "天赋系统未启用", Formatting.RED); return 0; }
        List<String> classes = ClassManager.learnedList(p);
        if (classes.isEmpty()) { msg(p, "尚未习得职业", Formatting.GRAY); return 1; }
        Map<String, Integer> learned = p.getAttachedOrElse(ModAttachments.TALENTS, Map.of());
        for (String cid : classes) {
            PlayerClass c = PlayerClass.byId(cid);
            if (c == null) continue;
            msg(p, "== 【" + c.cn + "】天赋树 ==", Formatting.GOLD);
            for (Node n : TREES.getOrDefault(c, List.of())) {
                int r = learned.getOrDefault(n.id(), 0);
                String pre = n.prereq();
                boolean locked = pre != null && learned.getOrDefault(pre, 0) <= 0;
                Formatting col = locked ? Formatting.DARK_GRAY : (r >= n.maxRank() ? Formatting.GREEN : Formatting.WHITE);
                String preTxt = "";
                if (pre != null) {
                    Found pf = findNode(pre);
                    preTxt = " [需:" + (pf != null ? pf.node().cn() : pre) + "]";
                }
                msg(p, "  " + n.id() + "  " + n.cn() + " " + r + "/" + n.maxRank() + "  " + n.desc() + preTxt, col);
            }
        }
        return 1;
    }

    public static int learn(ServerPlayerEntity p, String nodeId) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableTalents) { msg(p, "天赋系统未启用", Formatting.RED); return 0; }
        Found f = findNode(nodeId);
        if (f == null) { msg(p, "未知天赋: " + nodeId + "(用 /talent list 查看)", Formatting.RED); return 0; }
        if (!ClassManager.learnedList(p).contains(f.cls().id)) {
            msg(p, "你尚未习得职业【" + f.cls().cn + "】,无法点该天赋", Formatting.RED);
            return 0;
        }
        Map<String, Integer> learned = new HashMap<>(p.getAttachedOrElse(ModAttachments.TALENTS, Map.of()));
        int rank = learned.getOrDefault(nodeId, 0);
        if (rank >= f.node().maxRank()) {
            msg(p, "【" + f.node().cn() + "】已满级(" + rank + "/" + f.node().maxRank() + ")", Formatting.YELLOW);
            return 0;
        }
        String pre = f.node().prereq();
        if (pre != null && learned.getOrDefault(pre, 0) <= 0) {
            Found pf = findNode(pre);
            msg(p, "需先学习前置天赋【" + (pf != null ? pf.node().cn() : pre) + "】", Formatting.RED);
            return 0;
        }
        int pts = p.getAttachedOrElse(ModAttachments.TALENT_POINTS, 0);
        int cost = 1;
        if (pts < cost) { msg(p, "天赋点不足(需 " + cost + ",余 " + pts + ")", Formatting.RED); return 0; }
        learned.put(nodeId, rank + 1);
        p.setAttached(ModAttachments.TALENTS, learned);
        p.setAttached(ModAttachments.TALENT_POINTS, pts - cost);
        ClassManager.applyClasses(p); // 重刷(内部会调用 applyTalents)
        msg(p, "【" + f.node().cn() + "】 " + (rank + 1) + "/" + f.node().maxRank() + "  余 " + (pts - cost) + " 点", Formatting.GREEN);
        return 1;
    }

    public static int reset(ServerPlayerEntity p) {
        if (!YongyeConfig.get().enableTalents) { msg(p, "天赋系统未启用", Formatting.RED); return 0; }
        Map<String, Integer> learned = p.getAttachedOrElse(ModAttachments.TALENTS, Map.of());
        int refund = 0;
        for (int r : learned.values()) refund += r; // 每级 1 点
        int pts = p.getAttachedOrElse(ModAttachments.TALENT_POINTS, 0) + refund;
        p.setAttached(ModAttachments.TALENTS, new HashMap<>());
        p.setAttached(ModAttachments.TALENT_POINTS, pts);
        ClassManager.applyClasses(p);
        msg(p, "已重置天赋,返还 " + refund + " 点(共 " + pts + ")", Formatting.AQUA);
        return 1;
    }

    public static int info(ServerPlayerEntity p, String nodeId) {
        Found f = findNode(nodeId);
        if (f == null) { msg(p, "未知天赋: " + nodeId, Formatting.RED); return 0; }
        Node n = f.node();
        Map<String, Integer> learned = p.getAttachedOrElse(ModAttachments.TALENTS, Map.of());
        int r = learned.getOrDefault(nodeId, 0);
        msg(p, "【" + f.cls().cn + "·" + n.cn() + "】 " + r + "/" + n.maxRank() + "  " + n.desc()
                + (n.buff() != null ? "(技能型)" : ""), Formatting.GOLD);
        if (n.prereq() != null) {
            Found pf = findNode(n.prereq());
            msg(p, "前置: " + (pf != null ? pf.node().cn() : n.prereq()), Formatting.GRAY);
        }
        return 1;
    }
}
