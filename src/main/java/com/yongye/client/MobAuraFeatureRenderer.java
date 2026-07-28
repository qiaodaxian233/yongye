package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.entity.AnubisEntity;
import com.yongye.entity.DeathMageEntity;
import com.yongye.entity.FirePhoenixEntity;
import com.yongye.entity.GiantCrabEntity;
import com.yongye.entity.RedSpiderEntity;
import com.yongye.entity.ToroEnderDragonEntity;
import com.yongye.entity.VenomSpiderEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;

/**
 * m311(作者点名):全怪紫气分档——普通怪轻微 / 精英中等 / BOSS 高等(带盘升螺旋)。
 *
 * <p>接管并取代 m310 僵尸专属紫气,扩到所有敌对怪。优化三板斧(作者点名「要注意优化」):
 * ① 渲染驱动——只对进了视锥、真的被画出来的怪发射,看不见=零开销;
 * ② 距离裁剪——普通 16 格 / 精英 32 格 / BOSS 64 格外直接不冒;
 * ③ 概率限流——普通≈每秒 2 粒、精英≈6 粒、BOSS≈18 粒(双螺旋),百怪同屏也只是几百粒/秒,
 *   远低于一次爆炸的粒子量;粒子本体是纯客户端 WITCH 魔粒,零网络零服务端开销。
 *
 * <p>分档判定(与 EliteSkinFeatureRenderer 客户端口径一致):
 * BOSS = 五只皮肤 BOSS 实体类 或 名字含佩恩/长门/HIM/「 BOSS」;
 * 精英 = 名字含「精英」或 毒液蜘蛛/巨型螃蟹 实体类;普通 = 其余一切 Monster。
 * 总开关沿用 zombiePurpleAura(m311 起语义扩为全怪),密度倍率 mobAuraScale。
 */
@Environment(EnvType.CLIENT)
public class MobAuraFeatureRenderer<T extends Entity, M extends EntityModel<T>> extends FeatureRenderer<T, M> {

    public MobAuraFeatureRenderer(FeatureRendererContext<T, M> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
                       T entity, float limbAngle, float limbDistance, float tickDelta,
                       float animationProgress, float headYaw, float headPitch) {
        if (!(entity instanceof LivingEntity living) || living.isInvisible()) return;
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.zombiePurpleAura) return;

        int tier = tierOf(living);
        if (tier == 0) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        double d2 = mc.player.squaredDistanceTo(living);
        float scale = (float) MathHelper.clamp(cfg.mobAuraScale, 0.0, 4.0);
        var r = living.getRandom();

        if (tier == 1) {
            // 普通怪·轻微:16 格内,约每秒 2 粒缓浮
            if (d2 > 256 || r.nextFloat() >= 0.035f * scale) return;
            spawnAround(living, 0.0, 0.02);
        } else if (tier == 2) {
            // 精英·中等:32 格内,约每秒 6 粒,升速稍快
            if (d2 > 1024 || r.nextFloat() >= 0.10f * scale) return;
            spawnAround(living, 0.1, 0.04);
        } else {
            // BOSS·高等:64 格内,约每秒 18 粒 + 贴体双螺旋盘升(出场演出同款母题)
            if (d2 > 4096 || r.nextFloat() >= 0.30f * scale) return;
            spawnAround(living, 0.2, 0.06);
            float ang = (living.age + tickDelta) * 0.28f;
            double rad = living.getWidth() * 0.75;
            double sy = living.getY() + (living.age % 40) / 40.0 * living.getHeight();
            living.getWorld().addParticle(ParticleTypes.WITCH,
                    living.getX() + Math.cos(ang) * rad, sy, living.getZ() + Math.sin(ang) * rad, 0, 0.08, 0);
            living.getWorld().addParticle(ParticleTypes.WITCH,
                    living.getX() - Math.cos(ang) * rad, sy, living.getZ() - Math.sin(ang) * rad, 0, 0.08, 0);
        }
    }

    /** 身周随机一点发射一粒魔粒;extra = 额外再补一粒的概率(档位越高气越厚)。 */
    private static void spawnAround(LivingEntity living, double extra, double rise) {
        var r = living.getRandom();
        int n = 1 + (r.nextDouble() < extra ? 1 : 0);
        for (int i = 0; i < n; i++) {
            double px = living.getX() + (r.nextDouble() - 0.5) * living.getWidth() * 1.15;
            double py = living.getY() + r.nextDouble() * living.getHeight();
            double pz = living.getZ() + (r.nextDouble() - 0.5) * living.getWidth() * 1.15;
            living.getWorld().addParticle(ParticleTypes.WITCH, px, py, pz, 0, rise, 0);
        }
    }

    /** 0=不冒 1=普通 2=精英 3=BOSS。客户端口径,与精英叠皮/佩恩名判一致。 */
    private static int tierOf(LivingEntity e) {
        if (e instanceof AnubisEntity || e instanceof ToroEnderDragonEntity || e instanceof FirePhoenixEntity
                || e instanceof DeathMageEntity || e instanceof RedSpiderEntity) return 3;
        if (e.hasCustomName() && e.getCustomName() != null) {
            String n = e.getCustomName().getString();
            if (n.contains("佩恩") || n.contains("长门") || n.equals("HIM") || n.endsWith(" BOSS")) return 3;
            if (n.contains("精英")) return 2;
        }
        if (e instanceof VenomSpiderEntity || e instanceof GiantCrabEntity) return 2;
        if (e instanceof Monster) return 1;
        return 0;
    }
}
