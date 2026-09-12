package art.arcane.iris.command;

import art.arcane.iris.Iris;
import art.arcane.iris.world.IrisToolbelt;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.world.history.GenerationFindCatalog;
import art.arcane.iris.generation.biome.IrisBiome;
import art.arcane.iris.generation.terrain.IrisRegion;
import art.arcane.iris.platform.generation.EngineBukkitOps;
import art.arcane.iris.platform.generation.PlatformChunkGenerator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.director.DirectorEngineOptions;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionDispatcher;
import art.arcane.volmlib.util.director.runtime.DirectorExecutionResult;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import art.arcane.volmlib.util.plugin.ComponentMessenger;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class CommandSVCFindParsingTest {
    @Test
    public void registeredBiomeHandlerResolvesActiveThenRemovedNamesDuringParsing() throws Exception {
        Fixture fixture = new Fixture();
        TestCommandService service = new TestCommandService(DirectorExecutionDispatcher.IMMEDIATE);
        IrisBiome plateau = biome("plateau");
        IrisBiome meadow = biome("meadow");
        try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
             MockedStatic<GenerationFindCatalog> catalog = mockStatic(GenerationFindCatalog.class);
             MockedStatic<EngineBukkitOps> operations = mockStatic(EngineBukkitOps.class)) {
            toolbelt.when(() -> IrisToolbelt.access(fixture.world)).thenReturn(fixture.generator);
            catalog.when(() -> GenerationFindCatalog.biomes(fixture.engine))
                    .thenReturn(new KList<>(plateau, meadow));

            assertTrue(execute(service, fixture.player, "biome", "biome=plateau").isSuccess());
            assertNull(DirectorContext.get());
            assertTrue(execute(service, fixture.player, "biome", "biome=meadow").isSuccess());
            assertNull(DirectorContext.get());

            operations.verify(() -> EngineBukkitOps.gotoBiome(same(fixture.engine), same(plateau), same(fixture.player), eq(true)));
            operations.verify(() -> EngineBukkitOps.gotoBiome(same(fixture.engine), same(meadow), same(fixture.player), eq(true)));
            catalog.verify(() -> GenerationFindCatalog.biomes(fixture.engine), times(2));
            catalog.verifyNoMoreInteractions();
        }
    }

    @Test
    public void registeredHistoricalHandlersParseBeforeDeferredInvocationAndClearFailedContext() throws Exception {
        Fixture fixture = new Fixture();
        List<Runnable> queued = new ArrayList<>();
        TestCommandService service = new TestCommandService((mode, action) -> queued.add(action));
        IrisBiome meadow = biome("meadow");
        IrisRegion oldRegion = new IrisRegion();
        oldRegion.setLoadKey("old-region");
        try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class);
             MockedStatic<GenerationFindCatalog> catalog = mockStatic(GenerationFindCatalog.class);
             MockedStatic<EngineBukkitOps> operations = mockStatic(EngineBukkitOps.class);
             MockedStatic<ComponentMessenger> messages = mockStatic(ComponentMessenger.class)) {
            toolbelt.when(() -> IrisToolbelt.access(fixture.world)).thenReturn(fixture.generator);
            catalog.when(() -> GenerationFindCatalog.biomes(fixture.engine)).thenReturn(new KList<>(meadow));
            catalog.when(() -> GenerationFindCatalog.regions(fixture.engine)).thenReturn(new KList<>(oldRegion));
            catalog.when(() -> GenerationFindCatalog.objectKeys(fixture.engine)).thenReturn(new KList<>("old-tree"));
            operations.when(() -> EngineBukkitOps.gotoBiome(fixture.engine, meadow, fixture.player, true))
                    .thenAnswer(invocation -> {
                        assertSame(fixture.player, DirectorContext.get().player());
                        return null;
                    });

            assertTrue(execute(service, fixture.player, "biome", "biome=meadow").isSuccess());
            assertTrue(execute(service, fixture.player, "region", "region=old-region").isSuccess());
            assertTrue(execute(service, fixture.player, "object", "object=old-tree").isSuccess());
            assertEquals(3, queued.size());
            assertNull(DirectorContext.get());

            queued.getFirst().run();
            assertNull(DirectorContext.get());
            operations.verify(() -> EngineBukkitOps.gotoBiome(fixture.engine, meadow, fixture.player, true));

            DirectorExecutionResult rejected = execute(service, fixture.player, "biome", "biome=authoring-only");
            assertFalse(rejected.isSuccess());
            assertTrue(rejected.getMessage().contains("Cannot convert"));
            assertEquals(3, queued.size());
            assertNull(DirectorContext.get());
            catalog.verify(() -> GenerationFindCatalog.regions(fixture.engine));
            catalog.verify(() -> GenerationFindCatalog.objectKeys(fixture.engine));
        }
    }

    private static DirectorExecutionResult execute(CommandSVC service, Player player, String command, String argument)
            throws Exception {
        Method run = CommandSVC.class.getDeclaredMethod("runDirector", CommandSender.class, String.class, String[].class);
        run.setAccessible(true);
        return (DirectorExecutionResult) run.invoke(service, player, "iris",
                new String[]{"find", command, argument, "teleport=true"});
    }

    private static IrisBiome biome(String key) {
        IrisBiome biome = new IrisBiome();
        biome.setLoadKey(key);
        return biome;
    }

    private static final class Fixture {
        private final Engine engine = mock(Engine.class);
        private final Player player = mock(Player.class);
        private final World world = mock(World.class);
        private final PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);

        private Fixture() {
            when(player.getWorld()).thenReturn(world);
            when(player.getUniqueId()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
            when(generator.getEngine()).thenReturn(engine);
        }
    }

    private static final class TestCommandService extends CommandSVC {
        private final DirectorRuntimeEngine director;

        private TestCommandService(DirectorExecutionDispatcher dispatcher) {
            try (MockedStatic<Iris> iris = mockStatic(Iris.class)) {
                director = DirectorEngineFactory.create(new CommandIris(), DirectorEngineOptions.builder()
                        .dispatcher(dispatcher)
                        .invocationHook(this)
                        .build());
            }
        }

        @Override
        public DirectorRuntimeEngine getDirector() {
            return director;
        }
    }
}
