package com.volmit.resource;

import java.util.Objects;

/**
 * What an item or entity a module hands the host is: a registered type by its {@link ResourceId}, or a mob or item of a
 * content provider by the provider's own name for it. Provider names are kept as written because providers tell their
 * content apart by case ({@code mythicmobs:SkeletonKing}, {@code mmoitems_SWORD:EXCALIBUR}), which no
 * {@code ResourceId} can hold.
 */
public sealed interface ContentKey permits ContentKey.Registered, ContentKey.Provided {
    /**
     * A type in the platform's registries, such as {@code minecraft:diamond_sword}, or one the host routes by its
     * namespace to the provider that owns it, such as {@code itemsadder:ruby}; the host decides which.
     */
    record Registered(ResourceId id) implements ContentKey {
        public Registered {
            Objects.requireNonNull(id, "id");
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }

    /**
     * A mob or item of a content provider: the provider, such as {@code mythicmobs}, and its name for the content, which
     * may hold any character, {@code :} included.
     */
    record Provided(String provider, String name) implements ContentKey {
        public Provided {
            Objects.requireNonNull(provider, "provider");
            Objects.requireNonNull(name, "name");
            if (provider.isBlank() || name.isBlank()) {
                throw new IllegalArgumentException("A provided key needs a provider and a name: " + provider + ":" + name);
            }
        }

        @Override
        public String toString() {
            return provider + ":" + name;
        }
    }
}
