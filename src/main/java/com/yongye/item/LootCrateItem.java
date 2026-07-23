package com.yongye.item;

import com.yongye.YongyeConfig;
import com.yongye.registry.ModItems;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

/**
 * 战利品宝箱(m245)——BOSS 级怪物掉落,右键开启。
 * 模型/贴图来自暂存素材包(ItemsAdder 原版格式模型,已改写到本模组命名空间):
 * 普通=铁箱 / 稀有=金箱 / 史诗=钻石箱 / 传说=紫水晶箱,品质越高箱体越华丽(17→107 个元素)。
 * 开箱 = 服务端按品质加权摇 N 次战利品散落脚下 + 开箱音 + 图腾粒子;
 * 传说箱另有 lootCrateWeaponChance 概率直接开出一把随机职业武器(大奖)。
 * 掉落规模 lootCrateRollScale 可配;纯物品逻辑,无方块无 BlockEntity,存档零负担。
 */
public class LootCrateItem extends Item {

    /** 品质:0 普通 / 1 稀有 / 2 史诗 / 3 传说。 */
    public final int tier;

    private static final String[] TIER_NAMES = {"普通", "稀有", "史诗", "传说"};

    public LootCrateItem(int tier, Settings settings) {
        super(settings);
        this.tier = tier;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld sw)) {
            return TypedActionResult.success(stack, true);
        }
        stack.decrement(1);
        openCrate(sw, user);
        return TypedActionResult.success(stack, false);
    }

    /** 开箱:按品质摇战利品散落在玩家脚下。 */
    private void openCrate(ServerWorld world, PlayerEntity user) {
        YongyeConfig cfg = YongyeConfig.get();
        Random r = world.getRandom();

        // 摇奖次数:普通3 / 稀有5 / 史诗7 / 传说9,受 lootCrateRollScale 缩放(至少 1 次)
        int rolls = Math.max(1, (int) Math.round((3 + tier * 2) * Math.max(0.1, cfg.lootCrateRollScale)));
        for (int i = 0; i < rolls; i++) {
            scatter(world, user, rollLoot(r));
        }
        // 传说箱大奖:随机职业武器
        if (tier >= 3 && r.nextDouble() < cfg.lootCrateWeaponChance) {
            com.yongye.item.PlayerClass pc =
                    ModItems.WEAPON_CLASSES[r.nextInt(ModItems.WEAPON_CLASSES.length)];
            scatter(world, user, new ItemStack(ModItems.getClassWeapon(pc)));
            user.sendMessage(Text.literal("【战利品】传说宝箱开出了职业武器!").formatted(Formatting.LIGHT_PURPLE), false);
        }

        world.playSound(null, user.getBlockPos(), SoundEvents.BLOCK_CHEST_OPEN, SoundCategory.PLAYERS, 1.0f, 0.9f + tier * 0.05f);
        if (tier >= 2) {
            world.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.7f, 1.2f);
        }
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                user.getX(), user.getBodyY(0.6), user.getZ(),
                12 + tier * 10, 0.5, 0.5, 0.5, 0.25);
        user.sendMessage(Text.literal("开启了" + TIER_NAMES[tier] + "战利品宝箱 ×" + rolls + " 份战利品").formatted(tierColor()), true);
    }

    /** 单次摇奖:按品质加权。数值刻意保守,大头仍是打怪本身的掉落,宝箱是锦上添花。 */
    private ItemStack rollLoot(Random r) {
        int w = r.nextInt(100);
        return switch (tier) {
            case 0 -> // 普通:尘/生命碎片/裂隙/绿宝石/钻石
                    w < 40 ? new ItemStack(ModItems.ENDLESS_NIGHT_DUST, 2 + r.nextInt(4))
                  : w < 65 ? new ItemStack(ModItems.LIFE_SHARD, 1 + r.nextInt(3))
                  : w < 80 ? new ItemStack(ModItems.RIFT_FRAGMENT, 1 + r.nextInt(2))
                  : w < 90 ? new ItemStack(Items.EMERALD, 1 + r.nextInt(3))
                  : new ItemStack(Items.DIAMOND, 1 + r.nextInt(2));
            case 1 -> // 稀有:更多尘/生命水晶/裂隙/魂晶入池/钻石
                    w < 35 ? new ItemStack(ModItems.ENDLESS_NIGHT_DUST, 3 + r.nextInt(6))
                  : w < 55 ? new ItemStack(ModItems.LIFE_CRYSTAL, 1 + r.nextInt(2))
                  : w < 75 ? new ItemStack(ModItems.RIFT_FRAGMENT, 1 + r.nextInt(3))
                  : w < 85 ? new ItemStack(ModItems.ABYSS_SOUL_CRYSTAL, 1)
                  : new ItemStack(Items.DIAMOND, 2 + r.nextInt(3));
            case 2 -> // 史诗:生命核心/魂晶/保护卷轴/下界合金碎片
                    w < 30 ? new ItemStack(ModItems.ENDLESS_NIGHT_DUST, 5 + r.nextInt(8))
                  : w < 50 ? new ItemStack(ModItems.LIFE_CORE, 1)
                  : w < 70 ? new ItemStack(ModItems.ABYSS_SOUL_CRYSTAL, 1 + r.nextInt(2))
                  : w < 85 ? new ItemStack(ModItems.ENHANCE_PROTECT_SCROLL, 1)
                  : new ItemStack(Items.NETHERITE_SCRAP, 1 + r.nextInt(2));
            default -> // 传说:终焉精华/浩劫血核/魂晶大把/下界合金锭
                    w < 25 ? new ItemStack(ModItems.ENDING_ESSENCE, 1)
                  : w < 45 ? new ItemStack(ModItems.CATASTROPHE_BLOOD_CORE, 1)
                  : w < 70 ? new ItemStack(ModItems.ABYSS_SOUL_CRYSTAL, 2 + r.nextInt(3))
                  : w < 88 ? new ItemStack(ModItems.ENHANCE_PROTECT_SCROLL, 1 + r.nextInt(2))
                  : new ItemStack(Items.NETHERITE_INGOT, 1);
        };
    }

    private void scatter(ServerWorld world, PlayerEntity user, ItemStack loot) {
        ItemEntity e = new ItemEntity(world, user.getX(), user.getY() + 0.5, user.getZ(), loot);
        e.setVelocity((world.getRandom().nextDouble() - 0.5) * 0.25, 0.2, (world.getRandom().nextDouble() - 0.5) * 0.25);
        world.spawnEntity(e);
    }

    private Formatting tierColor() {
        return switch (tier) {
            case 0 -> Formatting.WHITE;
            case 1 -> Formatting.YELLOW;
            case 2 -> Formatting.AQUA;
            default -> Formatting.LIGHT_PURPLE;
        };
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("右键开启 · " + TIER_NAMES[tier] + "品质").formatted(Formatting.GRAY));
        if (tier >= 3) {
            tooltip.add(Text.literal("有概率开出职业武器!").formatted(Formatting.LIGHT_PURPLE));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }
}
