package com.yongye.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.UUID;

/**
 * BossBarHud 私有成员访问器(m179,供 BossBarStyleMixin 接管血条布局用)。
 * 成员名 bossBars / renderBossBar(DrawContext,int,int,BossBar) 已用 FabricMC 官方
 * yarn 1.21 javadoc 逐字核对;若映射不符,mixin 注解处理器会在本地 build 期直接报错
 * (不会带病进游戏),把报错贴来即改名。照 ClampedEntityAttributeAccessor /
 * EntityFlagInvoker 的仓库既有 @Accessor/@Invoker 范式。
 */
@Mixin(BossBarHud.class)
public interface BossBarHudAccess {

    /** 当前所有血条(LinkedHashMap,保持服务端下发顺序)。 */
    @Accessor("bossBars")
    Map<UUID, ClientBossBar> yongye$getBossBars();

    /** 原版单条血条绘制(底槽+填充,私有 4 参重载;名字文本不在其中)。 */
    @Invoker("renderBossBar")
    void yongye$renderVanillaBar(DrawContext context, int x, int y, BossBar bar);
}
