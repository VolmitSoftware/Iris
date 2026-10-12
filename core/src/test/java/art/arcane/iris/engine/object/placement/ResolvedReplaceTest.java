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

package art.arcane.iris.engine.object.placement;

import art.arcane.iris.core.modules.BlockEntities;
import art.arcane.iris.engine.object.prefab.TileData;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.math.RNG;
import com.volmit.block.BlockRegistry;
import com.volmit.block.BlockSpec;
import com.volmit.block.BlockState;
import com.volmit.block.PaletteSampler;
import com.volmit.block.PaletteSpec;
import com.volmit.object.BlockEntityData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** What an {@link art.arcane.iris.builtin.blocks.ObjectReplace} does to a block: the seed its palette is picked with and the draws of its tile pick. */
public class ResolvedReplaceTest {
    private static final long SEED = 4242L;
    private TileData.TileReader previousReader;

    @Before
    public void bindPlatform() {
        IrisPlatforms.unbind();
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(registries.block(anyString())).thenAnswer(call -> state(call.getArgument(0)));
        when(registries.blockOrNull(anyString(), anyBoolean())).thenAnswer(call -> state(call.getArgument(0)));
        IrisPlatform platform = mock(IrisPlatform.class);
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        previousReader = TileData.bindPlatformReader(in -> new TileData(in.readUTF(), new KMap<>()));
    }

    @After
    public void unbindPlatform() {
        TileData.restorePlatformReader(previousReader);
        IrisPlatforms.unbind();
    }

    private static PlatformBlockState state(String key) {
        PlatformBlockState state = mock(PlatformBlockState.class);
        when(state.key()).thenReturn(key);
        return state;
    }

    private static BlockEntityData tile(String material) {
        return BlockEntities.of(new TileData(material, new KMap<>()));
    }

    @Test
    public void thePaletteIsPickedWithTheSeedOfTheRngThePlacementHandsIn() {
        BlockRegistry blocks = mock(BlockRegistry.class);
        PaletteSpec spec = PaletteSpec.of(List.of(BlockSpec.of("oak_leaves"), BlockSpec.of("birch_leaves")));
        double[] asked = new double[3];
        PaletteSampler sampler = (x, y, z) -> {
            asked[0] = x;
            asked[1] = y;
            asked[2] = z;
            return BlockState.parse("minecraft:birch_leaves");
        };
        when(blocks.palette(spec, SEED)).thenReturn(sampler);

        ResolvedReplace replace = new ResolvedReplace(List.of(), false, 1F, spec, blocks);

        assertEquals("minecraft:birch_leaves", replace.replacement(new RNG(SEED), 10, 64, -3).key());
        assertEquals(List.of(10D, 64D, -3D), List.of(asked[0], asked[1], asked[2]));
    }

    @Test
    public void aSingleWeightedEntryGivesItsTileWithoutDrawing() {
        BlockRegistry blocks = mock(BlockRegistry.class);
        PaletteSpec spec = PaletteSpec.of(List.of(BlockSpec.of("chest")));
        when(blocks.blockEntity(any())).thenReturn(Optional.of(tile("minecraft:chest")));
        ResolvedReplace replace = new ResolvedReplace(List.of(), false, 1F, spec, blocks);
        RNG rng = new RNG(SEED);

        assertEquals("minecraft:chest", BlockEntities.tileOf(replace.tile(rng).orElseThrow()).getMaterialKey());
        assertEquals("no draw", new RNG(SEED).nextInt(1000), rng.nextInt(1000));
    }

    @Test
    public void severalEntriesGiveTheTileOfTheUniformlyDrawnEntry() {
        BlockRegistry blocks = mock(BlockRegistry.class);
        BlockSpec first = BlockSpec.of("chest");
        BlockSpec second = BlockSpec.of("barrel");
        BlockSpec third = BlockSpec.of("furnace").withWeight(5);
        when(blocks.blockEntity(first)).thenReturn(Optional.of(tile("minecraft:chest")));
        when(blocks.blockEntity(second)).thenReturn(Optional.of(tile("minecraft:barrel")));
        when(blocks.blockEntity(third)).thenReturn(Optional.empty());
        ResolvedReplace replace = new ResolvedReplace(List.of(), false, 1F, PaletteSpec.of(List.of(first, second, third)), blocks);

        for (long seed = 0; seed < 40; seed++) {
            int drawn = new RNG(seed).nextInt(3);
            Optional<BlockEntityData> tile = replace.tile(new RNG(seed));
            if (drawn == 2) {
                assertTrue("an entry without tile data gives none", tile.isEmpty());
            } else {
                assertEquals(drawn == 0 ? "minecraft:chest" : "minecraft:barrel", BlockEntities.tileOf(tile.orElseThrow()).getMaterialKey());
            }
        }
    }

    @Test
    public void aPaletteWithNoWeightGivesNoTile() {
        BlockRegistry blocks = mock(BlockRegistry.class);
        ResolvedReplace replace = new ResolvedReplace(List.of(), false, 1F, PaletteSpec.of(List.of(BlockSpec.of("chest").withWeight(0))), blocks);

        assertTrue(replace.tile(new RNG(SEED)).isEmpty());
    }
}
