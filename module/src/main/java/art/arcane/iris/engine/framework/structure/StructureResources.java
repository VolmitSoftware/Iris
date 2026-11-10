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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.util.List;

/**
 * The resource kinds of {@code iris:jigsaw}, which keep the folders and files Iris packs have always used, and
 * lookups by pack key for code that names structures, pools and pieces by key. Every lookup goes through the pack's
 * {@link PackResources}, so it sees the pack's current snapshot: a hotload, or a tool that rewrote the files and asked
 * for a new snapshot, reads them again.
 * <p>
 * Lookups answer null for a blank key, a key the pack has no file for, and a file that fails to load, which was
 * reported when it was read.
 */
public final class StructureResources {
    /** Jigsaw structures, in {@code structures/}. */
    public static final ResourceKind<IrisStructure> STRUCTURES = ResourceKind.of("iris:jigsaw/structure", "structures", IrisStructure.class);
    /** Jigsaw pools a structure starts from and connectors lead to, in {@code jigsaw-pools/}. */
    public static final ResourceKind<IrisJigsawPool> POOLS = ResourceKind.of("iris:jigsaw/pool", "jigsaw-pools", IrisJigsawPool.class);
    /** Jigsaw pieces pools hold, each an object with connectors, in {@code jigsaw-pieces/}. */
    public static final ResourceKind<IrisJigsawPiece> PIECES = ResourceKind.of("iris:jigsaw/piece", "jigsaw-pieces", IrisJigsawPiece.class);

    private StructureResources() {
    }

    /** Every resource kind of the structure model. */
    public static List<ResourceKind<?>> kinds() {
        return List.of(STRUCTURES, POOLS, PIECES);
    }

    public static IrisStructure structure(PackResources pack, String key) {
        return find(pack, STRUCTURES, key);
    }

    public static IrisJigsawPool pool(PackResources pack, String key) {
        return find(pack, POOLS, key);
    }

    public static IrisJigsawPiece piece(PackResources pack, String key) {
        return find(pack, PIECES, key);
    }

    /** The key of every structure of the pack snapshot {@code pack}, in key order. */
    public static List<String> structureKeys(PackResources pack) {
        return keys(pack, STRUCTURES);
    }

    /** The key of every jigsaw piece of the pack snapshot {@code pack}, in key order. */
    public static List<String> pieceKeys(PackResources pack) {
        return keys(pack, PIECES);
    }

    private static <T> T find(PackResources pack, ResourceKind<T> kind, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            return pack.find(kind, pack.idOf(key)).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            return null;
        }
    }

    /** A key's id has the key as its path, so the path names the resource again wherever a key is expected. */
    private static List<String> keys(PackResources pack, ResourceKind<?> kind) {
        return pack.ids(kind).stream().map(ResourceId::path).toList();
    }
}
