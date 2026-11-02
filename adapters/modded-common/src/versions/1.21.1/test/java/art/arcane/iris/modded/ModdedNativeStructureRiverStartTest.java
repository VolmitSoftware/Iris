package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedStructureStage;
import art.arcane.volmlib.nativelib.terrain.structure.StructureOwnershipRecordView;
import art.arcane.volmlib.nativelib.terrain.structure.StructureReferencePolicy;
import art.arcane.volmlib.nativelib.terrain.structure.StructureStartPlan;
import art.arcane.volmlib.nativelib.terrain.structure.StructureWaterColumn;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ModdedNativeStructureRiverStartTest {
    private static final String KEY = "minecraft:village_plains";

    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void plannedStartsWhosePiecesReachARiverBeyondTheStartChunkAreInvalidated() {
        Fixture fixture = new Fixture(35);

        fixture.adjust(fixture.plan(false));

        verify(fixture.chunk).setStartForStructure(fixture.structure, StructureStart.INVALID_START);
        verify(fixture.structures).discard(KEY, 0, 0);
    }

    @Test
    public void plannedStartsWithinTheLimitOrAllowingWaterKeepTheirStart() {
        Fixture within = new Fixture(36);
        within.adjust(within.plan(false));
        verify(within.chunk, never()).setStartForStructure(any(), any());

        Fixture flooded = new Fixture(35);
        flooded.adjust(flooded.plan(true));
        verify(flooded.chunk, never()).setStartForStructure(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static final class Fixture {
        private final NativeModdedStructureStage.Policy<Object, StructureStartPlan, Ownership> policy = mock(NativeModdedStructureStage.Policy.class);
        private final StructureReferencePolicy<StructureStartPlan, Ownership> structures = mock(StructureReferencePolicy.class);
        private final RegistryAccess registries = mock(RegistryAccess.class);
        private final ChunkAccess chunk = mock(ChunkAccess.class);
        private final Structure structure = mock(Structure.class);
        private final NativeModdedStructureStage<Object, StructureStartPlan, Ownership> stage;

        private Fixture(int riverEdgeX) {
            Registry<Structure> registry = mock(Registry.class);
            StructureStart start = mock(StructureStart.class);
            StructurePiece home = mock(StructurePiece.class);
            StructurePiece road = mock(StructurePiece.class);
            when(registries.registryOrThrow(Registries.STRUCTURE)).thenReturn(registry);
            when(registry.getKey(structure)).thenReturn(ResourceLocation.parse(KEY));
            when(chunk.getPos()).thenReturn(new ChunkPos(0, 0));
            when(chunk.getAllStarts()).thenReturn(Map.of(structure, start));
            when(start.isValid()).thenReturn(true);
            when(start.getChunkPos()).thenReturn(new ChunkPos(0, 0));
            when(start.getBoundingBox()).thenReturn(new BoundingBox(0, 60, 0, 39, 70, 15));
            when(home.getBoundingBox()).thenReturn(new BoundingBox(0, 60, 0, 15, 70, 15));
            when(road.getBoundingBox()).thenReturn(new BoundingBox(16, 64, 0, 39, 65, 15));
            when(start.getPieces()).thenReturn(List.of(home, road));
            when(policy.allowsFootprint(any(), any())).thenReturn(true);
            when(policy.structurePolicy(any())).thenReturn(structures);
            when(policy.water(any())).thenReturn((x, z) -> x >= riverEdgeX
                    ? new StructureWaterColumn(true, true, 58, 62) : StructureWaterColumn.DRY);
            stage = new NativeModdedStructureStage<>(new NativeModdedStructureStage.Options<>(null, null, policy));
        }

        private StructureStartPlan plan(boolean allowWater) {
            StructureStartPlan plan = mock(StructureStartPlan.class);
            when(plan.structureKey()).thenReturn(KEY);
            when(plan.allowWater()).thenReturn(allowWater);
            return plan;
        }

        private void adjust(StructureStartPlan plan) {
            stage.adjustGeneratedStructures(registries, chunk, Map.of(), Map.of(structure, plan), null, null);
        }
    }

    private interface Ownership extends StructureOwnershipRecordView<Ownership> {
    }
}
