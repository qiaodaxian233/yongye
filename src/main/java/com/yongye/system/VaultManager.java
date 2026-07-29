package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 材料仓库(m356,作者:「任务种类太多想存东西,选择存或不存、可以取出来」)。
 *  - 无限计数虚拟仓库:数据存玩家附件 VAULT_ITEMS(Map<键,数量>,persistent+copyOnDeath,死亡不丢);
 *  - 键=物品 id;技能书(带 SKILL_LEVEL 组件)追加 "#等级"——同书同级合并成一行计数,
 *    这正是作者要的「可以检查强化石和技能书」的聚合视图;
 *  - 可入库白名单:传统强化材料+全部强化石(EquipmentEnhancer.isMaterial)、两类技能书、
 *    终焉精华、强化保护卷——只收本模组成长物资,不做万物箱;
 *  - 存=「存入全部材料」一键扫主背包 36 格(不动盔甲/副手/主手拿着的照收——材料拿手里也是材料);
 *    取=按行取一叠(钳 maxCount),offerOrDrop 背包满掉脚下;
 *  - 同步走 "键=数量\n" 多行字符串(照 ConfigValuesPayload m??? 在树先例,零新 codec 面);
 *  - 未知键(卸模组/改档)取出时自动清理自愈;enableVault 总开关,关=服务端全拒。
 *  - m357 预留:自动存(捡到即入库)/自动用(强化/学书/任务从仓库扣)下一笔接线。
 */
public final class VaultManager {
    private VaultManager() {}

    /** 是否可入库:传统材料/强化石、技能书(带 SKILL_LEVEL 组件的两类书)、终焉精华、强化保护卷。 */
    public static boolean vaultable(ItemStack s) {
        if (s.isEmpty()) return false;
        if (EquipmentEnhancer.isMaterial(s.getItem())) return true;
        if (s.get(ModComponents.SKILL_LEVEL) != null) return true;
        return s.getItem() == ModItems.ENDING_ESSENCE || s.getItem() == ModItems.ENHANCE_PROTECT_SCROLL;
    }

    /** 仓库键:物品 id;技能书追加 "#等级"(同书同级合并计数)。 */
    public static String keyOf(ItemStack s) {
        String id = Registries.ITEM.getId(s.getItem()).toString();
        Integer lv = s.get(ModComponents.SKILL_LEVEL);
        return lv != null ? id + "#" + lv : id;
    }

    /** 由键重建一叠(数量由调用方钳好);解析失败/未知物品返回 EMPTY(卸模组/改档自愈)。 */
    public static ItemStack stackFor(String key, int count) {
        String id = key;
        int lv = 0;
        int h = key.indexOf('#');
        if (h > 0) {
            id = key.substring(0, h);
            try { lv = Integer.parseInt(key.substring(h + 1)); } catch (NumberFormatException e) { return ItemStack.EMPTY; }
        }
        Identifier ident = Identifier.tryParse(id);
        if (ident == null) return ItemStack.EMPTY;
        Item it = Registries.ITEM.get(ident);
        if (it == Items.AIR) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(it, Math.max(1, count));
        if (lv > 0) s.set(ModComponents.SKILL_LEVEL, lv);
        return s;
    }

    /** 「存入全部材料」:扫主背包 36 格,可入库的整叠搬进仓库。 */
    public static void depositAll(ServerPlayerEntity p) {
        if (!YongyeConfig.get().enableVault) { msg(p, "材料仓库未启用", Formatting.RED); return; }
        Map<String, Long> vault = new HashMap<>(p.getAttachedOrElse(ModAttachments.VAULT_ITEMS, Map.of()));
        PlayerInventory inv = p.getInventory();
        long moved = 0;
        for (int i = 0; i < 36; i++) {   // 主背包区(热栏 0~8 + 背包 9~35),盔甲/副手不动
            ItemStack s = inv.getStack(i);
            if (!vaultable(s)) continue;
            vault.merge(keyOf(s), (long) s.getCount(), Long::sum);
            moved += s.getCount();
            inv.setStack(i, ItemStack.EMPTY);
        }
        if (moved > 0) {
            p.setAttached(ModAttachments.VAULT_ITEMS, vault);
            msg(p, "已入库 " + moved + " 件材料", Formatting.LIGHT_PURPLE);
        } else {
            msg(p, "背包里没有可入库的材料", Formatting.GRAY);
        }
        sync(p);
    }

