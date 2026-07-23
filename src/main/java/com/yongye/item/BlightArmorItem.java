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
        if (!YongyeConfig.get().blightArmorSoulbound) return;
        // 认主:未绑定 + 在玩家背包里 → 绑给这名玩家
        if (stack.get(ModComponents.BLIGHT_OWNER) == null && entity instanceof ServerPlayerEntity p) {
            stack.set(ModComponents.BLIGHT_OWNER, p.getUuid().toString() + "|" + p.getGameProfile().getName());
            p.sendMessage(Text.literal("【夜蚀】" + stack.getName().getString() + " 与你的灵魂缔结了契约")
                    .formatted(Formatting.LIGHT_PURPLE), true);
        }
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
        super.appendTooltip(stack, context, tooltip, type);
    }
}
