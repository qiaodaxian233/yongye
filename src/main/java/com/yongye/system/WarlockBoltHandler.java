package com.yongye.system;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 术士魔法弹·飞行法术球(m450,作者:「远程攻击为什么没有特效,右键蓄力完成后远程法术球是不是应该有啊」)。
 *
 * <p><b>改了什么</b>:旧实现(m139/m261)松手瞬间做一次逐点射线、当帧结算,拖尾只有每 ~1.5 格 1 粒的
 * 稀疏火星——观感上「什么都没发生」。现在魔法弹是<b>一颗真的会飞的球</b>:服务端 tick 推进
 * (默认 1.5 格/tick=30 格/秒),球体=灵魂火核心+龙息晕圈+PORTAL 尾迹,**蓄力倍率越高球越大越浓**;
 * 命中活物或撞墙时爆开(爆点规模同样随倍率),满蓄还有 END_ROD 星芒。伤害公式/耗血/专属加成
 * 与旧版逐字一致,只是把「当帧结算」改成「球到才结算」——这正是作者要的「法术球」语义。
 *
 * <p><b>全服务端</b>:spawnParticles 广播给范围内所有客户端(联机同屏可见),零客户端代码零协议;
 * 命中判定复用旧版口径(0.5 步长逐点、1×1×1 盒、排除玩家=不误伤 PvP);撞方块用 isAir 判
 * (在树 EliteCombatAI/NightfallHordeHandler 同款)。弹道列表每 tick 推进,出界/命中即移除,
 * 服务器关闭自然清空;单人同时最多 4 颗(防蓄力宏刷屏)。
 *
 * <p>速度可调 warlockBoltSpeed(格/tick,默 1.5;拉到 8≈旧版瞬发手感)。飞行中每 tick 都重查命中,
 * 所以「打提前量」成为可能——这是玩法上唯一的变化,也是「球」的应有之义。
 */
public final class WarlockBoltHandler {
    private WarlockBoltHandler() {}

    private static final class Bolt {
        final ServerWorld world;
        final ServerPlayerEntity owner;
        final Vec3d dir;
        final float damage;
        final double mult, range;
        Vec3d pos;
        double traveled = 0;
        Bolt(ServerWorld w, ServerPlayerEntity o, Vec3d start, Vec3d dir, double range, float damage, double mult) {
            this.world = w; this.owner = o; this.pos = start; this.dir = dir;
            this.range = range; this.damage = damage; this.mult = mult;
        }
    }

    private static final List<Bolt> ACTIVE = new ArrayList<>();

