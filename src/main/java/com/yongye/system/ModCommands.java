package com.yongye.system;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
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

    /** 仅此玩家(按游戏内 ID/用户名,大小写不敏感)可打开 debug 运营菜单;改这里即可换人。 */
    private static final java.util.List<String> DEBUG_OWNERS = java.util.List.of("qiaodaxian", "jiemoli");

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> {
                dispatcher.register(CommandManager.literal("yongye")
                        .requires(s -> s.hasPermissionLevel(2))

                        // 召唤自定义末影龙 BOSS(测试用):/yongye dragon
                        .then(CommandManager.literal("dragon").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) {
                                ctx.getSource().sendError(Text.literal("只能由玩家执行"));
                                return 0;
                            }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.ToroEnderDragonEntity dragon =
                                    new com.yongye.entity.ToroEnderDragonEntity(com.yongye.registry.ModEntities.TORO_ENDER_DRAGON, w);
                            dragon.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(dragon);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤末影龙 BOSS").formatted(Formatting.DARK_PURPLE), false);
                            return 1;
                        }))

                        // 召唤精英·毒液蜘蛛(测试用):/yongye venomspider
                        .then(CommandManager.literal("venomspider").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendError(Text.literal("只能由玩家执行")); return 0; }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.VenomSpiderEntity e =
                                    new com.yongye.entity.VenomSpiderEntity(com.yongye.registry.ModEntities.VENOM_SPIDER, w);
                            e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(e);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤精英·毒液蜘蛛").formatted(Formatting.GREEN), false);
                            return 1;
                        }))

                        // 召唤 BOSS·红蜘蛛(测试用):/yongye redspider
                        .then(CommandManager.literal("redspider").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendError(Text.literal("只能由玩家执行")); return 0; }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.RedSpiderEntity e =
                                    new com.yongye.entity.RedSpiderEntity(com.yongye.registry.ModEntities.RED_SPIDER, w);
                            e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(e);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤 BOSS·红蜘蛛").formatted(Formatting.RED), false);
                            return 1;
                        }))

                        // 召唤 BOSS·浴火凤凰(测试用):/yongye phoenix —— 飞行 BOSS,出生在头顶 6 格高空
                        .then(CommandManager.literal("phoenix").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendError(Text.literal("只能由玩家执行")); return 0; }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.FirePhoenixEntity e =
                                    new com.yongye.entity.FirePhoenixEntity(com.yongye.registry.ModEntities.FIRE_PHOENIX, w);
                            e.refreshPositionAndAngles(p.getX(), p.getY() + 6.0, p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(e);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤 BOSS·浴火凤凰").formatted(Formatting.GOLD), false);
                            return 1;
                        }))

                        // 召唤 BOSS·死亡法师(测试用):/yongye deathmage
                        .then(CommandManager.literal("deathmage").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendError(Text.literal("只能由玩家执行")); return 0; }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.DeathMageEntity e =
                                    new com.yongye.entity.DeathMageEntity(com.yongye.registry.ModEntities.DEATH_MAGE, w);
                            e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(e);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤 BOSS·死亡法师").formatted(Formatting.DARK_PURPLE), false);
                            return 1;
                        }))

                        // 召唤精英·巨型螃蟹(测试用):/yongye giantcrab
                        .then(CommandManager.literal("giantcrab").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendError(Text.literal("只能由玩家执行")); return 0; }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.GiantCrabEntity e =
                                    new com.yongye.entity.GiantCrabEntity(com.yongye.registry.ModEntities.GIANT_CRAB, w);
                            e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(e);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤精英·巨型螃蟹").formatted(Formatting.GREEN), false);
                            return 1;
                        }))

                        // 召唤 BOSS·阿努比斯(测试用):/yongye anubis
                        .then(CommandManager.literal("anubis").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendError(Text.literal("只能由玩家执行")); return 0; }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.AnubisEntity e =
                                    new com.yongye.entity.AnubisEntity(com.yongye.registry.ModEntities.ANUBIS, w);
                            e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(e);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤 BOSS·阿努比斯").formatted(Formatting.GOLD), false);
                            return 1;
                        }))

                        // 夜蚀侵蚀(测试/运营用):/yongye blight [半径],把脚下一片转为夜蚀群系
                        .then(CommandManager.literal("blight")
                                .executes(ctx -> runBlight(ctx.getSource(), 40))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(8, 128))
                                        .executes(ctx -> runBlight(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "radius")))))

                        // 召唤小怪·阿努比斯恶灵(测试用):/yongye wraith
                        .then(CommandManager.literal("wraith").executes(ctx -> {
                            net.minecraft.server.network.ServerPlayerEntity p = ctx.getSource().getPlayer();
                            if (p == null) { ctx.getSource().sendError(Text.literal("只能由玩家执行")); return 0; }
                            net.minecraft.server.world.ServerWorld w = p.getServerWorld();
                            com.yongye.entity.AnubisWraithEntity e =
                                    new com.yongye.entity.AnubisWraithEntity(com.yongye.registry.ModEntities.ANUBIS_WRAITH, w);
                            e.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0.0f);
                            w.spawnEntity(e);
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已召唤小怪·阿努比斯恶灵").formatted(Formatting.GRAY), false);
                            return 1;
                        }))

                        .then(CommandManager.literal("nightfall")
                                .then(CommandManager.literal("status").executes(ctx -> {
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("当前:" + NightfallManager.getLevelName()
                                                    + "(等级 " + NightfallManager.getLevel() + ")").formatted(Formatting.DARK_PURPLE), false);
                                    return 1;
                                }))
                                // 上界放开:m56 已把封顶移到 nightfallMaxLevel(默认99),setLevel 内部会钳;
                                // 若仍写 (0,5),/yongye nightfall 6+ 会被 Brigadier 拒绝,深渊层无法用命令触达。
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(0)).executes(ctx -> {
                                    NightfallManager.setLevel(ctx.getSource().getServer(), IntegerArgumentType.getInteger(ctx, "level"));
                                    return 1;
                                })))

                        .then(CommandManager.literal("redeem").executes(ctx -> {
                            NightfallManager.redeem(ctx.getSource().getServer());
                            return 1;
                        }))

                        // m201:碎武器(碎裂)总开关(切换)
                        // m337:强化转移(主手来源→副手目标)
                        .then(CommandManager.literal("transfer").executes(ctx -> {
                                    if (ctx.getSource().getEntity() instanceof net.minecraft.server.network.ServerPlayerEntity sp) {
                                        EquipmentEnhancer.transfer(sp);
                                    }
                                    return 1;
                        }))
                        .then(CommandManager.literal("enhancebreak").executes(ctx -> {
                                    boolean v = !com.yongye.YongyeConfig.get().enableEnhanceBreak;
                                    com.yongye.YongyeConfig.get().enableEnhanceBreak = v;
                                    com.yongye.YongyeConfig.save();
                                    ctx.getSource().sendFeedback(() -> Text.literal("碎武器(碎裂)= " + v + "(已保存)").formatted(Formatting.GREEN), false);
                                    return 1;
                        }))
                        // m198:每次强化自动消耗保护卷 开关(无参=切换,或跟 true/false)
                        .then(CommandManager.literal("protectperop")
                                .executes(ctx -> {
                                    boolean v = !com.yongye.YongyeConfig.get().enhanceProtectPerOperation;
                                    com.yongye.YongyeConfig.get().enhanceProtectPerOperation = v;
                                    com.yongye.YongyeConfig.save();
                                    ctx.getSource().sendFeedback(() -> Text.literal("每次强化自动消耗保护卷 = " + v + "(已保存)").formatted(Formatting.GREEN), false);
                                    return 1;
                                })
                                .then(CommandManager.argument("v", BoolArgumentType.bool()).executes(ctx -> {
                                    boolean v = BoolArgumentType.getBool(ctx, "v");
                                    com.yongye.YongyeConfig.get().enhanceProtectPerOperation = v;
                                    com.yongye.YongyeConfig.save();
                                    ctx.getSource().sendFeedback(() -> Text.literal("每次强化自动消耗保护卷 = " + v + "(已保存)").formatted(Formatting.GREEN), false);
                                    return 1;
                                })))

                        // 世界难度:查看 / 设定(0游玩~6永夜,7=战斗爽,强度介于4地狱与5深渊)。整局一个值,设定即全局生效。
                        // m251 修:上限原硬编码 6,战斗爽(7)用命令设不进去——改按枚举长度。
                        .then(CommandManager.literal("difficulty")
                                .then(CommandManager.literal("status").executes(ctx -> {
                                    int lv = com.yongye.system.DifficultyManager.getLevel();
                                    String name = lv < 0 ? "未设定(按适中)" : com.yongye.item.GameDifficulty.byOrdinal(lv).cn + "(等级 " + lv + ")";
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("当前世界难度:" + name).formatted(Formatting.GOLD), false);
                                    return 1;
                                }))
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(0, com.yongye.item.GameDifficulty.values().length - 1)).executes(ctx -> {
                                    com.yongye.system.DifficultyManager.setLevel(ctx.getSource().getServer(),
                                            IntegerArgumentType.getInteger(ctx, "level"));
                                    return 1;
                                })))

                        // 打开调试 / 运营菜单(客户端 DebugScreen):服务端发 S2C 包,客户端收到即开界面。
                        // 菜单里的按钮再 sendCommand 回这些 /yongye 子命令,故仍受权限2约束。
                        .then(CommandManager.literal("debug").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            // 仅限指定 ID 打开 debug 菜单:其余玩家(即便有 OP/权限2)一律拒绝
                            String debugName = p.getGameProfile().getName();
                            if (DEBUG_OWNERS.stream().noneMatch(debugName::equalsIgnoreCase)) {
                                ctx.getSource().sendFeedback(() ->
                                        Text.literal("[夜蚀] 调试菜单仅限管理员(" + String.join("、", DEBUG_OWNERS) + ")使用。").formatted(Formatting.RED), false);
                                return 0;
                            }
                            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new com.yongye.network.OpenDebugPayload());
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
                                .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 1000000000)).executes(ctx -> {
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
                                        .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 1000000000)).executes(ctx -> {
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

                        // 把附近 16 格内的怪物就地变精英(测试精英光环/属性,免等 4% 概率刷新)
                        .then(CommandManager.literal("elite").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            int n = EliteHandler.makeNearbyElite(p, 16.0);
                            ctx.getSource().sendFeedback(() -> Text.literal(n > 0
                                    ? "已把附近 " + n + " 只怪物变为精英(看周身幽蓝魂火光环)"
                                    : "附近 16 格内没有可精英化的怪物——先在夜晚/洞穴附近刷点怪再用").formatted(Formatting.GOLD), false);
                            return 1;
                        }))

                        // 把附近 16 格内的怪物就地变 BOSS(测试怪物BOSS版:红血条/大属性/Boss能力/掉落,免等概率刷新)
                        .then(CommandManager.literal("mobboss").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            int n = MobBossHandler.makeNearbyMobBoss(p, 16.0);
                            ctx.getSource().sendFeedback(() -> Text.literal(n > 0
                                    ? "已把附近 " + n + " 只怪物变为 BOSS(顶部红色血条 + 体型放大 + Boss 能力)"
                                    : "附近 16 格内没有可BOSS化的怪物——先在夜晚/洞穴附近刷点怪再用").formatted(Formatting.DARK_RED), false);
                            return 1;
                        }))

                        // m413 召唤师干弟自定义(作者点名:输 ID 自动拉皮肤;皮肤客户端下一帧即换,名字在场即改)
                        .then(CommandManager.literal("puppet")
                                .then(CommandManager.literal("skin")
                                        .then(CommandManager.argument("槽", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                                .then(CommandManager.argument("ID", StringArgumentType.word())
                                                        .executes(ctx -> puppetSet(ctx.getSource().getPlayerOrThrow(),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "槽"),
                                                                StringArgumentType.getString(ctx, "ID"), true)))))
                                .then(CommandManager.literal("name")
                                        .then(CommandManager.argument("槽", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 5))
                                                .then(CommandManager.argument("名字", StringArgumentType.greedyString())
                                                        .executes(ctx -> puppetSet(ctx.getSource().getPlayerOrThrow(),
                                                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "槽"),
                                                                StringArgumentType.getString(ctx, "名字"), false)))))
                                .then(CommandManager.literal("reset").executes(ctx -> {
                                    setConfigField("summonGanDiSkins", "");
                                    setConfigField("summonGanDiNames", "");
                                    int n = SummonerHandler.applyGanDiNames(ctx.getSource().getPlayerOrThrow());
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "干弟皮肤与名字已全部还原默认" + (n > 0 ? "(在场 " + n + " 只已改回)" : "")).formatted(Formatting.AQUA), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("list").executes(ctx -> {
                                    var c = com.yongye.YongyeConfig.get();
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "干弟自定义 · 皮肤槽=[" + c.summonGanDiSkins + "] 名字槽=[" + c.summonGanDiNames
                                                    + "](槽 1~5 对应 岛风/晚安/不爱肝/迷人/芥末)").formatted(Formatting.AQUA), false);
                                    return 1;
                                })))

                        // m411(路线图23)FX 测试:不跑真实流程,一条命令触发各演出/压测(纯视觉零状态)
                        .then(CommandManager.literal("fxtest")
                                .then(CommandManager.literal("damage")
                                        .executes(ctx -> fxtestDamage(ctx.getSource().getPlayerOrThrow(), 12, false))
                                        .then(CommandManager.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 400))
                                                .executes(ctx -> fxtestDamage(ctx.getSource().getPlayerOrThrow(),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n"), false))))
                                .then(CommandManager.literal("stress")
                                        .executes(ctx -> fxtestDamage(ctx.getSource().getPlayerOrThrow(), 100, true))
                                        .then(CommandManager.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> fxtestDamage(ctx.getSource().getPlayerOrThrow(),
                                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "n"), true))))
                                .then(CommandManager.literal("lootbeam").executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    var w = p.getServerWorld();
                                    net.minecraft.item.ItemStack[] drops = {
                                            new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.CHAOS_BLADE),   // 金(职业武器口径)
                                            new net.minecraft.item.ItemStack(net.minecraft.item.Items.ENCHANTED_GOLDEN_APPLE), // 紫(EPIC)
                                            new net.minecraft.item.ItemStack(net.minecraft.item.Items.GOLDEN_APPLE)};     // 蓝(RARE)
                                    for (int i = 0; i < drops.length; i++) {
                                        var e = new net.minecraft.entity.ItemEntity(w,
                                                p.getX() + (i - 1) * 2.0, p.getY() + 0.3, p.getZ() + 2.5, drops[i]);
                                        e.setVelocity(0, 0.1, 0);
                                        w.spawnEntity(e);
                                    }
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "已在面前落下 金/紫/蓝 三档测试掉落(真实物品,验完记得捡走或清掉)").formatted(Formatting.GOLD), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("nightfall")
                                        .then(CommandManager.argument("lvl", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 99))
                                                .executes(ctx -> {
                                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                                    int lvl = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "lvl");
                                                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                                                            new com.yongye.network.FxTestPayload(com.yongye.network.FxTestPayload.NIGHTFALL,
                                                                    lvl, NightfallManager.nameOf(lvl)));
                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                            "已触发永夜转场演出(纯视觉,真实等级不变):" + NightfallManager.nameOf(lvl)
                                                                    + (lvl > 0 ? " [升级观感]" : " [降级观感]")).formatted(Formatting.GOLD), false);
                                                    return 1;
                                                })))
                                .then(CommandManager.literal("bosskill").executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                                            new com.yongye.network.FxTestPayload(com.yongye.network.FxTestPayload.BOSSKILL, 0, "测试 · 灾厄之主"));
                                    ctx.getSource().sendFeedback(() -> Text.literal("已触发讨伐演出(纯视觉)").formatted(Formatting.GOLD), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("cast").executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                                            new com.yongye.network.FxTestPayload(com.yongye.network.FxTestPayload.CAST, 0, ""));
                                    ctx.getSource().sendFeedback(() -> Text.literal("已触发大招起手光晕(纯视觉)").formatted(Formatting.GOLD), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("panel").executes(ctx -> {
                                    boolean cur = com.yongye.YongyeConfig.get().enableFxDebugHud;
                                    setConfigField("enableFxDebugHud", String.valueOf(!cur));
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "FX 调试面板:" + (!cur ? "已开启(左下角)" : "已关闭")).formatted(Formatting.AQUA), false);
                                    return 1;
                                })))

                        .then(CommandManager.literal("config")
                                .then(CommandManager.literal("reset").executes(ctx -> {
                                    com.yongye.YongyeConfig.reset();
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("配置已重置为默认值(部分改动重进世界生效)").formatted(Formatting.AQUA), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("get")
                                        .then(CommandManager.argument("key", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    String val = com.yongye.YongyeConfig.getFieldString(key);
                                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                                            val != null ? key + " = " + val : "没有这个配置字段:" + key)
                                                            .formatted(val != null ? Formatting.AQUA : Formatting.RED), false);
                                                    return val != null ? 1 : 0;
                                                })))
                                .then(CommandManager.literal("set")
                                        .then(CommandManager.argument("key", StringArgumentType.word())
                                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                                        .executes(ctx -> {
                                                            String msg = setConfigField(StringArgumentType.getString(ctx, "key"),
                                                                    StringArgumentType.getString(ctx, "value"));
                                                            ctx.getSource().sendFeedback(() ->
                                                                    Text.literal(msg).formatted(Formatting.AQUA), false);
                                                            return 1;
                                                        }))))
                                .then(CommandManager.literal("get")
                                        .then(CommandManager.argument("key", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    ctx.getSource().sendFeedback(() ->
                                                            Text.literal(key + " = " + getConfigField(key)).formatted(Formatting.AQUA), false);
                                                    return 1;
                                                })))
                                .then(CommandManager.literal("list").executes(ctx -> {
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal(listConfigFields()).formatted(Formatting.GRAY), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("check").executes(ctx -> {
                                    String report = com.yongye.YongyeConfig.diagnose();
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("【配置诊断】\n" + report).formatted(Formatting.AQUA), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("export").executes(ctx -> {
                                    com.yongye.YongyeConfig.save(); // 确保最新值已写盘
                                    String path = com.yongye.YongyeConfig.configPath().toAbsolutePath().toString();
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "配置已保存。文件位置:\n" + path
                                            + "\n把这个 yongye.json 发给作者即可设为默认配置。").formatted(Formatting.AQUA), false);
                                    com.yongye.Yongye.LOGGER.info("[夜蚀] 配置导出路径: {}", path);
                                    return 1;
                                })))

                        .then(CommandManager.literal("wardbook").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.WARD_BOOK));
                            ctx.getSource().sendFeedback(() -> Text.literal("已获得【守护附魔书】").formatted(Formatting.LIGHT_PURPLE), false);
                            return 1;
                        }))
                        // m366:猎杀勋章——有待选卡重推三选一弹屏(意外关屏找回);无待选显示层数汇总与进度
                        .then(CommandManager.literal("medal").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            String pending = p.getAttachedOrElse(com.yongye.registry.ModAttachments.HUNT_PENDING, "");
                            if (!pending.isEmpty()) {
                                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                                        new com.yongye.network.OpenMedalChoicePayload(HuntMedalHandler.buildData(p)));
                                ctx.getSource().sendFeedback(() -> Text.literal("已重新打开勋章三选一").formatted(Formatting.GOLD), false);
                                return 1;
                            }
                            StringBuilder sb = new StringBuilder("猎杀勋章:");
                            boolean any = false;
                            java.util.Map<String, Integer> mm =
                                    p.getAttachedOrElse(com.yongye.registry.ModAttachments.HUNT_MEDALS, java.util.Map.of());
                            for (int i = 0; i < HuntMedalHandler.IDS.length; i++) {
                                int lv = mm.getOrDefault(HuntMedalHandler.IDS[i], 0);
                                if (lv > 0) {
                                    if (any) sb.append(" · ");
                                    sb.append(HuntMedalHandler.NAMES[i]).append(" Lv.").append(lv);
                                    any = true;
                                }
                            }
                            if (!any) sb.append("暂无");
                            int remain = HuntMedalHandler.hudRemain(p);
                            if (remain >= 0) sb.append("(再杀 ").append(remain).append(" 只触发三选一)");
                            final String out = sb.toString();
                            ctx.getSource().sendFeedback(() -> Text.literal(out).formatted(Formatting.GOLD), false);
                            return 1;
                        }))
                        // m200:发强化保护卷(便于测试/管理;正常玩法靠杀怪低概率掉落 + 杀怪累计到阈值自动兑换)
                        .then(CommandManager.literal("protectscroll").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.ENHANCE_PROTECT_SCROLL, 16));
                            ctx.getSource().sendFeedback(() -> Text.literal("已获得【强化保护卷】×16").formatted(Formatting.LIGHT_PURPLE), false);
                            return 1;
                        }))

                        .then(CommandManager.literal("top").executes(ctx -> {
                            java.util.List<ServerPlayerEntity> list =
                                    new java.util.ArrayList<>(ctx.getSource().getServer().getPlayerManager().getPlayerList());
                            list.sort((a, b) -> {
                                int na = a.getAttachedOrElse(com.yongye.registry.ModAttachments.BEST_NIGHTFALL, 0);
                                int nb = b.getAttachedOrElse(com.yongye.registry.ModAttachments.BEST_NIGHTFALL, 0);
                                if (na != nb) return Integer.compare(nb, na);
                                return Integer.compare(b.getAttachedOrElse(com.yongye.registry.ModAttachments.BEST_DAY, 0),
                                        a.getAttachedOrElse(com.yongye.registry.ModAttachments.BEST_DAY, 0));
                            });
                            final java.util.List<ServerPlayerEntity> fl = list;
                            ctx.getSource().sendFeedback(() -> {
                                net.minecraft.text.MutableText t =
                                        Text.literal("=== 永夜·存活排行(在线)===").formatted(Formatting.GOLD);
                                int rank = 1;
                                for (ServerPlayerEntity pp : fl) {
                                    t.append(Text.literal("\n" + rank + ". " + pp.getGameProfile().getName()
                                            + " — 永夜 " + pp.getAttachedOrElse(com.yongye.registry.ModAttachments.BEST_NIGHTFALL, 0)
                                            + " 层 / 第 " + pp.getAttachedOrElse(com.yongye.registry.ModAttachments.BEST_DAY, 0) + " 天")
                                            .formatted(Formatting.GRAY));
                                    if (++rank > 10) break;
                                }
                                return t;
                            }, false);
                            return 1;
                        }))

                        .then(CommandManager.literal("recover").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            int lost = p.getAttachedOrElse(com.yongye.registry.ModAttachments.LOST_WEAPON_ENHANCE, 0);
                            if (lost <= 0) {
                                ctx.getSource().sendFeedback(() -> Text.literal("没有可找回的强化(被夺且未夺回的武器才会记录其强化等级)").formatted(Formatting.GRAY), false);
                                return 0;
                            }
                            net.minecraft.item.ItemStack held = p.getMainHandStack();
                            if (held.isEmpty() || !com.yongye.system.EquipmentEnhancer.isWeapon(held)) {
                                ctx.getSource().sendFeedback(() -> Text.literal("请手持一把武器作为转移目标").formatted(Formatting.RED), false);
                                return 0;
                            }
                            int keep = (int) Math.floor(lost * com.yongye.YongyeConfig.get().weaponRecoverKeepFraction);
                            int newLevel = com.yongye.system.EquipmentEnhancer.getLevel(held) + keep;
                            p.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,
                                    com.yongye.system.EquipmentEnhancer.withLevel(held, newLevel));
                            p.setAttached(com.yongye.registry.ModAttachments.LOST_WEAPON_ENHANCE, 0);
                            final int kept = keep, lostF = lost;
                            ctx.getSource().sendFeedback(() -> Text.literal(
                                    "已将丢失武器 " + lostF + " 级强化的 2/3(+" + kept + " 级)转移到当前武器").formatted(Formatting.AQUA), false);
                            return 1;
                        }))

                        .then(CommandManager.literal("classbook")
                                .then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    String tid = StringArgumentType.getString(ctx, "type");
                                    com.yongye.item.PlayerClass cls = com.yongye.item.PlayerClass.byId(tid);
                                    if (cls == null) {
                                        ctx.getSource().sendError(Text.literal("未知职业: " + tid + "(tank/warrior/warlock/swordsman/monk/assassin/summoner)"));
                                        return 0;
                                    }
                                    p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.getClassBook(cls)));
                                    ctx.getSource().sendFeedback(() -> Text.literal("已获得【职业书·" + cls.cn + "】").formatted(Formatting.AQUA), false);
                                    return 1;
                                })))

                        .then(CommandManager.literal("level")
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 5000)).executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    int n = IntegerArgumentType.getInteger(ctx, "n");
                                    p.setExperienceLevel(n);
                                    ctx.getSource().sendFeedback(() -> Text.literal("已设置等级为 " + n).formatted(Formatting.GREEN), false);
                                    return 1;
                                })))

                        .then(CommandManager.literal("chaosblade").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.CHAOS_BLADE));
                            ctx.getSource().sendFeedback(() ->
                                    Text.literal("已获得【混沌之刃】").formatted(Formatting.DARK_PURPLE), false);
                            return 1;
                        }))

                        .then(CommandManager.literal("classweapon")
                                .then(CommandManager.argument("type", StringArgumentType.word()).executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    String tid = StringArgumentType.getString(ctx, "type");
                                    com.yongye.item.PlayerClass cls = com.yongye.item.PlayerClass.byId(tid);
                                    if (cls == null) {
                                        ctx.getSource().sendError(Text.literal("未知职业: " + tid + "(tank/warrior/warlock/swordsman/monk/assassin/summoner)"));
                                        return 0;
                                    }
                                    net.minecraft.item.Item w = com.yongye.registry.ModItems.getClassWeapon(cls);
                                    if (w == null) {
                                        ctx.getSource().sendError(Text.literal("【" + cls.cn + "】是无武器职业,没有专属武器"));
                                        return 0;
                                    }
                                    p.giveItemStack(new net.minecraft.item.ItemStack(w));
                                    ctx.getSource().sendFeedback(() -> Text.literal("已获得【" + cls.cn + "专属武器】").formatted(Formatting.GOLD), false);
                                    return 1;
                                })))

                        .then(CommandManager.literal("tankshield").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.TANK_SHIELD));
                            ctx.getSource().sendFeedback(() -> Text.literal("已获得【磐盾】").formatted(Formatting.GOLD), false);
                            return 1;
                        }))

                        // 掉落率实时热调(改完下一只怪即生效,并写盘持久化)
                        .then(CommandManager.literal("loot")
                                .then(CommandManager.literal("show").executes(ctx -> {
                                    com.yongye.YongyeConfig c = com.yongye.YongyeConfig.get();
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                            "掉落配置  随机掉落=" + c.enableRandomLoot
                                            + "  碎片=" + c.lifeShardDropChance
                                            + "  结晶(普通)=" + c.lifeCrystalDropChance
                                            + "  核心(精英)=" + c.lifeCoreDropChance
                                            + "  血核(精英)=" + c.bloodCoreDropChanceElite).formatted(Formatting.AQUA), false);
                                    return 1;
                                }))
                                .then(CommandManager.literal("shard")
                                        .then(CommandManager.argument("v", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                                            double v = DoubleArgumentType.getDouble(ctx, "v");
                                            com.yongye.YongyeConfig.get().lifeShardDropChance = v;
                                            com.yongye.YongyeConfig.save();
                                            ctx.getSource().sendFeedback(() -> Text.literal("生命碎片掉率=" + v + "(普通怪;已即时生效并保存)").formatted(Formatting.GREEN), false);
                                            return 1;
                                        })))
                                .then(CommandManager.literal("crystal")
                                        .then(CommandManager.argument("v", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                                            double v = DoubleArgumentType.getDouble(ctx, "v");
                                            com.yongye.YongyeConfig.get().lifeCrystalDropChance = v;
                                            com.yongye.YongyeConfig.save();
                                            ctx.getSource().sendFeedback(() -> Text.literal("生命结晶掉率=" + v + "(普通怪;精英自动翻倍;已生效并保存)").formatted(Formatting.GREEN), false);
                                            return 1;
                                        })))
                                .then(CommandManager.literal("core")
                                        .then(CommandManager.argument("v", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                                            double v = DoubleArgumentType.getDouble(ctx, "v");
                                            com.yongye.YongyeConfig.get().lifeCoreDropChance = v;
                                            com.yongye.YongyeConfig.save();
                                            ctx.getSource().sendFeedback(() -> Text.literal("生命核心掉率=" + v + "(仅精英;已生效并保存)").formatted(Formatting.GREEN), false);
                                            return 1;
                                        })))
                                .then(CommandManager.literal("bloodcore")
                                        .then(CommandManager.argument("v", DoubleArgumentType.doubleArg(0.0, 1.0)).executes(ctx -> {
                                            double v = DoubleArgumentType.getDouble(ctx, "v");
                                            com.yongye.YongyeConfig.get().bloodCoreDropChanceElite = v;
                                            com.yongye.YongyeConfig.save();
                                            ctx.getSource().sendFeedback(() -> Text.literal("灾厄血核掉率=" + v + "(仅精英;已生效并保存)").formatted(Formatting.GREEN), false);
                                            return 1;
                                        })))
                                .then(CommandManager.literal("enable")
                                        .then(CommandManager.argument("v", BoolArgumentType.bool()).executes(ctx -> {
                                            boolean v = BoolArgumentType.getBool(ctx, "v");
                                            com.yongye.YongyeConfig.get().enableRandomLoot = v;
                                            com.yongye.YongyeConfig.save();
                                            ctx.getSource().sendFeedback(() -> Text.literal("随机掉落系统=" + v + "(已生效并保存)").formatted(Formatting.GREEN), false);
                                            return 1;
                                        }))))

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
                );

                dispatcher.register(CommandManager.literal("talent")
                        .executes(ctx -> TalentManager.overview(ctx.getSource().getPlayerOrThrow()))
                        .then(CommandManager.literal("list")
                                .executes(ctx -> TalentManager.list(ctx.getSource().getPlayerOrThrow())))
                        .then(CommandManager.literal("reset")
                                .executes(ctx -> TalentManager.reset(ctx.getSource().getPlayerOrThrow())))
                        .then(CommandManager.literal("learn")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> TalentManager.learn(ctx.getSource().getPlayerOrThrow(),
                                                StringArgumentType.getString(ctx, "id")))))
                        .then(CommandManager.literal("info")
                                .then(CommandManager.argument("id", StringArgumentType.word())
                                        .executes(ctx -> TalentManager.info(ctx.getSource().getPlayerOrThrow(),
                                                StringArgumentType.getString(ctx, "id")))))
                );
        });

        Yongye.LOGGER.info("[夜蚀] 指令已注册");
    }

    // ===== 通用配置读写(反射:任意 YongyeConfig 公共实例字段都能在游戏内 set/get/list)=====
    // 支持类型:boolean / int / long / double / String。数组等复杂字段只读不写。
    // 改完立即写盘(YongyeConfig.save());部分字段需重进世界才生效。

    /** m413:改干弟第 slot(1~5) 槽的皮肤ID或名字——重组逗号串写回配置,名字顺手给在场小队即改。 */
    private static int puppetSet(ServerPlayerEntity p, int slot, String value, boolean isSkin) {
        var c = com.yongye.YongyeConfig.get();
        String raw = isSkin ? c.summonGanDiSkins : c.summonGanDiNames;
        String[] parts = (raw == null ? "" : raw).split(",", -1);
        String[] five = new String[5];
        for (int i = 0; i < 5; i++) five[i] = i < parts.length ? parts[i] : "";
        String v0 = value == null ? "" : value.trim().replace(",", "");
        five[slot - 1] = "-".equals(v0) ? "" : v0; // 槽内不许带逗号;"-"=哨兵清槽回默认(m414 UI 空框下发)
        setConfigField(isSkin ? "summonGanDiSkins" : "summonGanDiNames", String.join(",", five));
        int renamed = isSkin ? 0 : SummonerHandler.applyGanDiNames(p);
        String what = isSkin ? "皮肤ID" : "名字";
        p.sendMessage(Text.literal("干弟槽 " + slot + " 的" + what + " → " + (five[slot - 1].isEmpty() ? "默认" : five[slot - 1])
                + (isSkin ? "(客户端拉取中,几秒内换肤;拉不到=原贴图)" : renamed > 0 ? "(在场 " + renamed + " 只已改名)" : "(下次召唤生效)"))
                .formatted(Formatting.GOLD), false);
        return 1;
    }

    /** m411 fxtest:朝玩家周围撒 n 条测试飘字(damage=四档轮换散布;stress=紧簇+同目标id 轮换练合并)。 */
    private static int fxtestDamage(ServerPlayerEntity p, int n, boolean stress) {
        var rnd = p.getRandom();
        for (int i = 0; i < n; i++) {
            double spread = stress ? 1.5 : 3.0;
            double x = p.getX() + (rnd.nextDouble() - 0.5) * 2 * spread;
            double z = p.getZ() + (rnd.nextDouble() - 0.5) * 2 * spread + 2.0;
            double y = p.getY() + 1.0 + rnd.nextDouble() * 1.5;
            int kind = i % 4;                                        // HIT/HEAVY/CRITICAL/EXECUTION 轮换
            float amount = switch (kind) {
                case 1 -> 200 + rnd.nextInt(1800);
                case 2 -> 500 + rnd.nextInt(4500);
                case 3 -> 1000 + rnd.nextInt(9000);
                default -> 5 + rnd.nextInt(95);
            };
            int targetId = stress ? 900_000 + (i % 6) : 0;           // stress:同 id 轮换练 m406 合并窗口
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                    new com.yongye.network.DamageNumberPayload(x, y, z, amount, kind, targetId));
        }
        p.sendMessage(Text.literal("fxtest:已发 " + n + " 条测试飘字" + (stress ? "(压力簇+合并)" : "")).formatted(Formatting.GOLD), true);
        return 1;
    }

    private static String setConfigField(String key, String value) {
        com.yongye.YongyeConfig cfg = com.yongye.YongyeConfig.get();
        try {
            java.lang.reflect.Field f = com.yongye.YongyeConfig.class.getField(key);
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) return "该字段不可设置:" + key;
            Class<?> t = f.getType();
            if (t == boolean.class) {
                f.setBoolean(cfg, value.equalsIgnoreCase("true") || value.equals("1") || value.equals("是"));
            } else if (t == int.class) {
                f.setInt(cfg, (int) Math.round(Double.parseDouble(value)));
            } else if (t == long.class) {
                f.setLong(cfg, (long) Math.round(Double.parseDouble(value)));
            } else if (t == double.class) {
                f.setDouble(cfg, Double.parseDouble(value));
            } else if (t == String.class) {
                f.set(cfg, value);
            } else {
                return "暂不支持该字段类型(" + t.getSimpleName() + "):" + key;
            }
            com.yongye.YongyeConfig.save();
            return "已设置 " + key + " = " + getConfigField(key) + "(部分改动重进世界生效)";
        } catch (NoSuchFieldException e) {
            return "无此配置字段:" + key + "(用 /yongye config list 查看全部)";
        } catch (NumberFormatException e) {
            return "数值无法解析:" + value;
        } catch (IllegalAccessException e) {
            return "设置失败:" + key;
        }
    }

    private static String getConfigField(String key) {
        try {
            java.lang.reflect.Field f = com.yongye.YongyeConfig.class.getField(key);
            Object v = f.get(com.yongye.YongyeConfig.get());
            if (v instanceof double[] arr) return java.util.Arrays.toString(arr);
            return String.valueOf(v);
        } catch (NoSuchFieldException e) {
            return "<无此字段>";
        } catch (IllegalAccessException e) {
            return "<读取失败>";
        }
    }

    private static String listConfigFields() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (java.lang.reflect.Field f : com.yongye.YongyeConfig.class.getFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            Class<?> t = f.getType();
            if (t == boolean.class || t == int.class || t == long.class || t == double.class || t == String.class) {
                if (n > 0) sb.append("、");
                sb.append(f.getName());
                n++;
            }
        }
        return "共 " + n + " 个可设字段:" + sb;
    }

    /** m212:/yongye blight [半径] —— 把执行者脚下一片已加载区块转为夜蚀群系。 */
    private static int runBlight(net.minecraft.server.command.ServerCommandSource src, int radius) {
        ServerPlayerEntity p = src.getPlayer();
        if (p == null) { src.sendError(Text.literal("只能由玩家执行")); return 0; }
        net.minecraft.server.world.ServerWorld w = p.getServerWorld();
        int chunks = com.yongye.system.NightBlightHandler.blightArea(w, p.getBlockPos(), radius);
        src.sendFeedback(() -> Text.literal(
                "夜蚀已吞噬周围 " + chunks + " 个区块(半径 " + radius + " 格)").formatted(Formatting.DARK_PURPLE), false);
        return chunks;
    }
}
