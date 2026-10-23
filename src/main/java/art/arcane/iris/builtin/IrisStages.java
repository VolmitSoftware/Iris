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

package art.arcane.iris.builtin;

import art.arcane.iris.builtin.anchor.AnchorHeight;
import art.arcane.iris.builtin.blocks.WrittenBlocks;
import art.arcane.iris.builtin.prepare.ObjectRotation;
import art.arcane.iris.builtin.prepare.PreparedPlacement;
import art.arcane.iris.builtin.stilt.StiltLayers;
import com.volmit.stage.BlockStage;
import com.volmit.stage.ChunkStage;
import com.volmit.stage.MantleStage;
import com.volmit.stage.PlacementStage;
import com.volmit.stage.Stage;

/**
 * Every stage of Iris's built-in modules, one nested class per module. Modules are listed in the order their
 * pipelines run them: placement, then mantle, then chunk; a module with stages in several pipelines is listed
 * where its first stage runs. The stage graph decides the actual order.
 */
public final class IrisStages {
    private IrisStages() {
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

    /** Module {@code iris:carve}: caves, planned in the mantle and cut into each chunk's terrain. */
    public static final class Carve {
        /** Plans the caves of the dimension's cave profiles into the mantle, one block into each neighbor. */
        public static final MantleStage CAVES = Stage.mantle("iris:carve/caves");
        /** Cuts the planned caves out of the chunk's terrain and decorates their floors and ceilings. */
        public static final ChunkStage<Void> APPLY = Stage.chunk("iris:carve/apply");

        private Carve() {
        }
    }

    /** Module {@code iris:objects}: the objects the dimension's regions and biomes place. */
    public static final class Objects {
        /** Places surface, cave and upper-dimension objects into the mantle, after the caves they anchor in. */
        public static final MantleStage PLACE = Stage.mantle("iris:objects/place");

        private Objects() {
        }
    }

    /** Module {@code iris:floating}: the floating islands of floating child biomes. */
    public static final class Floating {
        /** Places the objects on and under the islands into the mantle, after the dimension's other objects. */
        public static final MantleStage OBJECTS = Stage.mantle("iris:floating/objects");
        /** Writes the islands' solid blocks. */
        public static final ChunkStage<Void> SOLIDS = Stage.chunk("iris:floating/solids");
        /** Decorates the islands' tops and bottoms once the chunk's own decoration is done. */
        public static final ChunkStage<Void> DECORATE = Stage.chunk("iris:floating/decorate");

        private Floating() {
        }
    }

    /** Module {@code iris:structure}: Iris structures, assembled from pieces. */
    public static final class Structure {
        /** Places the structures of the dimension, its regions and biomes into the mantle, after objects and floating objects. */
        public static final MantleStage PLACE = Stage.mantle("iris:structure/place");

        private Structure() {
        }
    }

    /** Module {@code iris:biome}: the biomes a chunk reports to the server. */
    public static final class Biome {
        /** Writes every column's biome, sky biome or custom biome, into the chunk. */
        public static final ChunkStage<Void> WRITE = Stage.chunk("iris:biome/write");

        private Biome() {
        }
    }

    /** Module {@code iris:terrain}: the solid ground, fluids and biome layers of a chunk. */
    public static final class Terrain {
        /** Fills each column from bedrock up to its height and the fluid height, with its biome's layers. */
        public static final ChunkStage<Void> SHAPE = Stage.chunk("iris:terrain/shape");

        private Terrain() {
        }
    }

    /** Module {@code iris:post}: smoothing a chunk's surface after carving. */
    public static final class Post {
        /** Fills single-block holes, places slabs and walls on steps and settles snow. */
        public static final ChunkStage<Void> SMOOTH = Stage.chunk("iris:post/smooth");

        private Post() {
        }
    }

    /** Module {@code iris:deposit}: ores and other deposits. */
    public static final class Deposit {
        /**
         * Places the deposits every level declares at the chunk's centre column, the dimension's, the region's and
         * the biome's, each with its own clumps. Active where any column of the chunk has deposits.
         */
        public static final ChunkStage<Void> ORES = Stage.chunk("iris:deposit/ores");

        private Deposit() {
        }
    }

    /** Module {@code iris:matter}: the blocks the mantle planned for a chunk, such as objects and structures. */
    public static final class Matter {
        /** Copies the chunk's planned mantle blocks into it. */
        public static final ChunkStage<Void> INSERT = Stage.chunk("iris:matter/insert");

        private Matter() {
        }
    }

    /** Module {@code iris:decorant}: the decorators of biomes, such as grass, flowers and cave ceilings. */
    public static final class Decorant {
        /** Places every column's surface, sea, shore and cave decorators. */
        public static final ChunkStage<Void> DECORATE = Stage.chunk("iris:decorant/decorate");

        private Decorant() {
        }
    }

    /** Module {@code iris:perfection}: the last touches on an overworld chunk's blocks. */
    public static final class Perfection {
        /** Hides ores when the dimension asks, then removes decorations that nothing holds up. */
        public static final ChunkStage<Void> SURFACE = Stage.chunk("iris:perfection/surface");

        private Perfection() {
        }
    }

    /** Module {@code iris:custom}: blocks of content plugins, which the server places after generation. */
    public static final class Custom {
        /** Records the chunk's custom blocks so the server can place them once the chunk exists. */
        public static final ChunkStage<Void> BLOCKS = Stage.chunk("iris:custom/blocks");

        private Custom() {
        }
    }
}
