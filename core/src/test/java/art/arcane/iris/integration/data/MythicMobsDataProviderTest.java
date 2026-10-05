package art.arcane.iris.integration.data;

import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.world.history.SavedBiomeUnavailableException;
import io.lumine.mythic.api.adapters.AbstractLocation;
import io.lumine.mythic.api.config.MythicLineConfig;
import io.lumine.mythic.api.skills.conditions.ILocationCondition;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.adapters.BukkitWorld;
import io.lumine.mythic.core.skills.SkillExecutor;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MythicMobsDataProviderTest {
    @Test
    public void loadingChecksRetryTheSameLocationOnceSavedEnvironmentIsReady() {
        for (Query query : Query.values()) {
            try (Fixture fixture = new Fixture()) {
                ILocationCondition condition = fixture.condition(query);
                fixture.failure = new SavedBiomeUnavailableException("Saved biome information is loading", true);

                assertFalse(condition.check(fixture.target));

                fixture.failure = null;
                assertTrue(condition.check(fixture.target));
            }
        }
    }

    @Test
    public void readyEnvironmentMustMatchTheConfiguredName() {
        for (Query query : Query.values()) {
            try (Fixture fixture = new Fixture()) {
                ILocationCondition condition = fixture.condition(query);
                when(fixture.biome.getLoadKey()).thenReturn("other");
                when(fixture.region.getLoadKey()).thenReturn("other");

                assertFalse(condition.check(fixture.target));
            }
        }
    }

    @Test
    public void locationsWithoutAnIrisGeneratorOrEngineDoNotMatch() {
        for (Query query : Query.values()) {
            try (Fixture fixture = new Fixture()) {
                ILocationCondition condition = fixture.condition(query);
                fixture.toolbelt.when(() -> IrisToolbelt.access(fixture.world)).thenReturn(null);
                assertFalse(condition.check(fixture.target));

                fixture.toolbelt.when(() -> IrisToolbelt.access(fixture.world)).thenReturn(fixture.generator);
                when(fixture.generator.getEngine()).thenReturn(null);
                assertFalse(condition.check(fixture.target));
            }
        }
    }

    @Test
    public void permanentSavedEnvironmentFailuresPropagateUnchanged() {
        for (Query query : Query.values()) {
            try (Fixture fixture = new Fixture()) {
                ILocationCondition condition = fixture.condition(query);
                fixture.failure = new SavedBiomeUnavailableException("Saved epoch is missing", false);

                assertSame(fixture.failure, assertThrows(SavedBiomeUnavailableException.class,
                        () -> condition.check(fixture.target)));
            }
        }
    }

    @Test
    public void loadingFailuresWithSuppressedErrorsRemainVisible() {
        for (Query query : Query.values()) {
            try (Fixture fixture = new Fixture()) {
                ILocationCondition condition = fixture.condition(query);
                fixture.failure = new SavedBiomeUnavailableException("Saved biome information is loading", true);
                fixture.failure.addSuppressed(new IllegalStateException("failed to close saved epoch"));

                assertSame(fixture.failure, assertThrows(SavedBiomeUnavailableException.class,
                        () -> condition.check(fixture.target)));
            }
        }
    }

    @Test
    public void unrelatedRuntimeFailuresAreNotHidden() {
        for (Query query : Query.values()) {
            try (Fixture fixture = new Fixture()) {
                ILocationCondition condition = fixture.condition(query);
                fixture.failure = new IllegalStateException("broken integration query");

                assertSame(fixture.failure, assertThrows(IllegalStateException.class,
                        () -> condition.check(fixture.target)));
            }
        }
    }

    @Test
    public void volumeAndRegionQueriesUseWorldHeightWhileSurfaceUsesHorizontalCoordinates() {
        try (Fixture fixture = new Fixture()) {
            assertTrue(fixture.condition(Query.VOLUME).check(fixture.target));
            assertTrue(fixture.condition(Query.SURFACE).check(fixture.target));
            assertTrue(fixture.condition(Query.REGION).check(fixture.target));

            verify(fixture.engine).getBiomeOrMantle(12, 134, -18);
            verify(fixture.engine).getSurfaceBiome(12, -18);
            verify(fixture.engine).getRegion(12, 134, -18);
        }
    }

    private enum Query {
        VOLUME, SURFACE, REGION
    }

    private static final class Fixture implements AutoCloseable {
        private final World world = mock(World.class);
        private final PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        private final Engine engine = mock(Engine.class);
        private final IrisBiome biome = mock(IrisBiome.class);
        private final IrisRegion region = mock(IrisRegion.class);
        private final AbstractLocation target = mock(AbstractLocation.class);
        private final MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
        private final MockedStatic<MythicBukkit> mythic = mockStatic(MythicBukkit.class);
        private RuntimeException failure;

        private Fixture() {
            MythicBukkit plugin = mock(MythicBukkit.class);
            when(plugin.getSkillManager()).thenReturn(mock(SkillExecutor.class));
            mythic.when(MythicBukkit::inst).thenReturn(plugin);
            BukkitWorld targetWorld = mock(BukkitWorld.class);
            when(targetWorld.getBukkitWorld()).thenReturn(world);
            when(target.getWorld()).thenReturn(targetWorld);
            when(target.getBlockX()).thenReturn(12);
            when(target.getBlockY()).thenReturn(70);
            when(target.getBlockZ()).thenReturn(-18);
            toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
            when(generator.getEngine()).thenReturn(engine);
            when(engine.getMinHeight()).thenReturn(-64);
            when(biome.getLoadKey()).thenReturn("matching");
            when(region.getLoadKey()).thenReturn("matching");
            when(engine.getBiomeOrMantle(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> resolvedBiome());
            when(engine.getSurfaceBiome(anyInt(), anyInt())).thenAnswer(invocation -> resolvedBiome());
            when(engine.getRegion(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> resolvedRegion());
        }

        private ILocationCondition condition(Query query) {
            MythicLineConfig config = mock(MythicLineConfig.class);
            when(config.getString(any(String[].class), eq(""))).thenReturn("matching");
            when(config.getBoolean(any(String[].class), eq(false))).thenReturn(query == Query.SURFACE);
            return query == Query.REGION
                    ? new MythicMobsDataProvider.IrisRegionCondition("irisregion", config)
                    : new MythicMobsDataProvider.IrisBiomeCondition("irisbiome", config);
        }

        private IrisBiome resolvedBiome() {
            if (failure != null) {
                throw failure;
            }
            return biome;
        }

        private IrisRegion resolvedRegion() {
            if (failure != null) {
                throw failure;
            }
            return region;
        }

        @Override
        public void close() {
            mythic.close();
            toolbelt.close();
        }
    }
}
