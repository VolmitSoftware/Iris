package com.volmit.iris.util;

import com.volmit.iris.platform.PlatformNamespaceKey;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NSK implements PlatformNamespaceKey {
    private final String namespace;
    private final String key;

    public NSK(String namespacedkey)
    {
        this(namespacedkey.contains(":") ? namespacedkey.split("\\Q:\\E")[0] : "minecraft",
            namespacedkey.contains(":") ? namespacedkey.split("\\Q:\\E")[1] : namespacedkey);
    }

    @Override
    public String getNamespace() {
        return namespace;
    }

    @Override
    public String getKey() {
        return key;
    }

    public String toString() {
        return namespace + ":" + key;
    }
}
