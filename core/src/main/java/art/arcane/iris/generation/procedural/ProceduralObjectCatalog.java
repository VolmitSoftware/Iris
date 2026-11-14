/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.generation.procedural;

import art.arcane.iris.generation.procedural.part.ProceduralObject;
import art.arcane.iris.generation.procedural.part.ResolvedProceduralObject;
import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.loading.ResourceLoader;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.math.RNG;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pack's procedural objects, each read from {@code procedural/<path>.json} and placed from any object placement
 * as {@code procedural/<path>}, or one variant as {@code procedural/<path>#<index>}. Variants are baked on first use.
 */
public final class ProceduralObjectCatalog {
    public static final String KEY_PREFIX = "procedural/";
    public static final char VARIANT_SEPARATOR = '#';

    private final IrisData data;
    private final Map<String, Optional<ProceduralVariants>> variantsByPath = new ConcurrentHashMap<>();

    public ProceduralObjectCatalog(IrisData data) {
        this.data = data;
    }

    /** Whether {@code key} names a procedural object rather than a pack object. */
    public static boolean isProceduralKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX);
    }

    /** The variant {@code key} pins, or for a key naming the whole pool the one {@code rng} picks; empty when none. */
    public Optional<IrisObject> object(String key, RNG rng) {
        ObjectKey parsed = ObjectKey.parse(key);
        return variantsNamedBy(parsed).flatMap(variants -> parsed.isPinned() ? variants.variant(parsed.variantIndex()) : variants.variant(rng));
    }

    /**
     * The variant {@code key} pins, or for a key naming the whole pool its first variant, so a caller without a random
     * stream still gets the same object every time; empty when none.
     */
    public Optional<IrisObject> object(String key) {
        ObjectKey parsed = ObjectKey.parse(key);
        return variantsNamedBy(parsed).flatMap(variants -> parsed.isPinned()
                ? variants.variant(parsed.variantIndex())
                : variants.variants().stream().findFirst());
    }

    /** The variants {@code key} names, pinned to one or not; empty when the pack has no such object. */
    public Optional<ProceduralVariants> variants(String key) {
        return variantsNamedBy(ObjectKey.parse(key));
    }

    /** Every variant {@code key} may place: the one it pins, or the whole pool. */
    public List<IrisObject> placeable(String key) {
        ObjectKey parsed = ObjectKey.parse(key);
        return variantsNamedBy(parsed).map(variants -> parsed.isPinned()
                ? variants.variant(parsed.variantIndex()).stream().toList()
                : variants.variants()).orElse(List.of());
    }

    /** The key of every definition the pack has, naming its whole variant pool. */
    public List<String> definitionKeys() {
        ResourceLoader<ProceduralObject> loader = data.getProceduralObjectLoader();
        if (loader == null) {
            return List.of();
        }
        return Arrays.stream(loader.getPossibleKeys()).map(path -> KEY_PREFIX + path).toList();
    }

    /** Forgets every baked variant, so the next lookup bakes from the definitions as they are now. */
    public void invalidate() {
        variantsByPath.clear();
    }

    private Optional<ProceduralVariants> variantsNamedBy(ObjectKey key) {
        return variantsByPath.computeIfAbsent(key.path(), this::resolve);
    }

    private Optional<ProceduralVariants> resolve(String path) {
        ResourceLoader<ProceduralObject> loader = data.getProceduralObjectLoader();
        if (loader == null) {
            return Optional.empty();
        }
        ProceduralObject definition = loader.load(path, false);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(new ResolvedProceduralObject(path, definition, data));
    }

    /** A procedural object key split into the definition's path and, when it pins one, a variant index. */
    private record ObjectKey(String path, int variantIndex) {
        private static final int UNPINNED = -1;

        static ObjectKey parse(String key) {
            String path = key.substring(KEY_PREFIX.length());
            int separator = path.lastIndexOf(VARIANT_SEPARATOR);
            if (separator < 0) {
                return new ObjectKey(path, UNPINNED);
            }
            try {
                return new ObjectKey(path.substring(0, separator), Integer.parseInt(path.substring(separator + 1)));
            } catch (NumberFormatException notAnIndex) {
                return new ObjectKey(path, UNPINNED);
            }
        }

        boolean isPinned() {
            return variantIndex != UNPINNED;
        }
    }
}
