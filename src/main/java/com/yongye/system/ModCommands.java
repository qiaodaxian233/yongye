package com.yongye.system;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yongye.Yongye;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ArtifactType;
import com.yongye.item.HealthSkillBookItem;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /yongye 指令树(需 OP / 权限等级 2)。用于驱动永夜、派发任务、发放道具,方便测试与运营。
 */
public final class ModCommands {
    private ModCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
                dispatcher.register(CommandManager.literal("yongye")
                        .requires(s -> s.hasPermissionLevel(2))

                        .then(CommandManager.literal("nightfall")
                                .then(CommandManager.literal("status").executes(ctx -> {
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("当前:" + NightfallManager.getLevelName()
                                                    + "(等级 " + NightfallManager.getLevel() + ")").formatted(Formatting.DARK_PURPLE), false);
                                    return 1;
                                }))
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(0, 5)).executes(ctx -> {
                                    NightfallManager.setLevel(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "level"));
                                    return 1;
                                })))

                        .then(CommandManager.literal("redeem").executes(ctx -> {
                            NightfallManager.redeem(ctx.getSource().getServer());
                            return 1;
                        }))

                        .then(CommandManager.literal("quest")
                                .then(CommandManager.literal("hunt").executes(ctx -> {
                                    QuestManager.assign(ctx.getSource().getPlayerOrThrow(), QuestManager.Type.HUNT_ELITE);
                                    return 1;
                                }))
                                .then(CommandManager.literal("survive").executes(ctx -> {
                                    QuestManager.assign(ctx.getSource().getPlayerOrThrow(), QuestManager.Type.SURVIVE);
                                    return 1;
                                }))
                                .then(CommandManager.literal("flee").executes(ctx -> {
                                    QuestManager.assign(ctx.getSource().getPlayerOrThrow(), QuestManager.Type.FLEE);
                                    return 1;
                                })))

                        .then(CommandManager.literal("book")
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 65535)).executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    p.giveItemStack(HealthSkillBookItem.create(IntegerArgumentType.getInteger(ctx, "level")));
                                    return 1;
                                })))

                        .then(CommandManager.literal("artifact")
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                        .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 6)).executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                            String tid = StringArgumentType.getString(ctx, "type");
                                            ArtifactType type = null;
                                            for (ArtifactType t : ArtifactType.values()) {
                                                if (t.id.equals(tid)) { type = t; break; }
                                            }
                                            if (type == null) {
                                                ctx.getSource().sendError(Text.literal("未知神器: " + tid
                                                        + "(可用: life_idol/iron_core/bone_arrow_charm/voodoo_bottle/escapist_boots/gravedigger_compass/undying_ember/nightfall_eye/glutton_heart/world_anchor)"));
                                                return 0;
                                            }
                                            p.giveItemStack(ArtifactItem.create(type, IntegerArgumentType.getInteger(ctx, "level")));
                                            return 1;
                                        }))))
                ));

        Yongye.LOGGER.info("[亡途荒夜] 指令已注册");
    }
}
