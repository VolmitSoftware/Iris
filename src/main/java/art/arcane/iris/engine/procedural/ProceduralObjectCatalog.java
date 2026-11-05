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

package art.arcane.iris.engine.procedural;

import art.arcane.iris.engine.procedural.part.ProceduralObject;
import art.arcane.iris.engine.procedural.part.ResolvedProceduralObject;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.ModuleLog;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.world.PackWorld;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The pack's procedural objects, each read from {@code procedural/<path>.json} and placed from any object placement
 * as {@code procedural/<path>}, or one variant as {@code procedural/<path>#<index>}. Variants are baked on first use.
 */
public final class ProceduralObjectCatalog {
    /** The definitions, in {@code procedural/}; a placement's key for one is its file path, like {@code procedural/trees/oak}. */
    public static final ResourceKind<ProceduralObject> DEFINITIONS =
            ResourceKind.of("iris:objects/procedural", "procedural", ProceduralObject.class);
    public static final String KEY_PREFIX = DEFINITIONS.folder() + "/";
    public static final char VARIANT_SEPARATOR = '#';

    private final PackResources resources;
    private final ModuleLog log;
    private final Map<String, Optional<ProceduralVariants>> variantsByPath = new ConcurrentHashMap<>();

    private ProceduralObjectCatalog(PackResources resources, ModuleLog log) {
        this.resources = resources;
        this.log = log;
    }

    /** The catalog {@code world} keeps, so each definition bakes once per world. */
    public static ProceduralObjectCatalog of(PackWorld world) {
        return world.state(ProceduralObjectCatalog.class, generating -> new ProceduralObjectCatalog(generating.resources(), generating.log()));
    }

    /** A catalog of its own, for work outside any world such as working out the pack's reach; it bakes anew. */
    public static ProceduralObjectCatalog detached(PackResources resources, ModuleLog log) {
        return new ProceduralObjectCatalog(resources, log);
    }

    /** Whether {@code key} names a procedural object rather than a pack object. */
    public static boolean isProceduralKey(String key) {
        return key != null && key.startsWith(KEY_PREFIX);
    }

    /** The variant {@code key} pins, or for a key naming the whole pool the one {@code rng} picks; empty when none. */
    public Optional<PlaceableObject> object(String key, RNG rng) {
        ObjectKey parsed = ObjectKey.parse(key);
        return variantsNamedBy(parsed).flatMap(variants -> parsed.isPinned() ? variants.variant(parsed.variantIndex()) : variants.variant(rng));
    }

    /**
     * The variant {@code key} pins, or for a key naming the whole pool its first variant, so a caller without a random
     * stream still gets the same object every time; empty when none.
     */
    public Optional<PlaceableObject> object(String key) {
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
    public List<PlaceableObject> placeable(String key) {
        ObjectKey parsed = ObjectKey.parse(key);
        return variantsNamedBy(parsed).map(variants -> parsed.isPinned()
                ? variants.variant(parsed.variantIndex()).stream().toList()
                : variants.variants()).orElse(List.of());
    }

    private Optional<ProceduralVariants> variantsNamedBy(ObjectKey key) {
        return variantsByPath.computeIfAbsent(key.path(), this::resolve);
    }

    private Optional<ProceduralVariants> resolve(String path) {
        ResourceId id = resources.idOf(path);
        Optional<ProceduralObject> definition;
        try {
            definition = resources.find(DEFINITIONS, id);
        } catch (IllegalStateException failedToLoad) {
            log.warnOnce("procedural-unreadable:" + path, "Couldn't read procedural object " + KEY_PREFIX + path + ": " + failedToLoad.getMessage());
            return Optional.empty();
        }
        if (definition.isEmpty()) {
            log.warnOnce("procedural-missing:" + path, "Couldn't find procedural object " + KEY_PREFIX + path);
            return Optional.empty();
        }
        return Optional.of(new ResolvedProceduralObject(path, definition.get(), resources));
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
