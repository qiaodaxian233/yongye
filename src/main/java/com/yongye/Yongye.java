package com.yongye;

import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItemGroups;
import com.yongye.registry.ModItems;
import com.yongye.registry.ModRecipes;
import com.yongye.system.ArmorHealthHandler;
import com.yongye.system.ArtifactManager;
import com.yongye.system.BossHandler;
import com.yongye.system.EliteHandler;
import com.yongye.system.HighHpCounterHandler;
import com.yongye.system.LootHandler;
import com.yongye.system.MobEnhancementHandler;
import com.yongye.system.ModCommands;
import com.yongye.system.NightfallManager;
import com.yongye.system.PlayerSkillManager;
import com.yongye.system.PursuitHandler;
import com.yongye.system.QuestManager;
import com.yongye.system.SkillEffectManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《亡途荒夜》主入口。
 * Phase 0: 工程骨架 + 配置 + 注册框架。
 * Phase 1: 怪物基础增强 / 套装血量 / 技能书(V1~V65535,含同级合成) / 随机掉落 / 超稀有材料。
 * Phase 2: 精英怪系统 / Boss 翻倍。
 * Phase 3: 永夜灾变 / 随机任务 / 追杀(锁定·挖墙·爬墙)。
 * Phase 4: 背包神器 / 高血量反制 / 指令。
 */
public class Yongye implements ModInitializer {
    public static final String MOD_ID = "yongye";
    public static final Logger LOGGER = LoggerFactory.getLogger("亡途荒夜");

    @Override
    public void onInitialize() {
        LOGGER.info("[亡途荒夜] 世界已经坏掉了，正在初始化……");

        // 配置
        YongyeConfig.load();

        // 注册层
        ModComponents.init();
        ModAttachments.init();
        ModItems.init();
        ModItemGroups.init();
        ModRecipes.init();

        // 玩法系统(Phase 1~2)
        // 注意 ENTITY_LOAD 监听器注册顺序:基础增强 → Boss 标记 → 精英化
        MobEnhancementHandler.register();
        BossHandler.register();
        EliteHandler.register();
        ArmorHealthHandler.register();
        LootHandler.register();

        // 玩法系统(Phase 3:永夜 / 追杀 / 任务)
        NightfallManager.register();
        PursuitHandler.register();
        QuestManager.register();

        // 玩法系统(Phase 4:神器 / 高血量反制 / 指令)
        ArtifactManager.register();
        HighHpCounterHandler.register();
        SkillEffectManager.register();
        com.yongye.network.YongyeNet.register();
        ModCommands.register();

        // 玩家加入 / 重生时,根据持久化的累计等级重新应用血量强化
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerSkillManager.applyHealthModifier(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                PlayerSkillManager.applyHealthModifier(newPlayer));

        LOGGER.info("[亡途荒夜] 初始化完成。活到天亮就是胜利。");
    }
}
