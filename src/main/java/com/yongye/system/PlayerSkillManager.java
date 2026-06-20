package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.registry.ModAttachments;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 玩家血量强化(技能书)的应用与持久化。
 *  - 累计等级存于玩家数据附着 LEARNED_HEALTH(跨死亡保留)。
 *  - 额外最大生命 = 累计等级 * 10,以属性修饰符形式应用。
 */
public final class PlayerSkillManager {
    private PlayerSkillManager() {}

    public static final Identifier SKILL_HEALTH_ID = Identifier.of(Yongye.MOD_ID, "skill_health");

    public enum LearnResult { OK, CAPPED }

    public static int getLearnedHealth(ServerPlayerEntity player) {
        return player.getAttachedOrElse(ModAttachments.LEARNED_HEALTH, 0);
    }

    /** 学习一本 V_level 技能书,累加到玩家血量强化总等级。 */
    public static LearnResult learnHealth(ServerPlayerEntity player, int level) {
        int max = YongyeConfig.get().skillBookMaxLevel;
        int cur = getLearnedHealth(player);
        if (cur >= max) {
            return LearnResult.CAPPED;
        }
        int next = Math.min(max, cur + Math.max(1, level));
        player.setAttached(ModAttachments.LEARNED_HEALTH, next);
        applyHealthModifier(player);
        com.yongye.network.YongyeNet.sendStats(player);
        return LearnResult.OK;
    }

    /** 依据当前累计等级重新应用最大生命修饰符。需在加入世界/重生后调用。 */
    public static void applyHealthModifier(ServerPlayerEntity player) {
        EntityAttributeInstance inst = player.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (inst == null) return;
        inst.removeModifier(SKILL_HEALTH_ID);
        int total = getLearnedHealth(player);
        if (total > 0) {
            inst.addTemporaryModifier(new EntityAttributeModifier(
                    SKILL_HEALTH_ID,
                    total * 10.0,
                    EntityAttributeModifier.Operation.ADD_VALUE));
        }
    }
}
