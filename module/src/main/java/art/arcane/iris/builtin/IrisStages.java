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

import com.volmit.stage.ChunkStage;
import com.volmit.stage.MantleStage;
import com.volmit.stage.Stage;

/**
 * The mantle and chunk stages of Iris's built-in modules, one nested class per module, in the order their pipelines
 * run them: mantle, then chunk. The stage graph decides the actual order. The object placement stages are the
 * placement modules' own.
 */
public final class IrisStages {
    private IrisStages() {
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
}
