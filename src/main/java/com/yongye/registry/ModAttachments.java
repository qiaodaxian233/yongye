package com.yongye.registry;

import com.mojang.serialization.Codec;
import com.yongye.Yongye;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.NbtCompound;
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

    /** LEARNED_SKILLS: 其它技能书(护甲/恢复/闪避/反伤/抗性)的累计等级,键为类型 id。 */
    public static final AttachmentType<java.util.Map<String, Integer>> LEARNED_SKILLS =
            AttachmentRegistry.<java.util.Map<String, Integer>>builder()
                    .persistent(Codec.unboundedMap(Codec.STRING, Codec.INT))
                    .initializer(java.util.HashMap::new)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "learned_skills"));

    /**
     * MOB_ENHANCED: 标记某个怪物是否已被增强,避免反复 re-roll 随机药水。
     */
    public static final AttachmentType<Boolean> MOB_ENHANCED =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "mob_enhanced"));

    /** IS_ELITE: 该怪物为精英怪。 */
    public static final AttachmentType<Boolean> IS_ELITE =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_elite"));

    /** IS_BOSS: 该实体为(翻倍)Boss。 */
    public static final AttachmentType<Boolean> IS_BOSS =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_boss"));

    /** IS_PAIN: 该实体为长门(佩恩)Boss。 */
    public static final AttachmentType<Boolean> IS_PAIN =
            AttachmentRegistry.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_pain"));

    /** IS_HIM: 该实体为 HIM 突脸惊吓体(无 AI、无敌、无伤害、短暂存在)。 */
    public static final AttachmentType<Boolean> IS_HIM =
            AttachmentRegistry.<Boolean>builder()
                    .initializer(() -> false)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "is_him"));

    /** ACCESSORIES: 玩家饰品栏(4 格神器),以 NBT 存档。 */
    public static final AttachmentType<NbtCompound> ACCESSORIES =
            AttachmentRegistry.<NbtCompound>builder()
                    .persistent(NbtCompound.CODEC)
                    .initializer(NbtCompound::new)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "accessories"));

    /** NO_HEAL_UNTIL: 玩家禁疗截止的游戏时刻(world time)。 */
    public static final AttachmentType<Long> NO_HEAL_UNTIL =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "no_heal_until"));

    /** DISARM_COOLDOWN_UNTIL: 玩家被精英缴械的冷却截止游戏时刻。 */
    public static final AttachmentType<Long> DISARM_COOLDOWN_UNTIL =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "disarm_cooldown_until"));

    /** EMBER_READY_AT: 不灭余烬下一次可触发的游戏时刻。 */
    public static final AttachmentType<Long> EMBER_READY_AT =
            AttachmentRegistry.<Long>builder()
                    .persistent(Codec.LONG)
                    .initializer(() -> 0L)
                    .copyOnDeath()
                    .buildAndRegister(Identifier.of(Yongye.MOD_ID, "ember_ready_at"));

    public static void init() {
        Yongye.LOGGER.info("[亡途荒夜] 数据附着已注册");
    }
}
