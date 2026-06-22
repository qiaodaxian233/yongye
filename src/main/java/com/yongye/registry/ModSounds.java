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

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(Yongye.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void init() {
        Yongye.LOGGER.info("[永夜] 音效已注册");
    }
}
