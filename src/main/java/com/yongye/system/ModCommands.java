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

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> {
                dispatcher.register(CommandManager.literal("yongye")
                        .requires(s -> s.hasPermissionLevel(2))

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

                        // 打开调试 / 运营菜单(客户端 DebugScreen):服务端发 S2C 包,客户端收到即开界面。
                        // 菜单里的按钮再 sendCommand 回这些 /yongye 子命令,故仍受权限2约束。
                        .then(CommandManager.literal("debug").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
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

                        .then(CommandManager.literal("config")
                                .then(CommandManager.literal("reset").executes(ctx -> {
                                    com.yongye.YongyeConfig.reset();
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("配置已重置为默认值(部分改动重进世界生效)").formatted(Formatting.AQUA), false);
                                    return 1;
                                }))
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
                                })))

                        .then(CommandManager.literal("wardbook").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.WARD_BOOK));
                            ctx.getSource().sendFeedback(() -> Text.literal("已获得【守护附魔书】").formatted(Formatting.LIGHT_PURPLE), false);
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
                                        ctx.getSource().sendError(Text.literal("未知职业: " + tid + "(tank/warrior/warlock/swordsman/monk/assassin)"));
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
                                        ctx.getSource().sendError(Text.literal("未知职业: " + tid + "(tank/warrior/warlock/swordsman/monk/assassin)"));
                                        return 0;
                                    }
                                    p.giveItemStack(new net.minecraft.item.ItemStack(com.yongye.registry.ModItems.getClassWeapon(cls)));
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

        Yongye.LOGGER.info("[永夜] 指令已注册");
    }

    // ===== 通用配置读写(反射:任意 YongyeConfig 公共实例字段都能在游戏内 set/get/list)=====
    // 支持类型:boolean / int / long / double / String。数组等复杂字段只读不写。
    // 改完立即写盘(YongyeConfig.save());部分字段需重进世界才生效。

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
}
