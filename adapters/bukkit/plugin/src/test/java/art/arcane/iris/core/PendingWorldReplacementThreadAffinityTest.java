package art.arcane.iris.core;

import art.arcane.iris.core.ExactWorldSlotPathPolicy.SlotKind;
import art.arcane.iris.core.lifecycle.BukkitWorldConfiguration.WorldGeneratorSnapshot;
import art.arcane.iris.core.lifecycle.WorldReplacementJournal.Phase;
import art.arcane.iris.core.lifecycle.WorldReplacementJournal.Transaction;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.engine.framework.EngineTarget;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisEnvironment;
import art.arcane.iris.engine.platform.PlatformChunkGenerator;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class PendingWorldReplacementThreadAffinityTest {
    @Test
    public void runtimeStateIsCapturedIntoAnImmutableDetachedSnapshot() {
        World world = mock(World.class);
        PlatformChunkGenerator generator = mock(PlatformChunkGenerator.class);
        EngineTarget target = mock(EngineTarget.class);
        IrisDimension dimension = mock(IrisDimension.class);
        when(world.getKey()).thenReturn(NamespacedKey.minecraft("the_nether"));
        when(world.getSeed()).thenReturn(-18273645L);
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(generator.getTarget()).thenReturn(target);
        when(target.getDimension()).thenReturn(dimension);
        when(dimension.getLoadKey()).thenReturn("underworld");
        when(dimension.getEnvironment()).thenReturn(IrisEnvironment.NETHER);

        PendingWorldReplacementManager.PublishedWorldRuntimeState runtimeState;
        try (MockedStatic<IrisToolbelt> toolbelt = mockStatic(IrisToolbelt.class)) {
            toolbelt.when(() -> IrisToolbelt.isIrisWorld(world)).thenReturn(true);
            toolbelt.when(() -> IrisToolbelt.access(world)).thenReturn(generator);
            runtimeState = PendingWorldReplacementManager.capturePublishedWorldRuntime(world);
        }

        assertEquals(WorldSlotKey.minecraft("the_nether"), runtimeState.worldKey());
        assertTrue(runtimeState.irisWorld());
        assertEquals(-18273645L, runtimeState.seed());
        assertEquals(World.Environment.NETHER, runtimeState.bukkitEnvironment());
        assertEquals("underworld", runtimeState.dimension());
        assertEquals(IrisEnvironment.NETHER, runtimeState.dimensionEnvironment());
    }

    @Test
    public void runtimeValidationRejectsIdentitySeedAndEnvironmentMismatches() throws Exception {
        Transaction transaction = transaction();
        PendingWorldReplacementManager.PublishedWorldRuntimeState valid = runtimeState(
                WorldSlotKey.minecraft("the_nether"),
                918273645L,
                World.Environment.NETHER,
                IrisEnvironment.NETHER
        );
        PendingWorldReplacementManager.validatePublishedWorldRuntime(
                valid,
                transaction,
                SlotKind.VANILLA_NETHER
        );

        IOException identityFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("overworld"),
                                918273645L,
                                World.Environment.NETHER,
                                IrisEnvironment.NETHER
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );
        IOException seedFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("the_nether"),
                                1L,
                                World.Environment.NETHER,
                                IrisEnvironment.NETHER
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );
        IOException bukkitEnvironmentFailure = assertThrows(
                IOException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("the_nether"),
                                918273645L,
                                World.Environment.NORMAL,
                                IrisEnvironment.NETHER
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );
        IllegalArgumentException irisEnvironmentFailure = assertThrows(
                IllegalArgumentException.class,
                () -> PendingWorldReplacementManager.validatePublishedWorldRuntime(
                        runtimeState(
                                WorldSlotKey.minecraft("the_nether"),
                                918273645L,
                                World.Environment.NETHER,
                                IrisEnvironment.NORMAL
                        ),
                        transaction,
                        SlotKind.VANILLA_NETHER
                )
        );

        assertEquals("Loaded world identity does not match the replacement journal.", identityFailure.getMessage());
        assertEquals("The replaced world loaded with an unexpected seed.", seedFailure.getMessage());
        assertEquals("The replaced world loaded with an unexpected environment.",
                bukkitEnvironmentFailure.getMessage());
        assertTrue(irisEnvironmentFailure.getMessage().contains("requires a pack environment of NETHER"));
    }

    private static PendingWorldReplacementManager.PublishedWorldRuntimeState runtimeState(
            WorldSlotKey worldKey,
            long seed,
            World.Environment bukkitEnvironment,
            IrisEnvironment irisEnvironment
    ) {
        return new PendingWorldReplacementManager.PublishedWorldRuntimeState(
                worldKey,
                true,
                seed,
                bukkitEnvironment,
                "underworld",
                irisEnvironment
        );
    }

    private static Transaction transaction() {
        return new Transaction(
                UUID.fromString("2e488654-c259-4587-a7f2-8a053d59b60f"),
                WorldSlotKey.minecraft("the_nether"),
                "world_nether",
                Path.of("build", "replacement-thread-test", "world"),
                "underworld",
                918273645L,
                "fingerprint",
                new WorldGeneratorSnapshot(false, false, false, null, false, null),
                true,
                Phase.PUBLISHED
        );
    }
}
