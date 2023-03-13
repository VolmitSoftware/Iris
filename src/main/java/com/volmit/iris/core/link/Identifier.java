package com.volmit.iris.core.link;

import org.bukkit.NamespacedKey;

public record Identifier(String namespace, String key) {

    private static final String DEFAULT_NAMESPACE = "minecraft";

    public static Identifier fromString(String id) {
        String[] strings = id.split(":", 1);
        if(strings.length == 1) {
            return new Identifier(DEFAULT_NAMESPACE, strings[0]);
        } else {
            return new Identifier(strings[0], strings[1]);
        }
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof Identifier i) {
            return i.namespace().equals(this.namespace) && i.key().equals(this.key);
        } else if(obj instanceof NamespacedKey i) {
            return i.getNamespace().equals(this.namespace) && i.getKey().equals(this.key);
        } else {
            return false;
        }
    }
}
