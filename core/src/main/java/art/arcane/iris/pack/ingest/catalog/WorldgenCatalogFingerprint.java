/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.pack.ingest.catalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class WorldgenCatalogFingerprint {
    private WorldgenCatalogFingerprint() {
    }

    public static String of(List<CatalogNamespace> namespaces, Map<String, ? extends Collection<String>> registryKeys) {
        Objects.requireNonNull(namespaces, "namespaces");
        Objects.requireNonNull(registryKeys, "registryKeys");
        MessageDigest digest = sha256();
        List<String> versions = new ArrayList<>(namespaces.size());
        for (CatalogNamespace namespace : namespaces) {
            versions.add(namespace.namespace() + ":" + namespace.version());
        }
        update(digest, "namespaces", versions);
        for (Map.Entry<String, ? extends Collection<String>> registry : new TreeMap<>(registryKeys).entrySet()) {
            update(digest, "registry " + registry.getKey(), new ArrayList<>(registry.getValue()));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void update(MessageDigest digest, String section, List<String> values) {
        values.sort(null);
        digest.update((section + "\n").getBytes(StandardCharsets.UTF_8));
        for (String value : values) {
            digest.update((value + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is unavailable", missing);
        }
    }
}
