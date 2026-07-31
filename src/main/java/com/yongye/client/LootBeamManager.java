package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.item.ArtifactItem;
import com.yongye.item.ClassWeaponItem;
import com.yongye.item.EnhanceStoneItem;
import com.yongye.item.LootCrateItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * 稀有掉落光柱(m376,3A 打磨路线图第 4 项):地上躺着的稀有掉落物起一根品质色光柱
 * (蓝=稀有 / 紫=史诗 / 金=传说级),柱体顶端渐隐 + 呼吸脉动 + 缓慢自转 + 底部光晕,
 * 传说档更粗更亮远处一眼锁定。<b>纯客户端零流量零掉落逻辑改动</b>——不给任何掉落点插桩,
 * 每 10 客户端 tick 扫一遍附近 ItemEntity 按物品定级(掉落物本体客户端天然同步)。
 *
 * <p>定级口径(tierOf):职业武器/神器/传说宝箱=3 金;强化石 tier≥7(百万级)=3 金、
 * ≥4(千级)=2 紫;其余按原版 Rarity:EPIC=2 紫、RARE=1 蓝、更低不起柱
 * (本模组物品注册时普遍带 rarity,天然覆盖)。
 *
 * <p>渲染:AFTER_TRANSLUCENT + RenderLayer.getLightning(位置+颜色附加混合,
 * SlashFxManager m240 起在树已编),两组十字交叉竖面(内芯亮/外圈淡宽 2.6 倍)+
 * 底部菱形光晕,全部相机相对坐标、双面绕序;同屏 24 根上限、64 格外不扫。
 *
 * <p>待编译验证(低险,yarn 已核):ClientWorld.getEntities()=method_18112 返 Iterable、
 * ItemStack.getRarity()=method_7932——均仓库首用;报错只在 tick() 扫描两行。
 */
public final class LootBeamManager {
    private LootBeamManager() {}

    private static final int SCAN_INTERVAL = 10;      // 扫描间隔(客户端 tick)
    private static final double MAX_DIST_SQ = 64 * 64;
    private static final int MAX_BEAMS = 24;
    private static final float BEAM_H = 5.5f;
    /** 品质色:1=稀有蓝 2=史诗紫 3=传说金。 */
    private static final int[] TIER_RGB = {0x3B9CFF, 0xB44CFF, 0xFFC332};

    private record Beam(ItemEntity entity, int tier) {}

    private static final List<Beam> BEAMS = new ArrayList<>();
    private static int scanCd = 0;

