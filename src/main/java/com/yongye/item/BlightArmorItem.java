package com.yongye.item;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModComponents;
import net.minecraft.entity.Entity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * 夜蚀盔甲(m265):蚀锭打造的灵魂绑定套装。
 * 「别人抢不走」三件套:
 *   ① 认主——第一个把它拿进背包的玩家自动成为主人(BLIGHT_OWNER 组件存 "uuid|名字");
 *   ② 只有主人捡得起——掉在地上时非主人碰撞直接取消拾取(SoulboundPickupMixin);
 *   ③ 死亡不掉落——死亡瞬间从掉落流程里截走,重生原样归还(SoulboundDropMixin + 附件)。
 * 强化系统自动识别为盔甲(EquipmentEnhancer instanceof ArmorItem 兜底),可无限强化。
 */
public class BlightArmorItem extends ArmorItem {

    public BlightArmorItem(RegistryEntry<ArmorMaterial> material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClient) return;
        // 认主:未绑定 + 在玩家背包里 → 绑给这名玩家(受 blightArmorSoulbound 门控)
        if (YongyeConfig.get().blightArmorSoulbound
                && stack.get(ModComponents.BLIGHT_OWNER) == null && entity instanceof ServerPlayerEntity p) {
            stack.set(ModComponents.BLIGHT_OWNER, p.getUuid().toString() + "|" + p.getGameProfile().getName());
            p.sendMessage(Text.literal("【夜蚀】" + stack.getName().getString() + " 与你的灵魂缔结了契约")
                    .formatted(Formatting.LIGHT_PURPLE), true);
        }
        // m281 装备无法被破坏·耐久面:补上原版 UNBREAKABLE 组件,耐久永不下降永不损毁
        // (tooltip 自带一行"无法破坏";老存档里已造好的装备走这里自动补齐)
        // 【待编译验证】DataComponentTypes.UNBREAKABLE / UnbreakableComponent(boolean)——类路径已核 yarn 1.21.1
        //  (net.minecraft.component.type.UnbreakableComponent=class_9300);报错时把这两行注释掉即可,掉落物保护不受影响。
        if (YongyeConfig.get().blightArmorIndestructible
                && stack.get(net.minecraft.component.DataComponentTypes.UNBREAKABLE) == null) {
            stack.set(net.minecraft.component.DataComponentTypes.UNBREAKABLE,
                    new net.minecraft.component.type.UnbreakableComponent(true));
        }
    }

    /**
     * m281:这件物品是否受灵魂绑定保护——夜蚀盔甲本体,或任何已带认主组件的物品。
     * 掉落物免伤/永不消失/虚空归还/怪物捡不走/精英缴械豁免/定时清理豁免 全部走这一个口径。
     */
    public static boolean isSoulbound(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof BlightArmorItem
                    || stack.get(ModComponents.BLIGHT_OWNER) != null);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        String tag = stack.get(ModComponents.BLIGHT_OWNER);
        if (tag != null) {
            int cut = tag.indexOf('|');
            String owner = cut >= 0 ? tag.substring(cut + 1) : tag;
            tooltip.add(Text.literal("已认主:" + owner).formatted(Formatting.LIGHT_PURPLE));
        }
        tooltip.add(Text.literal("灵魂绑定 · 死亡不掉落 · 他人无法拾取").formatted(Formatting.DARK_PURPLE));
        if (YongyeConfig.get().blightArmorIndestructible) {
            tooltip.add(Text.literal("不可摧毁 · 火/爆炸/虚空不灭 · 怪物与精英抢不走").formatted(Formatting.DARK_PURPLE));
        }
        var c = YongyeConfig.get();
        tooltip.add(Text.literal(String.format("夜蚀共鸣:每件 生命+%d%% 攻击+%d%%,集齐 4 件 移速+%d%%",
                Math.round(c.blightSetHpPct * 100), Math.round(c.blightSetAtkPct * 100),
                Math.round(c.blightSetSpeedPct * 100))).formatted(Formatting.LIGHT_PURPLE));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