    /** 按键取出一叠(钳该物品 maxCount);未知键自动清理。 */
    public static void withdraw(ServerPlayerEntity p, String key) {
        if (!YongyeConfig.get().enableVault) { msg(p, "材料仓库未启用", Formatting.RED); return; }
        if (key == null || key.isEmpty()) return;
        Map<String, Long> vault = new HashMap<>(p.getAttachedOrElse(ModAttachments.VAULT_ITEMS, Map.of()));
        long have = vault.getOrDefault(key, 0L);
        if (have <= 0) { msg(p, "仓库里没有这项了", Formatting.GRAY); sync(p); return; }
        ItemStack probe = stackFor(key, 1);
        if (probe.isEmpty()) {   // 卸模组/坏键:清掉自愈
            vault.remove(key);
            p.setAttached(ModAttachments.VAULT_ITEMS, vault);
            msg(p, "该条目已失效,已从仓库清理", Formatting.GRAY);
            sync(p);
            return;
        }
        int take = (int) Math.min(have, probe.getMaxCount());
        p.getInventory().offerOrDrop(stackFor(key, take));
        if (have - take <= 0) vault.remove(key); else vault.put(key, have - take);
        p.setAttached(ModAttachments.VAULT_ITEMS, vault);
        msg(p, "取出 " + probe.getName().getString() + " ×" + take, Formatting.LIGHT_PURPLE);
        sync(p);
    }

    /** 下发仓库快照:"键=数量\n" 多行(TreeMap 排序=同族物品天然相邻,客户端按行解析)。 */
    public static void sync(ServerPlayerEntity p) {
        Map<String, Long> vault = new TreeMap<>(p.getAttachedOrElse(ModAttachments.VAULT_ITEMS, Map.of()));
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> e : vault.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        }
        ServerPlayNetworking.send(p, new com.yongye.network.VaultSyncPayload(sb.toString()));
    }

    /** m357 自动存:每 100t(5s)扫**背包区 9~35**(热栏 0~8 刻意不动——手上/热栏留的书石头
     *  代表玩家想手动用,自动收走会打断操作),可入库的整叠静默搬进仓库,有搬动才 actionbar 提示。 */
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 100 != 0) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableVault || !cfg.vaultAutoDeposit) return;
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                Map<String, Long> vault = null;
                PlayerInventory inv = p.getInventory();
                long moved = 0;
                for (int i = 9; i < 36; i++) {   // 背包区;热栏不动
                    ItemStack s = inv.getStack(i);
                    if (!vaultable(s)) continue;
                    if (vault == null) vault = new HashMap<>(p.getAttachedOrElse(ModAttachments.VAULT_ITEMS, Map.of()));
                    vault.merge(keyOf(s), (long) s.getCount(), Long::sum);
                    moved += s.getCount();
                    inv.setStack(i, ItemStack.EMPTY);
                }
                if (moved > 0) {
                    p.setAttached(ModAttachments.VAULT_ITEMS, vault);
                    msg(p, "已自动入库 " + moved + " 件材料(背包「仓库」查看)", Formatting.DARK_PURPLE);
                    sync(p);
                }
            }
        });
        Yongye.LOGGER.info("[夜蚀] 材料仓库已挂载(自动入库每5s,热栏豁免)");
    }

    private static void msg(ServerPlayerEntity p, String s, Formatting c) {
        p.sendMessage(Text.literal(s).formatted(c), true);
    }
}
