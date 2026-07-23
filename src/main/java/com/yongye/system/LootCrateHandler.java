package com.yongye.system;

import com.yongye.YongyeConfig;
import com.yongye.entity.AnubisEntity;
import com.yongye.entity.DeathMageEntity;
import com.yongye.entity.FirePhoenixEntity;
import com.yongye.entity.GiantCrabEntity;
import com.yongye.entity.RedSpiderEntity;
import com.yongye.entity.ToroEnderDragonEntity;
import com.yongye.entity.VenomSpiderEntity;
import com.yongye.registry.ModAttachments;
import com.yongye.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 战利品宝箱掉落(m245)——BOSS 级怪物被玩家击杀时掉落对应品质的宝箱物品:
 * 传说 = 末地龙(终局)/ 佩恩;史诗 = 凤凰 / 阿努比斯 / 红蜘蛛 / 死亡法师;
 * 稀有 = 巨蟹 / 毒蛛;普通 = 二次 BOSS 化的怪(MobBossHandler 红名「【BOSS】」前缀
 * 或皮肤 BOSS「xxx BOSS」后缀——它没有专用附件标记,以名字识别,来源单一不会误伤)。
 * 必须由玩家(或玩家的召唤物间接)击杀:source.getAttacker() 链上找得到玩家才掉,
 * 防止 BOSS 摔死/烧死白捡。传说 BOSS 额外附赠一只史诗箱。总开关 lootCrateEnabled。
 */
public final class LootCrateHandler {
    private LootCrateHandler() {}

    public static void init() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            YongyeConfig cfg = YongyeConfig.get();
            if (!cfg.lootCrateEnabled) return;
            if (!(entity.getWorld() instanceof ServerWorld world)) return;
            if (!(entity instanceof MobEntity mob)) return;
            if (!killedByPlayerSide(source.getAttacker())) return;

            int tier = tierOf(mob);
            if (tier < 0) return;

            dropCrate(world, mob, tier);
            if (tier >= 3) dropCrate(world, mob, 2); // 传说 BOSS 附赠史诗箱

            world.getServer().getPlayerManager().broadcast(
                    Text.literal("【战利品】").formatted(Formatting.GOLD)
                            .append(mob.getDisplayName())
                            .append(Text.literal(" 掉落了" + tierName(tier) + "战利品宝箱!")
                                    .formatted(Formatting.YELLOW)), false);
        });
    }

    /** 玩家本人,或玩家侧召唤物(铁傀儡/肝帝/宠物,owner 是玩家)击杀都算。 */
    private static boolean killedByPlayerSide(net.minecraft.entity.Entity attacker) {
        if (attacker instanceof PlayerEntity) return true;
        if (attacker instanceof net.minecraft.entity.mob.MobEntity m
                && m instanceof net.minecraft.entity.Tameable t
                && t.getOwner() instanceof PlayerEntity) return true;
        // 铁傀儡等非 Tameable 召唤物:有攻击目标记录且世界里能找到玩家即可放行——
        // 从简:傀儡击杀按环境击杀处理会漏掉召唤流的战果,这里放宽为「攻击者是友方傀儡也算」。
        if (attacker instanceof net.minecraft.entity.passive.IronGolemEntity) return true;
        if (attacker instanceof com.yongye.entity.GanDiEntity) return true;
        return false;
    }

    /** 怪 → 宝箱品质;-1 = 不是 BOSS 级不掉。 */
    private static int tierOf(MobEntity mob) {
        if (mob instanceof ToroEnderDragonEntity) return 3;
        if (mob.getAttachedOrElse(ModAttachments.IS_PAIN, false)) return 3;
        if (mob instanceof FirePhoenixEntity || mob instanceof AnubisEntity
                || mob instanceof RedSpiderEntity || mob instanceof DeathMageEntity) return 2;
        if (mob instanceof GiantCrabEntity || mob instanceof VenomSpiderEntity) return 1;
        // 二次 BOSS 化:MobBossHandler 的红名标记
        Text name = mob.getCustomName();
        if (name != null) {
            String s = name.getString();
            if (s.startsWith("【BOSS】") || s.endsWith(" BOSS")) return 0;
        }
        return -1;
    }

    private static void dropCrate(ServerWorld world, LivingEntity mob, int tier) {
        ItemStack crate = new ItemStack(ModItems.lootCrate(tier));
        ItemEntity e = new ItemEntity(world, mob.getX(), mob.getBodyY(0.5), mob.getZ(), crate);
        e.setVelocity(0, 0.25, 0);
        world.spawnEntity(e);
    }

    private static String tierName(int tier) {
        return switch (tier) { case 0 -> "普通"; case 1 -> "稀有"; case 2 -> "史诗"; default -> "传说"; };
    }
}
