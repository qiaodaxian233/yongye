package com.yongye.system;

import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * m334:反卡 BUG 双机制(作者点名)。
 * ① 悬空卡怪:玩家站的支撑方块**正下方两格全是空气**(浮空平台/断桥搭台,怪够不着)——
 *    且 pillarCheeseMobRadius 内有敌对怪(有怪才算卡怪;和平建筑/搭桥不误伤)——宽限
 *    pillarCheeseGraceTicks 后每秒按最大生命百分比扣血,先给一次 actionbar 警告。
 *    注意:普通土柱(下面连着地)不会触发——判定的是支撑块下方悬空,不是"站得高"。
 * ② 泡水躲怪:连续在水中超过 waterCheeseGraceTicks(默认 1 分钟)——每秒百分比扣血,
 *    且每隔 waterCheeseSummonInterval 在身边水里召一只溺尸索敌玩家(吃全套怪物成长缩放),
 *    半程先警告。离水计时即清零。
 * 两机制均跳过创造/旁观/骑乘,均可整体关闭,数值全配置(伤害走 magic 源,不吃护甲,卡就得痛)。
 */
public final class AntiCheeseHandler {
    private AntiCheeseHandler() {}

    private static final Map<UUID, Integer> PILLAR_TICKS = new HashMap<>();
    private static final Map<UUID, Integer> WATER_TICKS = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if ((server.getTicks() % 20) != 0) return;   // 每秒一检,开销可忽略
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isCreative() || p.isSpectator() || !p.isAlive() || p.hasVehicle()) {
                    PILLAR_TICKS.remove(p.getUuid()); WATER_TICKS.remove(p.getUuid());
                    continue;
                }
                if (!(p.getWorld() instanceof ServerWorld sw)) continue;
                if (cfg.pillarCheesePunish) tickPillar(p, sw, cfg);
                if (cfg.waterCheesePunish) tickWater(p, sw, cfg);
            }
        });
    }

    // ---------- ① 悬空卡怪 ----------
    private static void tickPillar(ServerPlayerEntity p, ServerWorld sw, YongyeConfig cfg) {
        UUID id = p.getUuid();
        boolean cheesing = false;
        if (p.isOnGround() && !p.isTouchingWater()) {
            BlockPos support = p.getBlockPos().down();
            if (!sw.getBlockState(support).isAir()
                    && sw.getBlockState(support.down()).isAir()
                    && sw.getBlockState(support.down(2)).isAir()) {
                // 支撑块下两格全空 = 浮空立足;再看附近有没有敌对怪(有怪才算卡怪)
                double r = Math.max(4.0, cfg.pillarCheeseMobRadius);
                cheesing = !sw.getEntitiesByClass(HostileEntity.class,
                        Box.of(p.getPos(), r * 2, r * 2, r * 2),
                        m -> m.isAlive()).isEmpty();
            }
        }
        if (!cheesing) { PILLAR_TICKS.remove(id); return; }
        int t = PILLAR_TICKS.merge(id, 20, Integer::sum);
        int grace = Math.max(20, cfg.pillarCheeseGraceTicks);
        if (t == 20 || t == grace / 2 / 20 * 20) {
            p.sendMessage(Text.literal("⚠ 悬空卡怪已被盯上:大地开始震怒,快下来!").formatted(Formatting.RED), true);
        }
        if (t >= grace) {
            float dmg = (float) (p.getMaxHealth() * Math.max(0.005, cfg.pillarCheeseDamagePercent));
            p.damage(sw.getDamageSources().magic(), dmg);
            p.sendMessage(Text.literal("悬空反制:-" + String.format("%.0f", dmg) + " 血/秒").formatted(Formatting.DARK_RED), true);
        }
    }

    // ---------- ② 泡水躲怪 ----------
    private static void tickWater(ServerPlayerEntity p, ServerWorld sw, YongyeConfig cfg) {
        UUID id = p.getUuid();
        if (!p.isTouchingWater()) { WATER_TICKS.remove(id); return; }
        int t = WATER_TICKS.merge(id, 20, Integer::sum);
        int grace = Math.max(200, cfg.waterCheeseGraceTicks);
        if (t == grace / 2) {
            p.sendMessage(Text.literal("⚠ 水下的东西察觉到了你…别在水里待太久").formatted(Formatting.RED), true);
        }
        if (t < grace) return;
        // 百分比掉血(每秒)
        float dmg = (float) (p.getMaxHealth() * Math.max(0.005, cfg.waterCheeseDamagePercent));
        p.damage(sw.getDamageSources().magic(), dmg);
        // 定期召溺尸索敌(吃全套怪物成长缩放:MobEnhancementHandler 对新生成怪统一生效)
        int interval = Math.max(40, cfg.waterCheeseSummonIntervalTicks);
        if (((t - grace) % interval) == 0) {
            for (int i = 0; i < Math.max(1, cfg.waterCheeseSummonCount); i++) {
                DrownedEntity d = EntityType.DROWNED.create(sw);
                if (d == null) continue;
                double ang = sw.getRandom().nextDouble() * Math.PI * 2;
                d.refreshPositionAndAngles(p.getX() + Math.cos(ang) * 3.0, p.getY() - 0.5,
                        p.getZ() + Math.sin(ang) * 3.0, sw.getRandom().nextFloat() * 360f, 0f);
                d.setTarget(p);
                sw.spawnEntity(d);
            }
            p.sendMessage(Text.literal("深水潜伏反制:它们来了!").formatted(Formatting.DARK_RED), true);
        }
    }
}
