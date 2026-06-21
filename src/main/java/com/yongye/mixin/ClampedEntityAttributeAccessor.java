package com.yongye.mixin;

import net.minecraft.entity.attribute.ClampedEntityAttribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 解除原版属性的硬上限。
 * 原版 generic.max_health / attack_damage / armor 等都是 ClampedEntityAttribute,
 * 上限被硬编码在 1024(再多也只按 1024 生效)——这会让本模组的高血量/无限强化失效。
 * 这里用 accessor 把私有的 maxValue 字段暴露为可写,模组初始化时(Yongye.raiseAttributeCaps)按需抬高。
 *
 * 待编译验证:字段名 "maxValue" 是 1.21.1 Yarn 的约定名。accessor 的字段名由 IDEA 的
 *           fabric mixin 注解处理器在【编译期】校验——若名字不符会直接编译失败并报
 *           "Unable to locate field ... maxValue",拿真实映射名替换即可(不会等到运行时崩)。
 */
@Mixin(ClampedEntityAttribute.class)
public interface ClampedEntityAttributeAccessor {
    @Mutable
    @Accessor("maxValue")
    void yongye$setMaxValue(double maxValue);
}
