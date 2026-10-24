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

package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.world.biome.IrisBiome;
import art.arcane.iris.engine.object.world.dimension.IrisDimension;
import art.arcane.iris.engine.object.world.region.IrisRegion;
import com.volmit.IrisFeature;
import com.volmit.config.Feature;
import com.volmit.config.FeatureSettings;
import com.volmit.config.Level;
import com.volmit.config.SettingsStack;
import com.volmit.resource.ResourceId;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/** Iris answers a chunk's settings from its distinct regions and biomes, and keeps a column's stack per pack snapshot. */
public class ChunkModuleSettingsTest {
    private static final int CHUNK_X = 3;
    private static final int CHUNK_Z = -2;
    private static final int CHUNK_MIDDLE = 8;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Feature(id = "test:stacked", name = "Stacked", key = "stacked", targets = {Level.DIMENSION, Level.REGION, Level.BIOME})
    static final class StackedSettings implements IrisFeature {
    }

    private final SettingsStacks snapshotStacks = new SettingsStacks();
    private PackResourceIds ids;
    private IrisDimension dimension;
    private IrisRegion westRegion;
    private IrisRegion eastRegion;
    private IrisBiome northBiome;
    private IrisBiome southBiome;

    @Before
    public void createLevels() {
        ids = new PackResourceIds(folder.getRoot());
        dimension = configured(new IrisDimension(), "overworld");
        westRegion = configured(new IrisRegion(), "west");
        eastRegion = configured(new IrisRegion(), "east");
        northBiome = configured(new IrisBiome(), "north");
        southBiome = new IrisBiome();
        southBiome.setLoadKey("south");
    }

    @Test
    public void aChunksSettingsAreTheDimensionThenItsRegionsThenItsBiomesEachOnceInTheOrderTheScanMeetsThem() {
        SettingsStack<StackedSettings> declared = settings().declaredInChunk(StackedSettings.class, CHUNK_X, CHUNK_Z);

        assertEquals(List.of(Level.DIMENSION, Level.REGION, Level.REGION, Level.BIOME),
                declared.entries().stream().map(FeatureSettings.Entry::level).toList());
        assertEquals(List.of(ids.of("dimension", "overworld"), ids.of("region", "west"), ids.of("region", "east"), ids.of("biome", "north")),
                declared.entries().stream().map(FeatureSettings.Entry::source).toList());
    }

    @Test
    public void aChunksSettingsMatchTheColumnByColumnScanOfTheApi() {
        ChunkModuleSettings settings = settings();
        FeatureSettings columnByColumn = new FeatureSettings() {
            @Override
            public <F extends IrisFeature> SettingsStack<F> at(Class<F> featureType, int worldX, int worldZ) {
                return settings.at(featureType, worldX, worldZ);
            }

            @Override
            public <F extends IrisFeature> Optional<F> declaredBy(ResourceId biome, Class<F> featureType) {
                return settings.declaredBy(biome, featureType);
            }
        };

        assertEquals(columnByColumn.declaredInChunk(StackedSettings.class, CHUNK_X, CHUNK_Z).entries(),
                settings.declaredInChunk(StackedSettings.class, CHUNK_X, CHUNK_Z).entries());
    }

    @Test
    public void aColumnWhoseLevelsWereSeenBeforeGetsTheSameStackInAnyChunkOfTheSnapshot() {
        int worldX = CHUNK_X * 16;
        int worldZ = CHUNK_Z * 16;
        SettingsStack<StackedSettings> first = settings().at(StackedSettings.class, worldX, worldZ);

        assertSame(first, settings().at(StackedSettings.class, worldX + 1, worldZ + 1));
    }

    private ChunkModuleSettings settings() {
        return new ChunkModuleSettings(new SplitLevels(), ids);
    }

    private static <T extends IrisRegistrant> T configured(T owner, String key) {
        owner.setLoadKey(key);
        owner.setModuleSettings(ModuleSettings.of(Map.of(StackedSettings.class, new StackedSettings())));
        return owner;
    }

    /** One region west of each chunk's middle and one east of it; a configured biome north of the middle, a bare one south. */
    private final class SplitLevels implements ChunkModuleSettings.Levels {
        @Override
        public IrisRegistrant dimension() {
            return dimension;
        }

        @Override
        public IrisRegistrant region(int worldX, int worldZ) {
            return Math.floorMod(worldX, 16) < CHUNK_MIDDLE ? westRegion : eastRegion;
        }

        @Override
        public IrisRegistrant surfaceBiome(int worldX, int worldZ) {
            return Math.floorMod(worldZ, 16) < CHUNK_MIDDLE ? northBiome : southBiome;
        }

        @Override
        public IrisBiome biome(ResourceId id) {
            return null;
        }

        @Override
        public SettingsStacks stacks() {
            return snapshotStacks;
        }
    }
}
