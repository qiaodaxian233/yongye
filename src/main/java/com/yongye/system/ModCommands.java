package com.yongye.system;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.yongye.Yongye;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ArtifactType;
import com.yongye.item.HealthSkillBookItem;
import com.yongye.item.SkillBookItem;
import com.yongye.item.SkillType;
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
                                }))
                                .then(CommandManager.literal("core").executes(ctx -> {
                                    QuestManager.assign(ctx.getSource().getPlayerOrThrow(), QuestManager.Type.CLEAR_CORE);
                                    return 1;
                                }))
                                .then(CommandManager.literal("gather").executes(ctx -> {
                                    QuestManager.assign(ctx.getSource().getPlayerOrThrow(), QuestManager.Type.GATHER);
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

                        .then(CommandManager.literal("skillbook")
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                        .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 65535)).executes(ctx -> {
                                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                            String tid = StringArgumentType.getString(ctx, "type");
                                            SkillType type = null;
                                            for (SkillType t : SkillType.values()) {
                                                if (t.id.equals(tid)) { type = t; break; }
                                            }
                                            if (type == null) {
                                                ctx.getSource().sendError(Text.literal("未知技能书: " + tid
                                                        + "(可用: armor/regen/evasion/thorns/resistance)"));
                                                return 0;
                                            }
                                            p.giveItemStack(SkillBookItem.create(type, IntegerArgumentType.getInteger(ctx, "level")));
                                            return 1;
                                        }))))

                        .then(CommandManager.literal("core").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            net.minecraft.util.math.BlockPos pos = CatastropheCoreManager.spawnCoreNear(p);
                            if (pos != null) {
                                ctx.getSource().sendFeedback(() -> Text.literal("已在 " + pos.getX() + ", " + pos.getY()
                                        + ", " + pos.getZ() + " 生成灾厄核心").formatted(Formatting.DARK_RED), false);
                            }
                            return 1;
                        }))

                        .then(CommandManager.literal("painboss").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            PainBossHandler.spawnPainBossNear(p);
                            return 1;
                        }))

                        .then(CommandManager.literal("wardbook").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.WARD_BOOK));
                            ctx.getSource().sendFeedback(() -> Text.literal("已获得【守护附魔书】").formatted(Formatting.LIGHT_PURPLE), false);
                            return 1;
                        }))

                        .then(CommandManager.literal("chaosblade").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.CHAOS_BLADE));
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已获得【混沌之刃】").formatted(Formatting.DARK_PURPLE), false);
                            return 1;
                        }))

                        .then(CommandManager.literal("enhance")
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(0)).executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    int lvl = IntegerArgumentType.getInteger(ctx, "level");
                                    net.minecraft.item.ItemStack held = p.getMainHandStack();
                                    if (held.isEmpty() || !EquipmentEnhancer.isEnhanceable(held.getItem())) {
                                        ctx.getSource().sendError(Text.literal("手持物品不是可强化的武器/盔甲"));
                                        return 0;
                                    }
                                    p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,
                                            EquipmentEnhancer.withLevel(held, lvl));
                                    com.yongye.item.WeaponQuality q = com.yongye.item.WeaponQuality.forLevel(lvl);
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("已强化至 +" + lvl + " 【" + q.cn + "】").formatted(q.color), false);
                                    return 1;
                                })))
                ));

        Yongye.LOGGER.info("[亡途荒夜] 指令已注册");
    }
}
