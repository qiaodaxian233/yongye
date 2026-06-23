package com.yongye.item;

import com.yongye.Yongye;
import com.yongye.YongyeConfig;
import com.yongye.system.ClassManager;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

import java.util.List;

/**
 * 职业专属武器(m42)。强力差异化基础属性;手持且对应职业生效时,
 * 由 ClassSkillHandler 强化该职业的签名技能(协同)。武僧那把是「拳套」,
 * 手持时仍按空手计、可触发连击。普通玩家也能拿来当高属性武器用,但吃不到专属协同。
 */
public class ClassWeaponItem extends Item {

    public final PlayerClass playerClass;

    public ClassWeaponItem(PlayerClass playerClass, Settings settings) {
        super(settings);
        this.playerClass = playerClass;
    }

    /** 像剑一样快速破坏蜘蛛网(自定义武器默认是 Item,没有这个加成,会破不动)。1.21.x 重命名为 getMiningSpeed(已核实 yarn 1.21.1)。 */
    @Override
    public float getMiningSpeed(ItemStack stack, BlockState state) {
        if (state.isOf(Blocks.COBWEB)) return 15.0F;
        return super.getMiningSpeed(stack, state);
    }

    /** 主手持有该职业专属武器? */
    public static boolean held(PlayerEntity p, PlayerClass c) {
        return p.getMainHandStack().getItem() instanceof ClassWeaponItem w && w.playerClass == c;
    }

    private static Identifier id(String s) {
        return Identifier.of(Yongye.MOD_ID, s);
    }

    private static EntityAttributeModifier baseAtk(double v) {
        return new EntityAttributeModifier(Item.BASE_ATTACK_DAMAGE_MODIFIER_ID, v, Operation.ADD_VALUE);
    }
    private static EntityAttributeModifier baseAspd(double v) {
        return new EntityAttributeModifier(Item.BASE_ATTACK_SPEED_MODIFIER_ID, v, Operation.ADD_VALUE);
    }

