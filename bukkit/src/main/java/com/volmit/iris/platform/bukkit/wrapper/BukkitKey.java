package com.volmit.iris.platform.bukkit.wrapper;

import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.Data;
import org.bukkit.NamespacedKey;

@Data
public class BukkitKey implements PlatformNamespaceKey {
    private final NamespacedKey delegate;

    private BukkitKey(NamespacedKey delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getNamespace() {
        return delegate.getNamespace();
    }

    @Override
    public String getKey() {
        return delegate.getKey();
    }

    public String toString() {
        return delegate.toString();
    }

    public static BukkitKey of(String nsk) {
        if(nsk.contains(":")) {
            String[] f = nsk.split("\\Q:\\E");
            return of(f[0], f[1]);
        }

        return of("minecraft", nsk);
    }

    public static BukkitKey of(String namespace, String key) {
        return of(new NamespacedKey(namespace, key));
    }

    public static BukkitKey of(NamespacedKey key) {
        return new BukkitKey(key);
    }
}
