package com.yongye.client;

import com.yongye.YongyeConfig;
import com.yongye.network.ChargeSlashPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.HitResult;

/**
 * 蓄力重斩·客户端(m257)——学 Epic Fight 的「按住派生」:
 * 手持可出刀光的武器、准星不指着方块时,按住攻击键开始蓄力;
 * 每 4t 咔哒声调渐升 + action bar 蓄力条,蓄满「叮」一声提示;
 * 松开且蓄够 minTicks → 发 ChargeSlashPayload 上报 + 本地放一道加大刀光,服务端结算伤害与大演出。
 * 蓄力中准星压到方块 / 换手武器不合格 → 静默归零(不误触挖掘)。
 */
public final class ChargeSlashManager {
    private ChargeSlashManager() {}

    private static int held = 0;        // 已按住的 tick 数(0=未在蓄力)
    private static boolean readyDinged = false;

    public static void tick(MinecraftClient mc) {
        YongyeConfig cfg = YongyeConfig.get();
        if (!cfg.enableChargeSlash || mc.player == null || mc.currentScreen != null) { reset(); return; }

        boolean down = mc.options.attackKey.isPressed();
        boolean blockAim = mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK;
        boolean weaponOk = SlashFxManager.weaponEligible(mc.player);

        if (down && !blockAim && weaponOk && !mc.player.isUsingItem()) {
            held++;
            int min = Math.max(1, cfg.chargeSlashMinTicks);
            int max = Math.max(min + 1, cfg.chargeSlashMaxTicks);
            if (held >= min) {
                if (held % 4 == 0 && held < max) {
                    float pitch = 0.9f + 0.9f * Math.min(1f, (held - min) / (float) (max - min));
                    mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, pitch);
                }
                if (held >= max && !readyDinged) {
                    readyDinged = true;
                    mc.player.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                }
                int bars = Math.min(10, (int) Math.ceil(10.0 * Math.min(1.0, (held - min + 1) / (double) (max - min + 1))));
                mc.player.sendMessage(Text.literal("蓄力 " + "▮".repeat(bars) + "▯".repeat(10 - bars))
                        .formatted(held >= max ? Formatting.GOLD : Formatting.YELLOW), true);
            }
            return;
        }

        // 松开(或条件失效):蓄够才放,否则静默归零
        if (held >= Math.max(1, cfg.chargeSlashMinTicks) && !down && weaponOk && !blockAim) {
            ClientPlayNetworking.send(new ChargeSlashPayload(held));
            SlashFxManager.spawnHeavy(mc.player); // 本地即刻一道加大刀光,不等回包
        }
        reset();
    }

    private static void reset() { held = 0; readyDinged = false; }
}
