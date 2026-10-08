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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.modules.IrisPackResources;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.util.List;

/**
 * The resource kinds of module {@code iris:structure}, which keep the folders and files Iris packs have always used,
 * and lookups by pack key for code that still names structures, pools and pieces by key. Every lookup goes through
 * the pack's {@link com.volmit.resource.PackResources}, so it sees the pack's current snapshot: a hotload, or
 * {@link IrisData#invalidateStructureResources()} after a tool rewrote the files, reads them again.
 * <p>
 * Lookups answer null where Iris's loaders did: for a blank key, a key the pack has no file for, and a file that
 * fails to load, which was reported when it was read.
 */
public final class StructureResources {
    /** Jigsaw structures, in {@code structures/}. */
    public static final ResourceKind<IrisStructure> STRUCTURES = ResourceKind.of("iris:structure/structure", "structures", IrisStructure.class);
    /** Jigsaw pools a structure starts from and connectors lead to, in {@code jigsaw-pools/}. */
    public static final ResourceKind<IrisJigsawPool> POOLS = ResourceKind.of("iris:structure/pool", "jigsaw-pools", IrisJigsawPool.class);
    /** Jigsaw pieces pools hold, each an object with connectors, in {@code jigsaw-pieces/}. */
    public static final ResourceKind<IrisJigsawPiece> PIECES = ResourceKind.of("iris:structure/piece", "jigsaw-pieces", IrisJigsawPiece.class);

    private StructureResources() {
    }

    /** Every resource kind of the structure module. */
    public static List<ResourceKind<?>> kinds() {
        return List.of(STRUCTURES, POOLS, PIECES);
    }

    public static IrisStructure structure(IrisData data, String key) {
        return find(data, STRUCTURES, key);
    }

    public static IrisJigsawPool pool(IrisData data, String key) {
        return find(data, POOLS, key);
    }

    public static IrisJigsawPiece piece(IrisData data, String key) {
        return find(data, PIECES, key);
    }

    /** The key of every structure of the pack, in key order. */
    public static List<String> structureKeys(IrisData data) {
        return keys(data, STRUCTURES);
    }

    /** The key of every jigsaw piece of the pack, in key order. */
    public static List<String> pieceKeys(IrisData data) {
        return keys(data, PIECES);
    }

    private static <T> T find(IrisData data, ResourceKind<T> kind, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        IrisPackResources resources = data.getPackResources();
        try {
            return resources.find(kind.key(resources.idOf(key))).orElse(null);
        } catch (IllegalStateException failedToLoad) {
            return null;
        }
    }

    /** A key's id has the key as its path, so the path names the resource again wherever a key is expected. */
    private static List<String> keys(IrisData data, ResourceKind<?> kind) {
        return data.getPackResources().ids(kind.type()).stream().map(ResourceId::path).toList();
    }
}
