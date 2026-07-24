package com.yongye.mixin;

import com.yongye.YongyeConfig;
import com.yongye.item.BlightArmorItem;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 夜蚀装备·怪物捡不走(m281,作者:「无法被精英怪抢走」的地面拾取面):
 * 原版会捡地面物品的怪(僵尸/尸壳/溺尸等,精英怪也是这些怪加词缀)统一走 MobEntity.loot(ItemEntity)
 * 把物品穿上/拿起——对灵魂绑定物在入口直接取消,装备原地不动只等主人。
 * 【待编译验证】loot 方法名(yarn 1.21.1 官方 mapping method_5949=loot 已核,仓库首次注入此点);
 * 若 build 报找不到方法,把本 mixin 从 yongye.mixins.json 移除即可,其余保护不受影响。
 * 注:精英「缴械」抢的是你身上穿的(不走这条),豁免在 EliteHandler;这里管的是掉在地上被怪顺走。
 */
@Mixin(MobEntity.class)
public abstract class SoulboundMobLootMixin {

    @Inject(method = "loot", at = @At("HEAD"), cancellable = true)
    private void yongye$noMobPickup(ItemEntity item, CallbackInfo ci) {
        if (!YongyeConfig.get().blightArmorIndestructible) return;
        if (BlightArmorItem.isSoulbound(item.getStack())) ci.cancel();
    }
}
