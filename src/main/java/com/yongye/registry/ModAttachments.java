package com.yongye.registry;

import com.mojang.serialization.Codec;
import com.yongye.Yongye;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.util.Identifier;

/**
 * 玩家数据附着。
 * LEARNED_HEALTH: 玩家累计学习的血量强化等级总和(V值)。
 *   实际额外最大生命 = LEARNED_HEALTH * 10。
 *   persistent: 存档保留; copyOnDeath: 死亡不丢失(永久成长)。
 */
public final class ModAttachments {
    private ModAttachments() {}

    public static final AttachmentType<Integer> LEARNED_HEALTH =
            AttachmentRegistry.<Integer>builder()
                    .persistent(Codec.INT)
                    .initializer(() -> 0)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "learned_health"));

    /**
     * MOB_ENHANCED: 标记某个怪物是否已被增强,避免反复 re-roll 随机药水。
     */
    public static final AttachmentType<Boolean> MOB_ENHANCED =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "mob_enhanced"));

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 数据附着已注册");
    }
}
