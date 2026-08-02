package com.yongye.client;

import net.minecraft.block.entity.SkullBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 干弟自定义皮肤缓存(m413,作者点名:输入正版 ID 自动拉皮肤)。
 * 走原版玩家头颅同一条链:SkullBlockEntity.fetchProfileByName(ID) 拉带材质的 GameProfile
 * → PlayerSkinProvider.fetchSkinTextures 出贴图与臂型——**全是 Mojang 官方缓存管线**,
 * 无自建 HTTP;三个方法名已核 yarn 1.21.1(method_52580/52863/52862)。
 * 异步期间返回 null(渲染器回退原贴图),拉不到(假 ID/离线)记 MISS 不再反复拉;
 * 回调线程写表统一调度回渲染线程,表本身再用 ConcurrentHashMap 双保险。
 */
public final class GanDiSkinCache {
    private GanDiSkinCache() {}

    /** 皮肤数据:贴图 + 是否细臂。 */
    public record SkinData(Identifier tex, boolean slim) {}

    private static final SkinData PENDING = new SkinData(null, false);
    private static final SkinData MISS = new SkinData(null, false);
    private static final Map<String, SkinData> CACHE = new ConcurrentHashMap<>();

    /** 取该 ID 的皮肤;null=还没拿到(拉取中/拉不到/空 ID),调用方回退默认。 */
    public static SkinData get(String id) {
        if (id == null || id.isBlank()) return null;
        String key = id.trim().toLowerCase(Locale.ROOT);
        SkinData d = CACHE.get(key);
        if (d != null) return (d == PENDING || d == MISS) ? null : d;
        CACHE.put(key, PENDING);
        SkullBlockEntity.fetchProfileByName(id.trim()).thenAccept(opt -> {
            if (opt.isEmpty()) { CACHE.put(key, MISS); return; }
            MinecraftClient.getInstance().getSkinProvider().fetchSkinTextures(opt.get())
                    .thenAccept(st -> MinecraftClient.getInstance().execute(() ->
                            CACHE.put(key, new SkinData(st.texture(),
                                    st.model() == SkinTextures.Model.SLIM))));
        });
        return null;
    }

    /** 换 ID 后如想立刻重拉(比如改了皮肤),清掉缓存条目。 */
    public static void invalidate(String id) {
        if (id != null && !id.isBlank()) CACHE.remove(id.trim().toLowerCase(Locale.ROOT));
    }
}
