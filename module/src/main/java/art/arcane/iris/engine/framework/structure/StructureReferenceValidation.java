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

package art.arcane.iris.engine.framework.structure;

import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawConnector;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPieceEntry;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPool;
import com.volmit.block.BlockRegistry;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;
import com.volmit.validation.Problem;
import com.volmit.validation.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * The references between the pack's structures, jigsaw pools, jigsaw pieces and objects, checked for every file,
 * placed or not, so a structure under construction reports a missing pool before anything places it. Assembling a
 * placed structure ({@link StructureGraphValidation}) finds the same problems and words them the same way.
 */
final class StructureReferenceValidation {
    private static final String JSON_EXTENSION = ".json";

    private final PackResources pack;
    private final Optional<BlockRegistry> blocks;
    private final Set<String> poolKeys;
    private final Set<String> pieceKeys;
    private final Set<String> objectKeys;
    private final List<Problem> problems = new ArrayList<>();

    private StructureReferenceValidation(PackResources pack, Optional<BlockRegistry> blocks) {
        this.pack = pack;
        this.blocks = blocks;
        this.poolKeys = keys(pack, StructureResources.POOLS);
        this.pieceKeys = keys(pack, StructureResources.PIECES);
        this.objectKeys = keys(pack, PackResources.OBJECTS);
    }

    /**
     * Checks the pack; the blocks its connectors turn into are checked against {@code blocks} when there is a platform
     * to know them.
     */
    static List<Problem> validate(PackResources pack, Optional<BlockRegistry> blocks) {
        StructureReferenceValidation validation = new StructureReferenceValidation(pack, blocks);
        validation.validateStructures();
        validation.validatePools();
        validation.validatePieces();
        return List.copyOf(validation.problems);
    }

    private void validateStructures() {
        forEachReadable(StructureResources.STRUCTURES, "Structure", this::validateStructure);
    }

    /** Hands each resource of {@code kind} to {@code check}; a file that cannot be read is an error instead. */
    private <T> void forEachReadable(ResourceKind<T> kind, String typeName, BiConsumer<String, T> check) {
        for (ResourceId id : pack.ids(kind)) {
            Optional<T> resource;
            try {
                resource = pack.find(kind, id);
            } catch (RuntimeException unreadable) {
                problems.add(Problem.error(file(kind, id.path()), typeName + " '" + id.path() + "' cannot be loaded: "
                        + failureMessage(unreadable)));
                continue;
            }
            resource.ifPresent(found -> check.accept(id.path(), found));
        }
    }

    private static String failureMessage(RuntimeException failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? failure.getClass().getSimpleName()
                : failure.getMessage();
    }

    private void validateStructure(String key, IrisStructure structure) {
        Subject file = file(StructureResources.STRUCTURES, key);
        String startPool = trimmed(structure.getStartPool());
        if (startPool.isEmpty()) {
            problems.add(Problem.error(file, "Structure '" + key + "' does not declare a start pool."));
            return;
        }
        if (!poolKeys.contains(startPool)) {
            problems.add(Problem.error(file, "Structure '" + key + "' references missing start pool '" + startPool + "'."));
        }
    }

    private void validatePools() {
        forEachReadable(StructureResources.POOLS, "Jigsaw pool", this::validatePool);
    }

    private void validatePool(String key, IrisJigsawPool pool) {
        Subject file = file(StructureResources.POOLS, key);
        List<IrisJigsawPieceEntry> entries = pool.getPieces() == null ? List.of() : pool.getPieces();
        for (int index = 0; index < entries.size(); index++) {
            String pieceKey = entries.get(index) == null ? "" : trimmed(entries.get(index).getPiece());
            if (!pieceKey.isEmpty() && !pieceKeys.contains(pieceKey)) {
                problems.add(Problem.error(file, "Jigsaw pool '" + key + "' pieces[" + index
                        + "] references missing piece '" + pieceKey + "'."));
            }
        }
        String fallback = trimmed(pool.getFallback());
        if (!fallback.isEmpty() && !poolKeys.contains(fallback)) {
            problems.add(Problem.error(file, "Jigsaw pool '" + key + "' references missing fallback pool '" + fallback + "'."));
        }
    }

    private void validatePieces() {
        forEachReadable(StructureResources.PIECES, "Jigsaw piece", this::validatePiece);
    }

    private void validatePiece(String key, IrisJigsawPiece piece) {
        Subject file = file(StructureResources.PIECES, key);
        String objectKey = trimmed(piece.getObject());
        if (objectKey.isEmpty()) {
            problems.add(Problem.error(file, "Jigsaw piece '" + key + "' does not declare an object."));
        } else if (!objectKeys.contains(objectKey)) {
            problems.add(Problem.error(file, "Jigsaw piece '" + key + "' references missing object '" + objectKey + "'."));
        }
        List<IrisJigsawConnector> connectors = piece.getConnectors() == null ? List.of() : piece.getConnectors();
        for (int index = 0; index < connectors.size(); index++) {
            String poolKey = connectors.get(index) == null ? "" : trimmed(connectors.get(index).getPool());
            if (!poolKey.isEmpty() && !poolKeys.contains(poolKey)) {
                problems.add(Problem.error(file, "Jigsaw piece '" + key + "' connectors[" + index
                        + "] references missing pool '" + poolKey + "'."));
            }
            if (connectors.get(index) != null) {
                validateFinalState(file, key, index, trimmed(connectors.get(index).getFinalState()));
            }
        }
    }

    /** A connector turns into its final state when its piece is placed, so it must be a block the platform knows. */
    private void validateFinalState(Subject file, String pieceKey, int index, String finalState) {
        if (blocks.isEmpty() || finalState.isEmpty() || blocks.get().find(finalState).isPresent()) {
            return;
        }
        problems.add(Problem.error(file, "Jigsaw piece '" + pieceKey + "' connectors[" + index + "] finalState '" + finalState
                + "' is not a block."));
    }

    private static Set<String> keys(PackResources pack, ResourceKind<?> kind) {
        return pack.ids(kind).stream().map(ResourceId::path).collect(Collectors.toUnmodifiableSet());
    }

    private static Subject file(ResourceKind<?> kind, String key) {
        return Subject.file(kind.folder() + "/" + key + JSON_EXTENSION);
    }

    private static String trimmed(String key) {
        return key == null ? "" : key.trim();
    }
}
