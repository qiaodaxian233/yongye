package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.item.PlayerClass;
import com.yongye.network.MagicFxPayload;
import com.yongye.registry.ModSounds;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

/**
 * 技能施放特效派发(m246)——大招成功施放时,脚下展开职业色魔法阵 + 播法师包音效。
 * 阵按职业配色:战士红 / 坦克绿(守护) / 刺客·召唤师粉 / 术士蓝 / 武僧·剑客黄绿;
 * 音效同理:战士=火焰甲、坦克=魔法防御、术士=白星、其余=白星,治疗类留 tree_heal 备用。
 * 只发给 64 格内同世界玩家(含施放者),照 PainBossHandler 的就近广播口径。
 */
public final class SkillFxHelper {
    private SkillFxHelper() {}

    public static void ultimateFx(ServerPlayerEntity p, PlayerClass c) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.magicCircleEnabled) return;
        if (!(p.getWorld() instanceof ServerWorld sw)) return;

        int color = switch (c) {
            case WARRIOR -> 4;               // 红
            case TANK -> 1;                  // 绿
            case ASSASSIN, SUMMONER -> 3;    // 粉
            case WARLOCK -> 0;               // 蓝
            case MONK, SWORDSMAN -> 2;       // 黄绿
        };
        float radius = (float) (2.6 * Math.max(0.3, cfg.magicCircleScale));
        MagicFxPayload payload = new MagicFxPayload(color, p.getX(), p.getY() + 0.06, p.getZ(), radius);
        for (ServerPlayerEntity sp : sw.getServer().getPlayerManager().getPlayerList()) {
            if (sp.getWorld() == sw && sp.squaredDistanceTo(p) < 64 * 64) {
                ServerPlayNetworking.send(sp, payload);
            }
        }
        SoundEvent snd = switch (c) {
            case WARRIOR -> ModSounds.SKILL_FIRE_ARMOR;
            case TANK -> ModSounds.SKILL_MAGIC_DEFENSE;
            default -> ModSounds.SKILL_WHITE_STAR;
        };
        sw.playSound(null, p.getBlockPos(), snd, SoundCategory.PLAYERS, 0.9f, 1.0f);
    }
}