    public static AttributeModifiersComponent baseAttributes(PlayerClass c) {
        AttributeModifiersComponent.Builder b = AttributeModifiersComponent.builder();
        AttributeModifierSlot M = AttributeModifierSlot.MAINHAND;
        switch (c) {
            case WARRIOR -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(14.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-2.8), M);
                b.add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(id("weapon_warrior_hp"), 8.0, Operation.ADD_VALUE), M);
            }
            case TANK -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(9.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-3.0), M);
                b.add(EntityAttributes.GENERIC_ARMOR,
                        new EntityAttributeModifier(id("weapon_tank_armor"), 6.0, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                        new EntityAttributeModifier(id("weapon_tank_kbr"), 0.4, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(id("weapon_tank_hp"), 14.0, Operation.ADD_VALUE), M);
            }
            case ASSASSIN -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(8.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(0.5), M);
                b.add(EntityAttributes.GENERIC_MOVEMENT_SPEED,
                        new EntityAttributeModifier(id("weapon_assassin_speed"), 0.05, Operation.ADD_MULTIPLIED_BASE), M);
            }
            case WARLOCK -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(11.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-2.0), M);
                b.add(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
                        new EntityAttributeModifier(id("weapon_warlock_reach"), 3.0, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_LUCK,
                        new EntityAttributeModifier(id("weapon_warlock_luck"), 3.0, Operation.ADD_VALUE), M);
                b.add(EntityAttributes.GENERIC_MAX_HEALTH,
                        new EntityAttributeModifier(id("weapon_warlock_hp"), -4.0, Operation.ADD_VALUE), M);
            }
            case MONK -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(7.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(1.2), M);
                b.add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                        new EntityAttributeModifier(id("weapon_monk_kbr"), 0.3, Operation.ADD_VALUE), M);
            }
            case SWORDSMAN -> {
                b.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, baseAtk(12.0), M);
                b.add(EntityAttributes.GENERIC_ATTACK_SPEED, baseAspd(-1.0), M);
                b.add(EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
                        new EntityAttributeModifier(id("weapon_swordsman_reach"), 1.0, Operation.ADD_VALUE), M);
            }
        }
        return b.build();
    }

    // =====================================================================
    // 术士专属:右键噬魂杖 → 蓄力吟唱 → 松手释放魔法弹(以命为薪)
    // getMaxUseTime / getUseAction / usageTick / onStoppedUsing
    // 【待编译验证】:四个方法签名在 1.21.1 yarn 下的确切形式
    // =====================================================================

    /** 非术士武器不触发蓄力;右键其他职业武器走默认 PASS */
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (playerClass != PlayerClass.WARLOCK) return TypedActionResult.pass(user.getStackInHand(hand));
        // use() 客户端+服务端均跑;服务端才能检查职业(ServerPlayerEntity),客户端直接放行触发蓄力动画
        if (!world.isClient && !(user instanceof ServerPlayerEntity sp && ClassManager.isActive(sp, PlayerClass.WARLOCK))) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }
        // setCurrentHand 触发原版使用动作(举杖+进入蓄力)
        user.setCurrentHand(hand);  // 【待编译验证】1.21.1 签名
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    /** 最长蓄力时间(tick);原版按此计算蓄力进度 0→1 */
    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {  // 【待编译验证】是否需要 LivingEntity 参
        return YongyeConfig.get().warlockBoltChargeTicks * 3; // 给足余量,实际松手即触发
    }

    /** 使用动作外观:BOW(举臂拉弓姿势,最贴合蓄力吟唱) */
    @Override
    public UseAction getUseAction(ItemStack stack) {  // 【待编译验证】
        return UseAction.BOW;
    }

    /** 蓄力 tick:播放吟唱粒子+音效(每8tick一次) */
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {  // 【待编译验证】
        if (world.isClient || !(user instanceof ServerPlayerEntity p)) return;
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        if (used > 0 && used % 8 == 0) {
            // 身周灵魂火粒子(吟唱感)
            if (world instanceof ServerWorld sw) {
                Vec3d pos = p.getPos().add(0, 1.0, 0);
                double angle = (used * 0.4) % (Math.PI * 2);
                double ox = Math.sin(angle) * 0.8;
                double oz = Math.cos(angle) * 0.8;
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x + ox, pos.y, pos.z + oz, 2, 0.1, 0.1, 0.1, 0.02);
            }
            // 渐强吟唱音效(pitch 随蓄力提升)
            float progress = Math.min(1.0f, used / (float) YongyeConfig.get().warlockBoltChargeTicks);
            world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_AMBIENT,
                    SoundCategory.PLAYERS, 0.3f, 0.8f + progress * 0.5f);
        }
    }

    /**
     * 松手/中断:释放魔法弹。
     * 用手动逐点射线检测第一个目标(不用 RaycastContext,避免版本敏感点)。
     * 蓄力越满伤害/耗血越高(0.4×~1.0×)。
     */
    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {  // 【待编译验证】1.21.2+可能返回boolean
        if (world.isClient || !(user instanceof ServerPlayerEntity p)) return;
        YongyeConfig cfg = YongyeConfig.get();
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        if (used < 5) return; // 蓄力不足0.25s:无效

        float charge = Math.min(1.0f, used / (float) cfg.warlockBoltChargeTicks);
        // 蓄力倍率:minMult → maxMult 随蓄力线性提升(满蓄力=攻击力×maxMult,默认4倍)
        double mult = cfg.warlockBoltMinMult + charge * (cfg.warlockBoltMaxMult - cfg.warlockBoltMinMult);
        double atk = p.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        // 伤害 = 攻击力 × 倍率(保底用配置基础值,防裸装攻击力过低)
        float damage = (float) (Math.max(atk, cfg.warlockBoltDamage) * mult);
        float hpCost = (float) (cfg.warlockBoltHpCost * (0.4 + charge * 0.6)); // 耗血仍按 0.4→1.0
        boolean hasWeapon = ClassWeaponItem.held(p, PlayerClass.WARLOCK);
        if (hasWeapon) { damage *= 1.2f; hpCost *= 0.8f; } // 专属武器加成

        // 扣血(至少留1点)
        if (p.getHealth() <= hpCost + 1.0f) {
            p.sendMessage(Text.literal("生命不足,无法施法!").formatted(Formatting.RED), true);
            return;
        }
        p.setHealth(Math.max(1.0f, p.getHealth() - hpCost));

        // 手动逐点射线(步长0.5格,检测前方范围内第一个目标)
        Vec3d eye = p.getEyePos();
        Vec3d dir = p.getRotationVector().normalize();
        double range = cfg.warlockBoltRange;
        LivingEntity hit = null;
        ServerWorld sw = (ServerWorld) world;
        for (double d = 0.5; d <= range; d += 0.5) {
            Vec3d pt = eye.add(dir.x * d, dir.y * d, dir.z * d);
            // 粒子光束
            if ((int)(d * 2) % 3 == 0) {
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.0);
            }
            if (hit != null) continue;
            Box box = new Box(pt.x - 0.5, pt.y - 0.5, pt.z - 0.5, pt.x + 0.5, pt.y + 0.5, pt.z + 0.5);
            List<LivingEntity> near = sw.getEntitiesByClass(LivingEntity.class, box,
                    e -> e.isAlive() && e != p && !(e instanceof PlayerEntity));
            if (!near.isEmpty()) hit = near.get(0);
        }

        if (hit != null) {
            DamageSource magic = world.getDamageSources().magic();
            hit.damage(magic, damage);
            hit.timeUntilRegen = 0;
            // 命中爆点粒子
            Vec3d hpos = hit.getPos().add(0, 1.0, 0);
            sw.spawnParticles(ParticleTypes.SOUL, hpos.x, hpos.y, hpos.z, 12, 0.4, 0.4, 0.4, 0.05);
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, hpos.x, hpos.y, hpos.z, 8, 0.3, 0.3, 0.3, 0.02);
            world.playSound(null, hit.getBlockPos(), SoundEvents.ENTITY_BLAZE_HURT,
                    SoundCategory.PLAYERS, 1.0f, 0.7f + charge * 0.5f);
            p.sendMessage(Text.literal(String.format("魔法弹命中!%.1f伤害(×%.1f) / 耗%.1f血", damage, mult, hpCost))
                    .formatted(Formatting.LIGHT_PURPLE), true);
        } else {
            // 未命中音效
            world.playSound(null, p.getBlockPos(), SoundEvents.ENTITY_ENDER_PEARL_THROW,
                    SoundCategory.PLAYERS, 0.6f, 1.2f);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("职业专属 · 【" + playerClass.cn + "】").formatted(Formatting.GOLD));
        tooltip.add(flavor(playerClass).formatted(Formatting.GRAY));
        if (playerClass == PlayerClass.WARLOCK) {
            tooltip.add(Text.literal("✦ 右键蓄力吟唱,松手释放魔法弹").formatted(Formatting.LIGHT_PURPLE));
            tooltip.add(Text.literal("  蓄力越满伤害越高 · 消耗生命施法").formatted(Formatting.DARK_PURPLE));
        }
        tooltip.add(Text.literal("✦ 手持且本职业生效时强化:").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(synergy(playerClass).formatted(Formatting.WHITE));
    }

    private static MutableText flavor(PlayerClass c) {
        return Text.literal(switch (c) {
            case WARRIOR -> "巨阙 —— 重劈裂阵,愈战愈勇。";
            case TANK -> "镇魂 —— 以血肉筑墙,挡在众人之前。";
            case ASSASSIN -> "影刺 —— 出于暗,归于暗。";
            case WARLOCK -> "噬魂杖 —— 以命为薪,焚尽周遭。";
            case MONK -> "鬼神拳套 —— 拳意不绝,连环不止。";
            case SWORDSMAN -> "流光 —— 剑气纵横,格挡即反击。";
        });
    }

    private static MutableText synergy(PlayerClass c) {
        return Text.literal(switch (c) {
            case WARRIOR -> "  斩杀阈值提升、吸血翻倍";
            case TANK -> "  护盾+1级、嘲讽范围扩大";
            case ASSASSIN -> "  背刺伤害翻倍、闪避几率提升";
            case WARLOCK -> "  范围更大、伤害更高、耗血更少";
            case MONK -> "  视为空手可连击、连击更狠";
            case SWORDSMAN -> "  剑气更广更痛、格挡反击增强";
        });
    }
}
