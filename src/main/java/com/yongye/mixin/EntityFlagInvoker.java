package com.yongye.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 Entity 的 protected getFlag/setFlag(用于 AccessoryGliderMixin 操作滑翔位 flag(7))。
 * getFlag/setFlag 定义在 Entity 类,故 accessor 的 @Mixin 必须指向 Entity,
 * 而非 LivingEntity——这是 m109 崩溃(method_5795 not located in class_1309)的根因。
 */
@Mixin(Entity.class)
public interface EntityFlagInvoker {
    @Invoker("getFlag")
    boolean yongye$getFlag(int index);

    @Invoker("setFlag")
    void yongye$setFlag(int index, boolean value);
}
