package com.yongye.registry;

import com.yongye.Yongye;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** 自定义音效注册:佩恩 BGM + 三大技能音效。 */
public final class ModSounds {
    private ModSounds() {}

    public static final SoundEvent PAIN_BGM = register("pain_bgm");
    public static final SoundEvent PAIN_ALMIGHTY_PUSH = register("pain_almighty_push");
    public static final SoundEvent PAIN_UNIVERSAL_PULL = register("pain_universal_pull");
    public static final SoundEvent PAIN_PLANETARY = register("pain_planetary");
    public static final SoundEvent HIM_JUMPSCARE = register("him_jumpscare");

    // —— 法师技能包音效(m246,素材见 docs/staging;先接大招 3 个,其余备用)——
    public static final SoundEvent SKILL_FIRE_ARMOR = register("skill_fire_armor");
    public static final SoundEvent SKILL_FIRE_ARMOR_PICKUP = register("skill_fire_armor_pickup");
    public static final SoundEvent SKILL_MAGIC_DEFENSE = register("skill_magic_defense");
    public static final SoundEvent SKILL_MAGIC_DEFENSE_DEFENSE = register("skill_magic_defense_defense");
    public static final SoundEvent SKILL_WHITE_STAR = register("skill_white_star");
    public static final SoundEvent SKILL_WHITE_STAR_STAR = register("skill_white_star_star");
    public static final SoundEvent SKILL_WHITE_STAR_STAR_HIT = register("skill_white_star_star_hit");
    public static final SoundEvent SKILL_TREE_HEAL = register("skill_tree_heal");
    public static final SoundEvent SKILL_FALLEN_HEART = register("skill_fallen_heart");
    public static final SoundEvent SKILL_FALLEN_HEART_HEARTPICKUP = register("skill_fallen_heart_heartpickup");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(Yongye.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
        Yongye.LOGGER.info("[夜蚀] 音效已注册");
    }
}
