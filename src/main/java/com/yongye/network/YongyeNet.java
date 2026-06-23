package com.yongye.network;

import com.yongye.item.SkillType;
import com.yongye.registry.ModAttachments;
import com.yongye.system.PlayerSkillManager;
import com.yongye.system.WeaponSkillManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;

/**
 * 成长数据网络层(通用入口注册;客户端入口只注册接收器)。
 */
public final class YongyeNet {
    private YongyeNet() {}

    public static void register() {
        PayloadTypeRegistry.playS2C().register(StatsPayload.ID, StatsPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SkillUsePayload.ID, SkillUsePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenAccessoryPayload.ID, OpenAccessoryPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SkillUsePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> WeaponSkillManager.use(p, payload.index()));
        });
        ServerPlayNetworking.registerGlobalReceiver(OpenAccessoryPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> p.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, pl) -> new com.yongye.screen.AccessoryScreenHandler(
                            syncId, playerInv, com.yongye.system.AccessoryStorage.load(pl)),
                    net.minecraft.text.Text.literal("饰品栏"))));
        });
        // 调试菜单:S2C 通知客户端开界面(命令 /yongye debug 触发)
        PayloadTypeRegistry.playS2C().register(com.yongye.network.OpenDebugPayload.ID, com.yongye.network.OpenDebugPayload.CODEC);
        // 开局选职
        PayloadTypeRegistry.playS2C().register(com.yongye.network.OpenClassSelectPayload.ID, com.yongye.network.OpenClassSelectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.yongye.network.ChooseClassPayload.ID, com.yongye.network.ChooseClassPayload.CODEC);
        // 职业大招
        PayloadTypeRegistry.playC2S().register(com.yongye.network.ClassUltimatePayload.ID, com.yongye.network.ClassUltimatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.ClassUltimatePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> com.yongye.system.ClassUltimateManager.use(p));
        });
        // 天赋界面:S2C 同步状态 + C2S 加点请求
        PayloadTypeRegistry.playS2C().register(com.yongye.network.TalentSyncPayload.ID, com.yongye.network.TalentSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.yongye.network.NightfallSyncPayload.ID, com.yongye.network.NightfallSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.yongye.network.CoreLocatorPayload.ID, com.yongye.network.CoreLocatorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.yongye.network.MpSyncPayload.ID, com.yongye.network.MpSyncPayload.CODEC);
        // 爆率编辑器:C2S 请求当前值 → S2C 回传(key=value 多行)
        PayloadTypeRegistry.playC2S().register(com.yongye.network.RequestConfigPayload.ID, com.yongye.network.RequestConfigPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(com.yongye.network.ConfigValuesPayload.ID, com.yongye.network.ConfigValuesPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.RequestConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> sendConfigValues(p));
        });
        PayloadTypeRegistry.playC2S().register(com.yongye.network.TalentLearnPayload.ID, com.yongye.network.TalentLearnPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.TalentLearnPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> {
                com.yongye.system.TalentManager.learn(p, payload.nodeId());
                sendTalents(p); // 加点后立即回传最新状态,界面即时刷新
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.ChooseClassPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> {
                com.yongye.item.PlayerClass c = com.yongye.item.PlayerClass.byId(payload.classId());
                if (c != null) com.yongye.system.ClassManager.chooseStartingClass(p, c);
            });
        });
        // 武器强化窗口:打开 + 应用升级
        PayloadTypeRegistry.playC2S().register(com.yongye.network.OpenEnhancePayload.ID, com.yongye.network.OpenEnhancePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.yongye.network.EnhanceApplyPayload.ID, com.yongye.network.EnhanceApplyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.OpenEnhancePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> p.openHandledScreen(new net.minecraft.screen.SimpleNamedScreenHandlerFactory(
                    (syncId, playerInv, pl) -> new com.yongye.screen.EnhanceScreenHandler(syncId, playerInv),
                    net.minecraft.text.Text.literal("武器强化"))));
        });
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.EnhanceApplyPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> {
                if (p.currentScreenHandler instanceof com.yongye.screen.EnhanceScreenHandler h) h.applyUpgrade(p);
            });
        });
        // 材料兑换(背包按钮:10 碎片→结晶→核心→血核,服务端扫背包扣料)
        PayloadTypeRegistry.playC2S().register(com.yongye.network.ExchangePayload.ID, com.yongye.network.ExchangePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.ExchangePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> com.yongye.system.MaterialExchange.exchange(p, payload.tier(), payload.all()));
        });
        // 守护界面:S2C 开界面(右键守护书触发) + C2S 对指定背包槽位施加守护(服务端校验+扣书)
        PayloadTypeRegistry.playS2C().register(com.yongye.network.OpenWardPayload.ID, com.yongye.network.OpenWardPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.yongye.network.WardApplyPayload.ID, com.yongye.network.WardApplyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.WardApplyPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> com.yongye.item.WardBookItem.applyWard(p, payload.slot()));
        });
        // 登录:未选过本命职业则弹出选职界面;老玩家(已有职业)只补标记
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity pl = handler.player;
            if (!com.yongye.YongyeConfig.get().enableStartingClassSelect) return;
            if (pl.getAttachedOrElse(ModAttachments.STARTING_CLASS_CHOSEN, false)) return;
            if (!com.yongye.system.ClassManager.learnedList(pl).isEmpty()) {
                pl.setAttached(ModAttachments.STARTING_CLASS_CHOSEN, true);
                return;
            }
            ServerPlayNetworking.send(pl, new com.yongye.network.OpenClassSelectPayload());
        });
        // 登录即推送一次,保证面板有数据
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendStats(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendTalents(handler.player));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendNightfall(handler.player));
    }

    /** 同步玩家天赋状态(点数 + 已习得职业 + 各节点等级)到客户端,供天赋界面渲染。 */
    public static void sendTalents(ServerPlayerEntity player) {
        int points = player.getAttachedOrElse(ModAttachments.TALENT_POINTS, 0);
        java.util.List<String> classes = com.yongye.system.ClassManager.learnedList(player);
        Map<String, Integer> learned = player.getAttachedOrElse(ModAttachments.TALENTS, Map.of());
        ServerPlayNetworking.send(player, new com.yongye.network.TalentSyncPayload(
                points, new java.util.ArrayList<>(classes), new java.util.HashMap<>(learned)));
    }

    public static void sendStats(ServerPlayerEntity player) {
        int health = PlayerSkillManager.getLearnedHealth(player);
        SkillType[] types = SkillType.values();
        int[] levels = new int[types.length];
        Map<String, Integer> learned = player.getAttachedOrElse(ModAttachments.LEARNED_SKILLS, Map.of());
        for (int i = 0; i < types.length; i++) {
            levels[i] = learned.getOrDefault(types[i].id, 0);
        }
        java.util.List<String> cls = com.yongye.system.ClassManager.learnedList(player);
        String className = cls.isEmpty() ? "" : cls.get(0);
        ServerPlayNetworking.send(player, new StatsPayload(health, levels, className));
    }

    /** 下发当前永夜等级 + 阶段名 + 视野压缩强度给指定玩家(HUD / 暗角显示用)。 */
    public static void sendNightfall(ServerPlayerEntity player) {
        com.yongye.YongyeConfig cfg = com.yongye.YongyeConfig.get();
        int level = com.yongye.system.NightfallManager.getLevel();
        // 视野压缩强度:开启且达到最低层才 >0;随等级递增并封顶,客户端据此画恒定暗角(不闪)
        int vision = (cfg.enableNightfallDarkness && level >= cfg.nightfallDarknessMinLevel)
                ? Math.min(level, 6) : 0;
        ServerPlayNetworking.send(player, new com.yongye.network.NightfallSyncPayload(
                level, com.yongye.system.NightfallManager.getLevelName(), vision));
    }

    /** 下发"最近灾厄核心位置"给玩家(HUD 方向箭头用);has=false 表示范围内无核心。 */
    public static void sendCoreLocator(ServerPlayerEntity player, boolean has, double x, double y, double z) {
        ServerPlayNetworking.send(player, new com.yongye.network.CoreLocatorPayload(has, x, y, z));
    }

    /** 把当前可编辑配置(爆率/经验)的值回传客户端,供爆率编辑器预填(data='key=value\n...')。 */
    public static void sendConfigValues(ServerPlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        for (String key : com.yongye.network.ConfigValuesPayload.EDITABLE_KEYS) {
            sb.append(key).append('=').append(com.yongye.YongyeConfig.getFieldString(key)).append('\n');
        }
        ServerPlayNetworking.send(player, new com.yongye.network.ConfigValuesPayload(sb.toString()));
    }
}
