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

package art.arcane.iris.modules.placement;

import art.arcane.iris.builtin.anchor.AnchorHeight;
import art.arcane.iris.builtin.blocks.WrittenBlocks;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.stilt.StiltLayers;
import com.volmit.stage.BlockStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;

/**
 * The stages of Iris's built-in object placement modules, one nested class per module, in the order a placement runs
 * them. The stage graph decides the actual order.
 */
public final class PlacementStages {
    private PlacementStages() {
    }

    /** Module {@code iris:prepare}: what a placement decides before it looks for its height. */
    public static final class Prepare {
        /** The rotation the placement uses: the requested one, turned downhill when the settings ask to face the slope. Rejects {@code slope}. */
        public static final PlacementStage<ObjectRotation> SLOPE = Stage.placement("iris:prepare/slope", ObjectRotation.class);
        /** The random rotation, translation and height offset, chosen once per placement. */
        public static final PlacementStage<PreparedPlacement> TRANSFORMS = Stage.placement("iris:prepare/transforms", PreparedPlacement.class);

        private Prepare() {
        }
    }

    /** Module {@code iris:anchor}: the height an object is placed at. */
    public static final class Anchor {
        /** The requested or sampled height the checks judge. Rejects {@code carving-anchor}. */
        public static final PlacementStage<AnchorHeight> HEIGHT = Stage.placement("iris:anchor/height", AnchorHeight.class);
        /** The height the object's blocks are written at: the checked height plus the random offset. */
        public static final PlacementStage<AnchorHeight> FINAL = Stage.placement("iris:anchor/final", AnchorHeight.class);

        private Anchor() {
        }
    }

    /** Module {@code iris:validate}: the checks that reject a placement before anything is written, in this order. */
    public static final class Validate {
        /** Rejects {@code bedrock-anchor} and {@code surface-support}. */
        public static final PlacementStage<Void> SURFACE = Stage.placement("iris:validate/surface");
        /** Rejects {@code underwater-anchor}, {@code cave-clearance}, {@code fluid-height} and {@code height-clamp}. */
        public static final PlacementStage<Void> LIMITS = Stage.placement("iris:validate/limits");
        /** Rejects {@code native-structure}, even for placements that force their way past the other checks. */
        public static final PlacementStage<Void> STRUCTURES = Stage.placement("iris:validate/structures");
        /** Rejects {@code object-collision}. */
        public static final PlacementStage<Void> COLLISIONS = Stage.placement("iris:validate/collisions");

        private Validate() {
        }
    }

    /** Module {@code iris:bore}: clearing the object's volume before its blocks are written. */
    public static final class Bore {
        public static final PlacementStage<Void> CLEAR = Stage.placement("iris:bore/clear");

        private Bore() {
        }
    }

    /** Module {@code iris:blocks}: writing the object's blocks. */
    public static final class Blocks {
        /** Writes each object block where the placement puts it. */
        public static final BlockStage<WrittenBlocks> WRITE = Stage.placementBlock("iris:blocks/write", WrittenBlocks.class);
        /** Marks some written blocks with the placement's markers. */
        public static final PlacementStage<Void> MARKERS = Stage.placement("iris:blocks/markers");
        /** Milestone: stages requiring it run after the object and its markers are written. */
        public static final PlacementStage<Void> COMPLETE = Stage.placement("iris:blocks/complete");

        private Blocks() {
        }
    }

    /** Module {@code iris:stilt}: the stilts of the stilt and ceiling placement modes. */
    public static final class Stilt {
        /** Finds the layers to repeat. */
        public static final BlockStage<StiltLayers> LAYERS = Stage.placementBlock("iris:stilt/layers", StiltLayers.class);
        /** Grows the stilt once the object is written. */
        public static final PlacementStage<Void> FILL = Stage.placement("iris:stilt/fill");

        private Stilt() {
        }
    }

    /** Module {@code iris:vacuum}: bending the terrain to meet objects of the vacuum placement modes. */
    public static final class Vacuum {
        public static final PlacementStage<Void> TERRAIN = Stage.placement("iris:vacuum/terrain");

        private Vacuum() {
        }
    }

    /** Module {@code iris:snow}: snow on the object, after its stilt or terrain bend. */
    public static final class Snow {
        public static final PlacementStage<Void> COVER = Stage.placement("iris:snow/cover");

        private Snow() {
        }
    }
}
