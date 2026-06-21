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
        // 开局选职
        PayloadTypeRegistry.playS2C().register(com.yongye.network.OpenClassSelectPayload.ID, com.yongye.network.OpenClassSelectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(com.yongye.network.ChooseClassPayload.ID, com.yongye.network.ChooseClassPayload.CODEC);
        // 职业大招
        PayloadTypeRegistry.playC2S().register(com.yongye.network.ClassUltimatePayload.ID, com.yongye.network.ClassUltimatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.ClassUltimatePayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> com.yongye.system.ClassUltimateManager.use(p));
        });
        ServerPlayNetworking.registerGlobalReceiver(com.yongye.network.ChooseClassPayload.ID, (payload, context) -> {
            ServerPlayerEntity p = context.player();
            p.server.execute(() -> {
                com.yongye.item.PlayerClass c = com.yongye.item.PlayerClass.byId(payload.classId());
                if (c != null) com.yongye.system.ClassManager.chooseStartingClass(p, c);
            });
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
    }

    public static void sendStats(ServerPlayerEntity player) {
        int health = PlayerSkillManager.getLearnedHealth(player);
        SkillType[] types = SkillType.values();
        int[] levels = new int[types.length];
        Map<String, Integer> learned = player.getAttachedOrElse(ModAttachments.LEARNED_SKILLS, Map.of());
        for (int i = 0; i < types.length; i++) {
            levels[i] = learned.getOrDefault(types[i].id, 0);
        }
        ServerPlayNetworking.send(player, new StatsPayload(health, levels));
    }
}
