package com.yongye;

import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModBlocks;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItemGroups;
import com.yongye.registry.ModItems;
import com.yongye.registry.ModSounds;
import com.yongye.registry.ModRecipes;
import com.yongye.system.ArmorHealthHandler;
import com.yongye.system.ArtifactManager;
import com.yongye.system.BonusXpHandler;
import com.yongye.system.BossHandler;
import com.yongye.system.BossAbilityHandler;
import com.yongye.system.CatastropheCoreManager;
import com.yongye.system.EliteHandler;
import com.yongye.system.HardcoreSurvivalHandler;
import com.yongye.system.HimJumpscareHandler;
import com.yongye.system.HighHpCounterHandler;
import com.yongye.system.LootHandler;
import com.yongye.system.MobBossHandler;
import com.yongye.system.MobEnhancementHandler;
import com.yongye.system.ModCommands;
import com.yongye.system.NightfallManager;
import com.yongye.system.PainBossHandler;
import com.yongye.system.PlayerSkillManager;
import com.yongye.system.PursuitHandler;
import com.yongye.system.QuestManager;
import com.yongye.system.SkillEffectManager;
import com.yongye.system.WeaponCombatHandler;
import com.yongye.system.WeaponSkillManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《永夜》主入口。
 * Phase 0: 工程骨架 + 配置 + 注册框架。
 * Phase 1: 怪物基础增强 / 套装血量 / 技能书(V1~V65535,含同级合成) / 随机掉落 / 超稀有材料。
 * Phase 2: 精英怪系统 / Boss 翻倍。
 * Phase 3: 永夜灾变 / 随机任务 / 追杀(锁定·挖墙·爬墙)。
 * Phase 4: 背包神器 / 高血量反制 / 指令。
 */
public class Yongye implements ModInitializer {
    public static final String MOD_ID = "yongye";
    public static final Logger LOGGER = LoggerFactory.getLogger("永夜");

    @Override
    public void onInitialize() {
        LOGGER.info("[永夜] 世界已经坏掉了，正在初始化……");

        // 配置
        YongyeConfig.load();

        // 解除原版属性 1024 硬上限(否则血量书/装备强化堆到一千多就失效)
        raiseAttributeCaps();

        // 注册层
        ModComponents.init();
        com.yongye.registry.ModScreens.init();
        ModAttachments.init();
        ModBlocks.init();
        ModItems.init();
        ModItemGroups.init();
        ModRecipes.init();

        // 玩法系统(Phase 1~2)
        // 注意 ENTITY_LOAD 监听器注册顺序:基础增强 → Boss 标记 → 精英化
        com.yongye.system.ProgressionManager.register();
        com.yongye.system.ClassManager.register();
        com.yongye.system.TalentManager.register();
        com.yongye.system.ClassSkillHandler.register();
        MobEnhancementHandler.register();
        BossHandler.register();
        BossAbilityHandler.register();
        MobBossHandler.register();
        BonusXpHandler.register();
        com.yongye.system.StartingKitHandler.register();
        com.yongye.system.NightfallHordeHandler.register();
        com.yongye.system.SurvivalRankHandler.register();
        com.yongye.system.NightfallWeatherHandler.register();
        EliteHandler.register();
        ArmorHealthHandler.register();
        LootHandler.register();

        // 玩法系统(Phase 3:永夜 / 追杀 / 任务)
        NightfallManager.register();
        PursuitHandler.register();
        QuestManager.register();
        CatastropheCoreManager.register();
        PainBossHandler.register();

        // 玩法系统(Phase 4:神器 / 高血量反制 / 指令)
        ArtifactManager.register();
        HighHpCounterHandler.register();
        SkillEffectManager.register();
        WeaponCombatHandler.register();
        WeaponSkillManager.init();
        HardcoreSurvivalHandler.register();
        ModSounds.init();
        HimJumpscareHandler.register();
        com.yongye.network.YongyeNet.register();
        ModCommands.register();

        // 玩家加入 / 重生时,根据持久化的累计等级重新应用血量强化
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerSkillManager.applyHealthModifier(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            PlayerSkillManager.applyHealthModifier(newPlayer);
            newPlayer.setHealth(newPlayer.getMaxHealth()); // 复活回满血(而非默认20)
        });

        LOGGER.info("[永夜] 初始化完成。活到天亮就是胜利。");
    }

    /** 把某个属性的硬上限抬到 max(原版默认夹在 1024,会让高血量/高强化失效)。 */
    private static void raiseCap(net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.attribute.EntityAttribute> entry, double max) {
        if (entry.value() instanceof net.minecraft.entity.attribute.ClampedEntityAttribute c) {
            ((com.yongye.mixin.ClampedEntityAttributeAccessor) (Object) c).yongye$setMaxValue(max);
        }
    }

    /** 解除核心属性的 1024 硬上限。攻速 1024 已够用,不动。 */
    private static void raiseAttributeCaps() {
        double cap = 1_000_000.0; // 上限抬到一百万,远超玩法需求
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH, cap);
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE, cap);
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR, cap);
        raiseCap(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ARMOR_TOUGHNESS, cap);
        LOGGER.info("[永夜] 已抬高属性上限(原版默认夹在 1024)");
    }
}
