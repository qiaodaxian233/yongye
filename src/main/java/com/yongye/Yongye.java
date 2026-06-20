package com.yongye;

import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModComponents;
import com.yongye.registry.ModItemGroups;
import com.yongye.registry.ModItems;
import com.yongye.registry.ModRecipes;
import com.yongye.system.ArmorHealthHandler;
import com.yongye.system.LootHandler;
import com.yongye.system.MobEnhancementHandler;
import com.yongye.system.PlayerSkillManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《亡途荒夜》主入口。
 * Phase 0: 工程骨架 + 配置 + 注册框架。
 * Phase 1: 怪物基础增强 / 套装血量 / 技能书(V1~V65535,含同级合成) / 随机掉落 / 超稀有材料。
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

        // 玩法系统
        MobEnhancementHandler.register();
        ArmorHealthHandler.register();
        LootHandler.register();

        // 玩家加入 / 重生时,根据持久化的累计等级重新应用血量强化
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                PlayerSkillManager.applyHealthModifier(handler.getPlayer()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                PlayerSkillManager.applyHealthModifier(newPlayer));

        LOGGER.info("[亡途荒夜] 初始化完成。活到天亮就是胜利。");
    }
}
