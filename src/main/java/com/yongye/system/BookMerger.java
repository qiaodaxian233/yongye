package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
import com.yongye.registry.ModItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * m323:一键合书(作者:「升级等级应该是升级机制,不应该每个等级都需要重新合成」)。
 * 背包「合书」按钮一次到位:把背包里**所有**技能书/血量书按类型各合成一本(等级相加,
 * skillBookMaxLevel 封顶),自动从背包扣**结果等级档位**的阶段材料(与工作台合成 m319 同一套阈值:
 * 结晶/核心/血核,各 1 个);缺材料的类型跳过并提示,不动那些书。
 * 口径:等级累加 long 防溢出;某类型全部书里最高一本已≥合并结果(封顶)则跳过防误亏;
 * 工作台 2 本合成(m319)保留不动,合书是它的批量升级机制版。
 */
public final class BookMerger {
    private BookMerger() {}

    public static void mergeAll(ServerPlayerEntity p) {
        YongyeConfig c = YongyeConfig.get();
        if (!c.enableBookMerge) { msg(p, "合书功能未启用", Formatting.RED); return; }
        PlayerInventory inv = p.getInventory();
        int cap = c.skillBookMaxLevel;
        int done = 0;
        StringBuilder out = new StringBuilder();

        for (SkillType t : SkillType.values()) {
            done += mergeType(p, inv, t, cap, out) ? 1 : 0;
        }
        done += mergeHealth(p, inv, cap, out) ? 1 : 0;

        if (done == 0 && out.isEmpty()) msg(p, "背包里没有可合并的书(同类型需≥2本)", Formatting.GRAY);
        else if (out.length() > 0) msg(p, "合书完成 " + done + " 类;" + out, Formatting.GOLD);
        else msg(p, "合书完成:" + done + " 类书已各合为一本", Formatting.GOLD);
    }

    /** 合并一种属性技能书;返回是否合成了。跳过原因写入 out。 */
    private static boolean mergeType(ServerPlayerEntity p, PlayerInventory inv, SkillType t, int cap, StringBuilder out) {
        long sum = 0; int books = 0; int highest = 0; String name = null;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof SkillBookItem sb && sb.getType() == t) {
                int lv = Math.max(1, SkillBookItem.getLevel(s));
                sum += (long) lv * s.getCount();
                books += s.getCount();
                highest = Math.max(highest, lv);
                if (name == null) name = s.getName().getString();
            }
        }
        if (books < 2) return false;
        int result = (int) Math.min(cap, sum);
        if (result <= highest) { out.append("【").append(name).append("】已封顶跳过;"); return false; }
        if (!takeMaterial(inv, result, name, out)) return false;
        removeBooks(inv, s -> s.getItem() instanceof SkillBookItem sb && sb.getType() == t);
        inv.offerOrDrop(SkillBookItem.create(t, result));
        return true;
    }

    /** 合并血量技能书;口径同上。 */
    private static boolean mergeHealth(ServerPlayerEntity p, PlayerInventory inv, int cap, StringBuilder out) {
        long sum = 0; int books = 0; int highest = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() instanceof HealthSkillBookItem) {
                int lv = Math.max(1, HealthSkillBookItem.getLevel(s));
                sum += (long) lv * s.getCount();
                books += s.getCount();
                highest = Math.max(highest, lv);
            }
        }
        if (books < 2) return false;
        int result = (int) Math.min(cap, sum);
        if (result <= highest) { out.append("【血量技能书】已封顶跳过;"); return false; }
        if (!takeMaterial(inv, result, "血量技能书", out)) return false;
        removeBooks(inv, s -> s.getItem() instanceof HealthSkillBookItem);
        inv.offerOrDrop(HealthSkillBookItem.create(result));
        return true;
    }

    /** 按结果等级取档扣 1 个阶段材料(与 m319 工作台合成同阈值);无需材料返回 true。 */
    private static boolean takeMaterial(PlayerInventory inv, int resultLevel, String name, StringBuilder out) {
        YongyeConfig c = YongyeConfig.get();
        Item need = null;
        if (resultLevel >= c.catastropheBloodCoreThreshold) need = ModItems.CATASTROPHE_BLOOD_CORE;
        else if (resultLevel >= c.lifeCoreThreshold) need = ModItems.LIFE_CORE;
        else if (resultLevel >= c.lifeCrystalThreshold) need = ModItems.LIFE_CRYSTAL;
        if (need == null) return true;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getItem() == need) { s.decrement(1); return true; }
        }
        out.append("【").append(name).append("】缺 ").append(new ItemStack(need).getName().getString()).append(" 跳过;");
        return false;
    }

    private static void removeBooks(PlayerInventory inv, java.util.function.Predicate<ItemStack> match) {
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && match.test(s)) inv.setStack(i, ItemStack.EMPTY);
        }
    }

    private static void msg(ServerPlayerEntity p, String text, Formatting f) {
        p.sendMessage(Text.literal(text).formatted(f), false);
    }
}
