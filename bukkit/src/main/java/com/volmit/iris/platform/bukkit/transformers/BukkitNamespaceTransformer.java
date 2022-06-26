package com.volmit.iris.platform.bukkit.transformers;

import com.volmit.iris.engine.object.NSKey;
import com.volmit.iris.platform.PlatformTransformer;
import org.bukkit.NamespacedKey;

public class BukkitNamespaceTransformer implements PlatformTransformer<NamespacedKey, NSKey> {
    @Override
    public NSKey toIris(NamespacedKey namespacedKey) {
        return new NSKey(namespacedKey.toString());
    }

    @Override
    public NamespacedKey toNative(NSKey nsKey) {
        return new NamespacedKey(nsKey.getNamespace(), nsKey.getKey());
    }
}
