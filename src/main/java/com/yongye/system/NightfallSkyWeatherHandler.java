package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;

/**
 * 永夜天气联动(m435,「大工程点单区」最后一项落地):**原版的雨/雪/雷暴本身**随永夜等级加深而变多变久。
 *
 * <p><b>与 {@link NightfallWeatherHandler} 的分工(防重复,两者不是一回事)</b>:那边是「永夜天象」——
 * 血月/酸雨/流星雨,是本模组自造的**限时灾害事件**;本类不造任何事件,只**调原版天气本身的节律**:
 * 永夜越深,天越容易下雨、下得越久、越容易升级成雷暴。天象跑着的时候本类整体让路
 * ({@link NightfallWeatherHandler#eventActive()}),酸雨会强改天气,两边对着拧会打架。
 *
 * <p><b>为什么这算「联动」而不只是刷个天气</b>:原版雷暴本身就带一串玩法后果——天光压暗到亡灵
 * 白天不燃、光照判定下调导致刷怪位变多、末影人/苦力怕活动范围变广。所以只要把「下雨/雷暴的频率」
 * 挂到永夜等级上,危险度是靠**原版自己的机制**放大的,本模组一行伤害逻辑都不用碰——
 * 这也是本类刻意不加自定义受伤/减益的原因(那是天象那边的职责,重复叠加只会让后期无法出门)。
 *
 * <p><b>雪是免费的</b>:原版在寒冷群系把降雨渲染/结算成降雪,故「雨雪与永夜联动」一条实现两种表现,
 * 不需要按群系分支。
 *
 * <p>口径:只管主世界(下界/末地无天气);永夜 0 级=完全不介入,原版天气循环照旧;
 * 每 {@code nightfallWeatherCheckIntervalTicks} 检定一次(默 600t=30s),
 * 起雨概率 = {@code nightfallRainChancePerLevel × 永夜等级}(封顶 0.9),
 * 雨时长 = 基础 + 每级增量,雷暴需永夜 ≥ {@code nightfallThunderMinLevel} 再过一次概率。
 *
 * <p>零新 API:setWeather/isRaining 与 NightfallWeatherHandler 逐字同款(在树 CI 已编),
 * isThundering 已核 yarn 1.21.1 官方映射 method_8546。
 */
public final class NightfallSkyWeatherHandler {
    private NightfallSkyWeatherHandler() {}

    private static int checkTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.enableNightfallVanillaWeather) return;
            if (++checkTick < Math.max(20, cfg.nightfallWeatherCheckIntervalTicks)) return;
            checkTick = 0;

            int nf = NightfallManager.getLevel();
            if (nf < 1) return;                              // 永夜 0:原版天气循环原样不碰
            if (NightfallWeatherHandler.eventActive()) return; // 天象(尤其酸雨)在跑:让路,不对着拧

            ServerWorld w = server.getOverworld();           // 只有主世界有天气
            if (w == null) return;
            var rnd = w.getRandom();

            boolean thunderOk = nf >= cfg.nightfallThunderMinLevel
                    && rnd.nextDouble() < cfg.nightfallThunderChance;

            if (!w.isRaining()) {
                double chance = Math.min(0.9, Math.max(0.0, cfg.nightfallRainChancePerLevel) * nf);
                if (rnd.nextDouble() >= chance) return;
                int dur = Math.max(200, cfg.nightfallRainDurationBase + cfg.nightfallRainDurationPerLevel * nf);
                w.setWeather(0, dur, true, thunderOk);
                return;
            }
            // 已经在下雨:永夜够深时有机会**升级**成雷暴(不重置时长,只把雷加上;原版雷暴带来的
            // 压暗/刷怪后果就是本系统要的「越深越难出门」)
            if (!w.isThundering() && thunderOk) {
                int dur = Math.max(200, cfg.nightfallRainDurationBase);
                w.setWeather(0, dur, true, true);
            }
        });
        Yongye.LOGGER.info("[夜蚀] 永夜天气联动已挂载(原版雨雪雷随永夜加深)");
    }
}
