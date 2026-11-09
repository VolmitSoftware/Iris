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

package art.arcane.iris.core.structure.tooling;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.runtime.jigsaw.JigsawPlanarArchetype;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioBay;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioCellDimensions;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioGraphMapper;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioLayout;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioMode;
import art.arcane.iris.core.runtime.jigsaw.JigsawStudioVariant;
import art.arcane.iris.core.service.JigsawStudioService;
import art.arcane.iris.core.structure.tooling.StudioSessions.ActiveContext;
import art.arcane.iris.engine.framework.structure.PlanarJigsawWorkcellResolver;
import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.object.prefab.IrisObject;
import art.arcane.iris.engine.object.structure.IrisStructure;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawThemeSet;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawWorkcellArchetype;
import art.arcane.iris.engine.object.value.IrisPosition;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** What the commands that edit the open graph share: running a change as one graph transaction, and reading the graph back. */
final class GraphMutations {
    private static final String NUMBERED_FAMILY_PREFIX = "variant-";

    private GraphMutations() {
    }

    static void runGraphMutation(
            StudioUse use,
            ActiveContext context,
            JigsawStudioService.CommandGraphMutation mutation
    ) {
        use.onPlayer(() -> use.studio().runCommandGraphMutation(use.player(), context.request().requestId(), mutation));
    }

    static JigsawStudioLayout loadPersistedLayout(ActiveContext context) throws IOException {
        IrisData source = context.request().source();
        source.invalidateStructureResources();
        IrisStructure structure = StructureResources.structure(source.getPackResources(), context.request().structureKey());
        if (structure == null) {
            throw new IOException("The structure could not be reloaded after the graph transaction");
        }
        return JigsawStudioGraphMapper.map(source, structure);
    }

    static JigsawStudioService.CommandGraphMutationResult mappedMutationResult(
            ActiveContext context,
            String activateWorkcellId,
            String activatePieceKey,
            String message
    ) throws IOException {
        return new JigsawStudioService.CommandGraphMutationResult(
                loadPersistedLayout(context),
                activateWorkcellId,
                activatePieceKey,
                message);
    }

    static String workcellIdForVariant(JigsawStudioLayout layout, String pieceKey) throws IOException {
        JigsawStudioVariant variant = layout.variantCatalog().find(pieceKey).orElse(null);
        if (variant == null) {
            throw new IOException("Persisted variant '" + pieceKey + "' did not map to a Studio workcell");
        }
        return variant.archetype()
                .map(archetype -> archetype.stableId())
                .orElse(JigsawStudioLayout.SPATIAL_WORKCELL_ID);
    }

    static PieceWorkcellResolution resolvePieceWorkcell(
            JigsawStudioLayout layout,
            IrisJigsawPiece piece,
            IrisObject object
    ) {
        JigsawStudioLayout activeLayout = Objects.requireNonNull(layout, "Jigsaw Studio layout");
        IrisJigsawPiece activePiece = Objects.requireNonNull(piece, "Jigsaw piece");
        IrisObject activeObject = Objects.requireNonNull(object, "Jigsaw piece object");
        String workcellId;
        IrisPosition requiredDimensions;
        if (activeLayout.mode() == JigsawStudioMode.PLANAR_JIGSAW) {
            IrisJigsawWorkcellArchetype modelArchetype = IrisJigsawWorkcellArchetype.fromPiece(activePiece);
            workcellId = JigsawPlanarArchetype.fromModel(modelArchetype).stableId();
            requiredDimensions = PlanarJigsawWorkcellResolver.canonicalDimensions(activePiece, activeObject);
        } else {
            workcellId = JigsawStudioLayout.SPATIAL_WORKCELL_ID;
            requiredDimensions = new IrisPosition(
                    activeObject.getW(),
                    activeObject.getH(),
                    activeObject.getD());
        }
        JigsawStudioBay workcell = activeLayout.get(workcellId);
        if (workcell == null) {
            throw new IllegalArgumentException("Workcell '" + workcellId + "' is not configured");
        }
        return new PieceWorkcellResolution(workcell, requiredDimensions);
    }

    static String nextThemeKey(ActiveContext context) throws IOException {
        IrisStructure structure = StructureResources.structure(
                context.request().source().getPackResources(),
                context.request().structureKey());
        if (structure == null) {
            throw new IOException("The active jigsaw structure could not be loaded");
        }
        Set<String> declared = new HashSet<>();
        if (structure.getThemeSets() != null) {
            for (IrisJigsawThemeSet themeSet : structure.getThemeSets()) {
                if (themeSet != null && themeSet.getKey() != null) {
                    declared.add(themeSet.getKey());
                }
            }
        }
        int candidate = 1;
        while (candidate > 0) {
            String key = NUMBERED_FAMILY_PREFIX + candidate;
            if (!declared.contains(key)) {
                return key;
            }
            candidate = Math.incrementExact(candidate);
        }
        throw new IOException("Jigsaw Studio cannot allocate another numbered family key");
    }

    record PieceWorkcellResolution(
            JigsawStudioBay workcell,
            IrisPosition requiredDimensions
    ) {
        PieceWorkcellResolution {
            workcell = Objects.requireNonNull(workcell, "Jigsaw Studio workcell");
            requiredDimensions = Objects.requireNonNull(requiredDimensions, "Jigsaw piece dimensions");
        }

        boolean fits() {
            JigsawStudioCellDimensions capacity = workcell.capacity();
            return requiredDimensions.getX() <= capacity.width()
                    && requiredDimensions.getY() <= capacity.height()
                    && requiredDimensions.getZ() <= capacity.depth();
        }
    }
}
