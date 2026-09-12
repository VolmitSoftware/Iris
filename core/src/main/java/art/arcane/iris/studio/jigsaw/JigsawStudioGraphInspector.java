package art.arcane.iris.studio.jigsaw;

import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.graph.PlanarJigsawWorkcellResolver;
import art.arcane.iris.structure.jigsaw.IrisJigsawMode;
import art.arcane.iris.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.structure.placement.IrisStructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Map;

final class JigsawStudioGraphInspector {
    private JigsawStudioGraphInspector() {
    }

    static void requireAvailableVariantTarget(
            JigsawStudioGraphEditor.OwnedGraph graph,
            String relativePath
    ) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path target = graph.root().resolve(relativePath).normalize();
        if (!target.startsWith(graph.root())) {
            throw new IOException("Variant target escapes the pack root: " + relativePath);
        }
        if (graph.manifest().resourceHashes().containsKey(relativePath)
                || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Variant target already exists: " + relativePath);
        }
    }

    static void requireAvailableThemeSetTarget(
            JigsawStudioGraphEditor.OwnedGraph graph,
            String relativePath
    ) throws IOException {
        StructureResourceBundle.validateRelativePath(relativePath);
        Path target = graph.root().resolve(relativePath).normalize();
        if (!target.startsWith(graph.root())) {
            throw new IOException("Theme-set target escapes the pack root: " + relativePath);
        }
        if (graph.manifest().resourceHashes().containsKey(relativePath)
                || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Theme-set target already exists: " + relativePath);
        }
    }

    static boolean otherOwnedPieceReferencesObject(
            JigsawStudioGraphEditor.OwnedGraph graph,
            String targetPieceKey,
            String objectKey
    ) throws IOException {
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pieces/") || !relativePath.endsWith(".json")) {
                continue;
            }
            String pieceKey = JigsawStudioGraphDocuments.resourceKey(relativePath, "jigsaw-pieces/", ".json");
            if (pieceKey.equals(targetPieceKey)) {
                continue;
            }
            IrisJigsawPiece piece = JigsawStudioGraphEditor.readPiece(JigsawStudioGraphEditor.resolveOwnedResource(graph.root(), relativePath), relativePath);
            if (objectKey.equals(piece.getObject())) {
                return true;
            }
        }
        return false;
    }

    static void requireDeletableArchetype(
            JigsawStudioGraphEditor.OwnedGraph graph,
            String targetPieceKey,
            IrisJigsawPiece targetPiece
    ) throws IOException {
        String structureResource = "structures/" + graph.manifest().structure().path() + ".json";
        if (!graph.manifest().resourceHashes().containsKey(structureResource)) {
            throw new IOException("The owned graph manifest does not include " + structureResource);
        }
        IrisStructure structure = JigsawStudioGraphEditor.readStructure(
                JigsawStudioGraphEditor.resolveOwnedResource(graph.root(), structureResource),
                structureResource);
        if (structure.resolvedMode() == IrisJigsawMode.SPATIAL_JIGSAW) {
            if (countOtherOwnedPieces(graph, targetPieceKey, null) == 0) {
                throw new IOException("Cannot delete the final spatial jigsaw variant");
            }
            return;
        }
        IrisJigsawWorkcellArchetype archetype = IrisJigsawWorkcellArchetype.fromPiece(targetPiece);
        Map<IrisJigsawWorkcellArchetype, PlanarJigsawWorkcellResolver.ResolvedWorkcell> workcells;
        try {
            workcells = PlanarJigsawWorkcellResolver.resolve(structure);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Planar workcell configuration is invalid: "
                    + exception.getMessage(), exception);
        }
        PlanarJigsawWorkcellResolver.ResolvedWorkcell workcell = workcells.get(archetype);
        if (workcell != null && workcell.enabled()
                && countOtherOwnedPieces(graph, targetPieceKey, archetype) == 0) {
            throw new IOException("Cannot delete the final variant for enabled planar workcell "
                    + JigsawPlanarArchetype.fromModel(archetype).stableId());
        }
    }

    static int countOtherOwnedPieces(
            JigsawStudioGraphEditor.OwnedGraph graph,
            String targetPieceKey,
            IrisJigsawWorkcellArchetype archetype
    ) throws IOException {
        int count = 0;
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pieces/") || !relativePath.endsWith(".json")) {
                continue;
            }
            String pieceKey = JigsawStudioGraphDocuments.resourceKey(relativePath, "jigsaw-pieces/", ".json");
            if (pieceKey.equals(targetPieceKey)) {
                continue;
            }
            if (archetype == null) {
                count++;
                continue;
            }
            IrisJigsawPiece piece = JigsawStudioGraphEditor.readPiece(JigsawStudioGraphEditor.resolveOwnedResource(graph.root(), relativePath), relativePath);
            if (IrisJigsawWorkcellArchetype.fromPiece(piece) == archetype) {
                count++;
            }
        }
        return count;
    }

    static void requireExclusiveObjectReference(
            JigsawStudioGraphEditor.OwnedGraph graph,
            String activePieceKey,
            String objectKey
    ) throws IOException {
        for (String relativePath : graph.manifest().resourceHashes().keySet()) {
            if (!relativePath.startsWith("jigsaw-pieces/") || !relativePath.endsWith(".json")) {
                continue;
            }
            String pieceKey = relativePath.substring(
                    "jigsaw-pieces/".length(),
                    relativePath.length() - ".json".length());
            if (pieceKey.equals(activePieceKey)) {
                continue;
            }
            IrisJigsawPiece piece = JigsawStudioGraphEditor.readPiece(JigsawStudioGraphEditor.resolveOwnedResource(graph.root(), relativePath), relativePath);
            if (objectKey.equals(piece.getObject())) {
                throw new IOException("Object '" + objectKey + "' is shared by piece '" + pieceKey
                        + "'; duplicate the active variant before resizing its object bounds");
            }
        }
    }
}
