package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 自动卷轴执行器(m276)。两条独立节拍:
 *   自动强化:每 autoEnhanceIntervalTicks——深扫背包(含潜影盒)吞掉全部强化材料,
 *     强化主手(不可强化则强化等级最低的一件身上盔甲);走 EquipmentEnhancer.attempt 正规管线,
 *     碎裂/保护卷(m271 被动)/成功率全部与手动强化一致,自动化不解锁任何超模路径。
 *   自动吃书:每 autoBookIntervalTicks——深扫找一本技能书/血量书研读(满级的跳过继续找下一本)。
 * 到期各自播报一次「效果结束」。
 */
public final class AutoScrollHandler {
    private AutoScrollHandler() {}

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    /** 上一轮是否生效(transient):由「生效→失效」的边沿发一次结束播报。 */
    private static final Map<UUID, Boolean> WAS_ENHANCE = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_BOOK = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig c = YongyeConfig.get();
            long ticks = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                long now = p.getWorld().getTime();

                boolean enhActive = now < p.getAttachedOrElse(ModAttachments.AUTO_ENHANCE_UNTIL, 0L);
                if (enhActive && ticks % Math.max(20, c.autoEnhanceIntervalTicks) == 0) autoEnhance(p);
                edge(WAS_ENHANCE, p, enhActive, "自动强化卷 效果结束");

                boolean bookActive = now < p.getAttachedOrElse(ModAttachments.AUTO_BOOK_UNTIL, 0L);
                if (bookActive && ticks % Math.max(10, c.autoBookIntervalTicks) == 0) autoBook(p);
                edge(WAS_BOOK, p, bookActive, "自动吃书卷 效果结束");
            }
        });
        Yongye.LOGGER.info("[夜蚀] 自动卷轴执行器已挂载");
    }

    private static void edge(Map<UUID, Boolean> was, ServerPlayerEntity p, boolean active, String endMsg) {
        Boolean prev = was.put(p.getUuid(), active);
        if (prev != null && prev && !active) {
            p.sendMessage(Text.literal(endMsg).formatted(Formatting.GRAY), true);
            p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.5f, 0.7f);
        }
    }

    // ---------------- 自动强化 ----------------

    private static void autoEnhance(ServerPlayerEntity p) {
        // ① 选目标:主手可强化就是它;否则身上盔甲里等级最低的一件
        EquipmentSlot target = null;
        ItemStack main = p.getEquippedStack(EquipmentSlot.MAINHAND);
        if (!main.isEmpty() && EquipmentEnhancer.isEnhanceable(main.getItem())) {
            target = EquipmentSlot.MAINHAND;
        } else {
            int best = Integer.MAX_VALUE;
            for (EquipmentSlot s : ARMOR) {
                ItemStack st = p.getEquippedStack(s);
                if (st.isEmpty() || !EquipmentEnhancer.isEnhanceable(st.getItem())) continue;
                int lv = EquipmentEnhancer.getLevel(st);
                if (lv < best) { best = lv; target = s; }
            }
        }
        if (target == null) return; // 没有可强化的目标,材料原封不动

        // ② 深扫吞材料(含潜影盒)。m294 分账+防溢出;m302:高档强化石(> autoScrollMaxStoneTier)
        //    不自动吞——亿级石该由玩家亲手决定砸哪件;入池的石头也**成功后才扣**(碎裂不消耗)
        final int stoneTierLimit = YongyeConfig.get().autoScrollMaxStoneTier;
        final EquipmentEnhancer.MaterialSum total = new EquipmentEnhancer.MaterialSum();
        InventoryDeepScan.scan(p, s -> {
            if (s.isEmpty() || !EquipmentEnhancer.isMaterial(s.getItem())) return 0;
            if (s.getItem() instanceof com.yongye.item.EnhanceStoneItem stone) {
                if (stone.tier > stoneTierLimit) return 0; // 高档石留给玩家手动
                total.add(s);
                return 0; // 石头此遍只记账,成功后第二遍再扣
            }
            total.add(s);
            return s.getCount(); // 传统材料照旧当场扣(碎裂也不退,老规矩)
        });
        if (!total.any()) return;

        // ③ 正规管线强化(强化石必得直加;传统材料的碎裂/被动保护卷/成功率与手动完全一致)
        ItemStack eq = p.getEquippedStack(target);
        EquipmentEnhancer.EnhanceResult res = EquipmentEnhancer.enhanceWith(p, eq, total);
        if (!res.broke && total.direct > 0) {
            InventoryDeepScan.scan(p, s -> {
                if (!s.isEmpty() && s.getItem() instanceof com.yongye.item.EnhanceStoneItem stone
                        && stone.tier <= stoneTierLimit) return s.getCount();
                return 0;
            });
        }
        if (res.broke) {
            p.equipStack(target, ItemStack.EMPTY);
            p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                    SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 0.7f);
            p.sendMessage(Text.literal("自动强化:装备在 Lv." + res.startLevel + " 时碎裂了!")
                    .formatted(Formatting.DARK_RED), true);
            return;
        }
        p.equipStack(target, res.stack);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.45f, 1.4f);
        String prot = res.usedProtect ? " [保护卷抵挡]" : "";
        p.sendMessage(Text.literal("⚙ 自动强化:" + res.stack.getName().getString()
                        + " +" + res.succeeded + " 级 → Lv." + res.endLevel + prot)
                .formatted(Formatting.GOLD), true);
    }

    // ---------------- 自动吃书 ----------------

    private static void autoBook(ServerPlayerEntity p) {
        final String[] learned = { null };
        InventoryDeepScan.scan(p, new InventoryDeepScan.StackVisitor() {
            @Override public int visit(ItemStack s) {
                if (learned[0] != null || s.isEmpty()) return 0;
                if (s.getItem() instanceof SkillBookItem book) {
                    int lv = SkillBookItem.getLevel(s);
                    if (SkillEffectManager.learn(p, book.getType(), lv) == SkillEffectManager.LearnResult.OK) {
                        int total = SkillEffectManager.getLearnedLevel(p, book.getType());
                        learned[0] = s.getName().getString() + " → V" + total;
                        return 1;
                    }
                    return 0; // 满级跳过,继续找下一本
                }
                if (s.getItem() instanceof HealthSkillBookItem) {
                    int lv = HealthSkillBookItem.getLevel(s);
                    if (PlayerSkillManager.learnHealth(p, lv) == PlayerSkillManager.LearnResult.OK) {
                        learned[0] = "血量书 V" + lv + "(累计 V" + PlayerSkillManager.getLearnedHealth(p) + ")";
                        return 1;
                    }
                    return 0;
                }
                return 0;
            }
            @Override public boolean done() { return learned[0] != null; }
        });
        if (learned[0] == null) return;
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.4f, 1.7f);
        p.sendMessage(Text.literal("📖 自动研读:" + learned[0]).formatted(Formatting.AQUA), true);
    }
}
