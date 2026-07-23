package com.yongye.client;

import com.yongye.Yongye;
import com.yongye.entity.WarlockCloneEntity;
import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.util.Identifier;

/**
 * 术士分身渲染器(m262)——玩家模型 + 程序化暗紫剪影皮肤(warlock_clone.png)。
 * 结构与 GanDiRenderer 一致(该写法已实机编译验证),单皮肤宽臂,无变体切换。
 */
public class WarlockCloneRenderer extends BipedEntityRenderer<WarlockCloneEntity, PlayerEntityModel<WarlockCloneEntity>> {

    private static final Identifier TEX = Identifier.of(Yongye.MOD_ID, "textures/entity/warlock_clone.png");

    public WarlockCloneRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false), 0.5f);
    }

    @Override
    public Identifier getTexture(WarlockCloneEntity entity) {
        return TEX;
    }
}
