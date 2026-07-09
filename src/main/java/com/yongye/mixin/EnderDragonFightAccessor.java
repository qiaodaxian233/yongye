package com.yongye.mixin;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.boss.dragon.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * m187:暴露 EnderDragonFight.bossBar,供 EndDragonHandler 嵌入血量数字(‖当前/最大)进血条名。
 * <p><b>待编译验证:</b> yarn 1.21.1 mapping 中 EnderDragonFight 的 bossBar 字段名。
 * 若编译报「No field bossBar」改查 mapping 表中的混淆名(通常为 bossBar / field_xxxx)。
 */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccessor {
    @Accessor("bossBar")
    ServerBossBar yongye$getBossBar();
}
