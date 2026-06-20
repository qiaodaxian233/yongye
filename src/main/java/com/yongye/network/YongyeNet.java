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