    /** 客户端初始化时挂扫描 + 世界渲染(YongyeClient 调)。 */
    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(LootBeamManager::scan);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(LootBeamManager::render);
    }

    private static void scan(MinecraftClient mc) {
        if (--scanCd > 0) return;
        scanCd = SCAN_INTERVAL;
        BEAMS.clear();
        if (!YongyeConfig.get().enableLootBeam || !FxBudget.on()) return; // m381 预算闸
        if (mc.world == null || mc.player == null) return;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof ItemEntity item) || !item.isAlive()) continue;
            if (item.squaredDistanceTo(mc.player) > FxBudget.scaleDistSq(MAX_DIST_SQ)) continue; // m381 缩可见距
            int tier = tierOf(item.getStack());
            if (tier <= 0) continue;
            BEAMS.add(new Beam(item, tier));
            if (BEAMS.size() >= Math.max(4, FxBudget.scaleCount(MAX_BEAMS))) break; // m381 缩上限(保底 4)
        }
    }

    /** 品质定级:0=不起柱 1=蓝 2=紫 3=金。 */
    private static int tierOf(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        Item it = stack.getItem();
        if (it instanceof ClassWeaponItem || it instanceof ArtifactItem) return 3;
        if (it instanceof LootCrateItem) return 3;                       // 宝箱本就稀罕,给足牌面
        if (it instanceof EnhanceStoneItem es) return es.tier >= 7 ? 3 : es.tier >= 4 ? 2 : 0;
        Rarity r = stack.getRarity();
        if (r == Rarity.EPIC) return 2;
        if (r == Rarity.RARE) return 1;
        return 0;
    }

    private static void render(WorldRenderContext ctx) {
        if (BEAMS.isEmpty()) return;
        VertexConsumerProvider consumers = ctx.consumers();
        if (consumers == null) return;
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
        Vec3d cam = ctx.camera().getPos();
        long nowMs = System.nanoTime() / 1_000_000L;
        float spinDeg = (nowMs % 18000L) / 18000f * 360f;                // 20 秒一圈缓慢自转

        for (Beam b : BEAMS) {
            ItemEntity e = b.entity;
            if (!e.isAlive()) continue;
            int rgb = TIER_RGB[b.tier - 1];
            int cr = (rgb >> 16) & 0xFF, cg = (rgb >> 8) & 0xFF, cb = rgb & 0xFF;
            // 呼吸脉动(按实体 id 错相,一片掉落不同步呼吸)
            float pulse = 0.75f + 0.25f * (float) Math.sin((nowMs / 1600.0 + e.getId() * 0.37) * Math.PI * 2);
            float cx = (float) (e.getX() - cam.x);
            float cy = (float) (e.getY() + 0.10 - cam.y);
            float cz = (float) (e.getZ() - cam.z);

            float half = (b.tier == 3 ? 0.20f : 0.16f);
            int aCore = (int) ((b.tier == 3 ? 185 : 150) * pulse);
            int aOut  = (int) (45 * pulse);

            // 两组十字交叉竖面(0°/90°,随时间自转);每组再画 2.6 倍宽的淡外圈
            for (int k = 0; k < 2; k++) {
                double ang = Math.toRadians(spinDeg + k * 90);
                float dx = (float) Math.cos(ang), dz = (float) Math.sin(ang);
                plane(vc, cx, cy, cz, dx, dz, half, BEAM_H, cr, cg, cb, aCore);
                if (!FxBudget.lowDetail()) plane(vc, cx, cy, cz, dx, dz, half * 2.6f, BEAM_H, cr, cg, cb, aOut); // m381 LOW 裁外圈
            }
            // 底部菱形光晕(水平,双面)
            float gr = b.tier == 3 ? 0.65f : 0.5f;
            int aGlow = (int) (70 * pulse);
            diamond(vc, cx, cy + 0.05f, cz, gr, cr, cg, cb, aGlow);
        }
    }

    /** 一面竖立矩形(底 alpha=a,顶 alpha=0 出顶端渐隐),双面绕序。 */
    private static void plane(VertexConsumer vc, float cx, float cy, float cz,
                              float dx, float dz, float half, float h,
                              int r, int g, int b, int a) {
        float x1 = cx - dx * half, z1 = cz - dz * half;
        float x2 = cx + dx * half, z2 = cz + dz * half;
        // 正面
        vc.vertex(x1, cy, z1).color(r, g, b, a);
        vc.vertex(x2, cy, z2).color(r, g, b, a);
        vc.vertex(x2, cy + h, z2).color(r, g, b, 0);
        vc.vertex(x1, cy + h, z1).color(r, g, b, 0);
        // 背面(反绕序)
        vc.vertex(x1, cy + h, z1).color(r, g, b, 0);
        vc.vertex(x2, cy + h, z2).color(r, g, b, 0);
        vc.vertex(x2, cy, z2).color(r, g, b, a);
        vc.vertex(x1, cy, z1).color(r, g, b, a);
    }

    /** 水平菱形光晕(中心亮外缘用同 alpha 的小面近似即可),双面绕序。 */
    private static void diamond(VertexConsumer vc, float cx, float cy, float cz,
                                float rad, int r, int g, int b, int a) {
        vc.vertex(cx, cy, cz - rad).color(r, g, b, a);
        vc.vertex(cx + rad, cy, cz).color(r, g, b, a);
        vc.vertex(cx, cy, cz + rad).color(r, g, b, a);
        vc.vertex(cx - rad, cy, cz).color(r, g, b, a);
        vc.vertex(cx - rad, cy, cz).color(r, g, b, a);
        vc.vertex(cx, cy, cz + rad).color(r, g, b, a);
        vc.vertex(cx + rad, cy, cz).color(r, g, b, a);
        vc.vertex(cx, cy, cz - rad).color(r, g, b, a);
    }
}