    /** ClassWeaponItem.onStoppedUsing 调:发射一颗法术球(伤害/倍率已在物品侧算好)。 */
    public static void fire(ServerWorld world, ServerPlayerEntity owner, Vec3d eye, Vec3d dir,
                            double range, float damage, double mult) {
        long mine = ACTIVE.stream().filter(b -> b.owner == owner).count();
        if (mine >= 4) return;                                  // 防连发刷屏(旧版蓄力节奏本就到不了)
        ACTIVE.add(new Bolt(world, owner, eye.add(dir.multiply(0.8)), dir, range, damage, mult));
        world.playSound(null, owner.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL,
                SoundCategory.PLAYERS, 0.9f, 1.05f);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ACTIVE.isEmpty()) return;
            YongyeConfig cfg = YongyeConfig.get();
            double speed = Math.max(0.3, Math.min(8.0, cfg.warlockBoltSpeed));
            Iterator<Bolt> it = ACTIVE.iterator();
            while (it.hasNext()) {
                Bolt b = it.next();
                if (b.owner.isRemoved() || b.owner.getServerWorld() != b.world) { it.remove(); continue; }
                boolean done = false;
                // 子步推进(0.5 格),命中判定与旧版逐字同口径
                for (double s = 0; s < speed && !done; s += 0.5) {
                    b.pos = b.pos.add(b.dir.multiply(0.5));
                    b.traveled += 0.5;
                    if (b.traveled > b.range) { fizzle(b); done = true; break; }
                    BlockPos bp = BlockPos.ofFloored(b.pos);
                    if (!b.world.getBlockState(bp).isAir()) { burst(b, null); done = true; break; }
                    Box box = new Box(b.pos.x - 0.5, b.pos.y - 0.5, b.pos.z - 0.5,
                            b.pos.x + 0.5, b.pos.y + 0.5, b.pos.z + 0.5);
                    List<LivingEntity> near = b.world.getEntitiesByClass(LivingEntity.class, box,
                            e -> e.isAlive() && e != b.owner && !(e instanceof PlayerEntity));
                    if (!near.isEmpty()) { burst(b, near.get(0)); done = true; break; }
                }
                if (done) { it.remove(); continue; }
                trail(b);
            }
        });
        Yongye.LOGGER.info("[夜蚀] 术士魔法弹弹道已挂载(飞行法术球)");
    }

    /** 飞行中的球体:核心+晕圈+尾迹,规模随蓄力倍率(×1≈拳头,×10≈脸盆)。 */
    private static void trail(Bolt b) {
        YongyeConfig cfg = YongyeConfig.get();
        double k = Math.min(1.0, b.mult / Math.max(1.0, cfg.warlockBoltMultCap));   // 0..1 蓄力度
        double r = 0.18 + 0.35 * k;                                                 // 球半径
        int core = 3 + (int) (5 * k), halo = 2 + (int) (4 * k);
        b.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, b.pos.x, b.pos.y, b.pos.z, core, r * 0.5, r * 0.5, r * 0.5, 0.005);
        b.world.spawnParticles(ParticleTypes.DRAGON_BREATH,   b.pos.x, b.pos.y, b.pos.z, halo, r, r, r, 0.01);
        b.world.spawnParticles(ParticleTypes.PORTAL,          b.pos.x, b.pos.y, b.pos.z, 2, 0.05, 0.05, 0.05, 0.4); // 尾迹拉丝
        if (k >= 0.999) {                                                            // 满蓄:星芒
            b.world.spawnParticles(ParticleTypes.END_ROD, b.pos.x, b.pos.y, b.pos.z, 1, r, r, r, 0.02);
        }
    }

    /** 命中/撞墙爆开:结算伤害(与旧版公式同源,值已算好)+ 爆点随倍率放大。 */
    private static void burst(Bolt b, LivingEntity hit) {
        YongyeConfig cfg = YongyeConfig.get();
        double k = Math.min(1.0, b.mult / Math.max(1.0, cfg.warlockBoltMultCap));
        Vec3d pos = hit != null ? hit.getPos().add(0, 1.0, 0) : b.pos;
        int n1 = 10 + (int) (18 * k), n2 = 6 + (int) (12 * k);
        b.world.spawnParticles(ParticleTypes.SOUL,            pos.x, pos.y, pos.z, n1, 0.4 + 0.3 * k, 0.4 + 0.3 * k, 0.4 + 0.3 * k, 0.05);
        b.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y, pos.z, n2, 0.3, 0.3, 0.3, 0.03);
        b.world.spawnParticles(ParticleTypes.WITCH,           pos.x, pos.y, pos.z, 4 + (int) (8 * k), 0.35, 0.35, 0.35, 0.02);
        if (hit != null) {
            DamageSource magic = b.world.getDamageSources().magic();
            hit.damage(magic, b.damage);
            hit.timeUntilRegen = 0;
            b.world.playSound(null, hit.getBlockPos(), SoundEvents.ENTITY_BLAZE_HURT,
                    SoundCategory.PLAYERS, 1.0f, 0.7f + (float) k * 0.5f);
            b.owner.sendMessage(Text.literal(String.format("魔法弹命中!%.1f伤害(×%.1f)", b.damage, b.mult))
                    .formatted(Formatting.LIGHT_PURPLE), true);
        } else {
            b.world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_BLAZE_SHOOT,
                    SoundCategory.PLAYERS, 0.7f, 0.8f);
        }
    }

    /** 飞满射程未中:余烬消散。 */
    private static void fizzle(Bolt b) {
        b.world.spawnParticles(ParticleTypes.SOUL, b.pos.x, b.pos.y, b.pos.z, 6, 0.3, 0.3, 0.3, 0.02);
        b.world.playSound(null, BlockPos.ofFloored(b.pos), SoundEvents.ENTITY_ENDER_PEARL_THROW,
                SoundCategory.PLAYERS, 0.5f, 1.2f);
    }
}
