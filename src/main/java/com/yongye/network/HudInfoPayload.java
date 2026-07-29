package com.yongye.network;

import com.yongye.Yongye;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端:战况看板数据(m288/m289,左边缘信息块)。
 * kills:      累计杀怪总数(TOTAL_KILLS 附件,跨登录/死亡累计);
 * nextName:   下一阶段名(""=已至上限,不显示预告行);
 * nextSeconds:距久留自动升层剩余秒数(-1=不适用,只显示阶段名不显示倒计时);
 * dayForecast:按天事件预告,服务端按**实时配置**拼好(""=没有未到的事件),如「第 6 天:佩恩降临 · 怪物学会挖掘(还有 3 天)」。
 * dayForecastShort:同一预告的紧凑版(m308,客户端 hudInfoCompact 开时显示),如「3天后:佩恩降临+1」——
 *   长短两版都发、客户端按自己配置挑,专用服上各客户端可各选各的。
 * 天数不走包——昼夜时钟原版就同步到客户端,ProgressionManager.gameDay 客户端直接算。
 * 每 20 tick 下发一次(KillStatsHandler)。
 */
public record HudInfoPayload(long kills, String nextName, int nextSeconds, String dayForecast,
                             String dayForecastShort, String mainGoal, int mainStage, String bounty) implements CustomPayload {
    // m361:mainGoal=主线目标常显行(""=不显示),MainQuestLine.hudGoal 每 20t 服务端拼好
    // m363:mainStage=当前主线阶段号(渐进解锁的门控信号,背包按钮按阶段点亮)
    // m364:bounty=每日悬赏同步串「streak;type,target,prog,done;×3」(""=未生成/关闭,任务书悬赏页展示)

    public static final CustomPayload.Id<HudInfoPayload> ID =
            new CustomPayload.Id<>(Identifier.of(Yongye.MOD_ID, "hud_info"));

    public static final PacketCodec<PacketByteBuf, HudInfoPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                buf.writeVarLong(value.kills);
                buf.writeString(value.nextName);
                buf.writeVarInt(value.nextSeconds);
                buf.writeString(value.dayForecast);
                buf.writeString(value.dayForecastShort);
                buf.writeString(value.mainGoal == null ? "" : value.mainGoal);   // 空值兜底(m336 铁律)
                buf.writeVarInt(value.mainStage);
                buf.writeString(value.bounty == null ? "" : value.bounty);       // m364 空值兜底
            },
            buf -> new HudInfoPayload(buf.readVarLong(), buf.readString(), buf.readVarInt(), buf.readString(),
                    buf.readString(), buf.readString(), buf.readVarInt(), buf.readString())
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}
