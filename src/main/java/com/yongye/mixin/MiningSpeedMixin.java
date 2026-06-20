package com.yongye.mixin;

import com.yongye.YongyeConfig;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 极难生存核心:直接拖慢玩家挖掘速度(只影响挖方块,不影响攻击速度)。
 * 注入 PlayerEntity#getBlockBreakingSpeed 的返回值,对目标方块乘以系数。
 */
@Mixin(PlayerEntity.class)
public abstract class MiningSpeedMixin {

    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void yongye$slowMining(BlockState block, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.isCreative() || self.isSpectator()) return;

        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableHardcoreSurvival || !cfg.hcResourceHarder || !cfg.hcMiningSlowdown) return;

        boolean target = cfg.hcMiningSlowAll
                || block.isIn(BlockTags.LOGS)
                || block.isIn(BlockTags.BASE_STONE_OVERWORLD)
                || block.isIn(BlockTags.COAL_ORES)
                || block.isIn(BlockTags.IRON_ORES);
        if (!target) return;

        float mult = (float) Math.max(0.05, cfg.hcMiningSpeedMultiplier);
        cir.setReturnValue(cir.getReturnValue() * mult);
    }
}
