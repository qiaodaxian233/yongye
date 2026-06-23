package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * 战利品磁吸:把玩家附近、属于本 mod(命名空间 yongye)的掉落物拉向玩家,自动被原版拾取。
 * <p>只吸本 mod 的贵重材料/技能书/神器/职业书等;原版杂物(腐肉/骨头/线等)留在地上,交给
 * {@link ItemCleanupHandler} 定时清理。这样后期"地上东西太多拿不下"时,有价值的会自己飞过来,
 * 垃圾被定时清掉,背包不被杂物占满。
 */
public final class LootMagnetHandler {
    private LootMagnetHandler() {}

    private static final int PERIOD = 4;   // 每 4 tick 跑一次(省性能)

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % PERIOD != 0) return;
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableLootMagnet) return;
            double rad = Math.max(1.0, cfg.lootMagnetRadius);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isSpectator()) continue;
                if (!(p.getWorld() instanceof ServerWorld sw)) continue;
                var area = p.getBoundingBox().expand(rad);
                Vec3d target = p.getPos().add(0, 0.5, 0);
                List<ItemEntity> items = sw.getEntitiesByClass(ItemEntity.class, area,
                        ie -> ie.isAlive() && isModLoot(ie));
                for (ItemEntity ie : items) {
                    Vec3d dir = target.subtract(ie.getPos());
                    if (dir.length() < 0.6) continue;          // 足够近,等原版自然拾取
                    ie.setVelocity(dir.normalize().multiply(0.55)); // 拉向玩家
                    ie.velocityModified = true;                 // 同步速度到客户端
                }
            }
        });
        Yongye.LOGGER.info("[永夜] 战利品磁吸已挂载");
    }

    /** 该掉落物是否属于本 mod(命名空间 yongye)。 */
    private static boolean isModLoot(ItemEntity ie) {
        var id = Registries.ITEM.getId(ie.getStack().getItem());
        return id != null && Yongye.MOD_ID.equals(id.getNamespace());
    }
}
