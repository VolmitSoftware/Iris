package art.arcane.iris.platform.bukkit.api;

import art.arcane.iris.api.terrain.IrisColumnField;
import art.arcane.iris.api.terrain.IrisColumnQuery;
import art.arcane.iris.api.terrain.IrisRiverState;
import art.arcane.iris.api.terrain.IrisSurfaceKind;
import art.arcane.iris.api.terrain.IrisTerrainService;
import art.arcane.iris.generation.hydrology.HydrologyColumnLayer;
import art.arcane.iris.generation.hydrology.HydrologyColumnSample;
import art.arcane.iris.generation.hydrology.HydrologyFeatureRef;
import art.arcane.iris.generation.hydrology.HydrologyFeatureType;
import art.arcane.iris.platform.bukkit.plugin.IrisService;
import org.bukkit.World;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IrisTerrainSVCTest {
    private static final IrisColumnQuery SMALL = IrisColumnQuery.rect(
            0, 0, 15, 15, 4, EnumSet.of(IrisColumnField.SURFACE_HEIGHT, IrisColumnField.SURFACE_KIND));

    @Test
    public void theServiceImplementsBothContracts() throws NoSuchMethodException {
        assertTrue(IrisService.class.isAssignableFrom(IrisTerrainSVC.class));
        assertTrue(IrisTerrainService.class.isAssignableFrom(IrisTerrainSVC.class));
        IrisTerrainSVC.class.getDeclaredConstructor();
    }

    @Test
    public void aQueryIsOnlyAnswerableWhileEnabledAndAgainstARealWorld() {
        assertTrue(IrisTerrainSVC.answerable(true, true));
        assertFalse("a disabled service must never resolve a generator",
                IrisTerrainSVC.answerable(false, true));
        assertFalse(IrisTerrainSVC.answerable(true, false));
        assertFalse(IrisTerrainSVC.answerable(false, false));
    }

    @Test
    public void aServiceThatIsNotEnabledAnswersAbsenceInsteadOfThrowing() {
        IrisTerrainSVC service = new IrisTerrainSVC();

        assertFalse(service.isIrisWorld(null));
        assertTrue(service.worldInfo(null).isEmpty());
        assertEquals(OptionalInt.empty(), service.surfaceHeight(null, 0, 0));
        assertEquals(IrisSurfaceKind.UNKNOWN, service.surfaceKind(null, 0, 0));
        assertTrue(service.surfaceBiomeKey(null, 0, 0).isEmpty());
        assertTrue(service.surfaceBiomeName(null, 0, 0).isEmpty());
        assertTrue(service.biomeKey(null, 0, 64, 0).isEmpty());
        assertTrue(service.regionKey(null, 0, 0).isEmpty());
        assertTrue(service.regionName(null, 0, 0).isEmpty());
    }

    @Test
    public void theServiceExposesDisplayNamesAlongsideLoadKeys() throws NoSuchMethodException {
        assertEquals(Optional.class,
                IrisTerrainService.class.getMethod("surfaceBiomeName", World.class, int.class, int.class)
                        .getReturnType());
        assertEquals(Optional.class,
                IrisTerrainService.class.getMethod("regionName", World.class, int.class, int.class)
                        .getReturnType());
    }

    @Test
    public void anUnanswerableSampleNeverTouchesTheSink() {
        IrisTerrainSVC service = new IrisTerrainSVC();
        AtomicInteger sinkCalls = new AtomicInteger();

        boolean answered = service.sampleColumns(null, SMALL, sample -> sinkCalls.incrementAndGet());

        assertFalse(answered);
        assertEquals(0, sinkCalls.get());
    }

    @Test
    public void nullArgumentsAreRefusedRatherThanDereferenced() {
        IrisTerrainSVC service = new IrisTerrainSVC();

        assertFalse(service.sampleColumns(null, null, null));
        assertFalse(service.sampleColumns(null, SMALL, null));
    }

    @Test
    public void acceptedHydrologyMapsToThePublicDiagnosticStates() {
        assertEquals(IrisRiverState.NONE, IrisTerrainSVC.riverState(null));
        assertEquals(IrisRiverState.WET, IrisTerrainSVC.riverState(river(true)));
        assertEquals(IrisRiverState.DRY, IrisTerrainSVC.riverState(river(false)));
    }

    private static HydrologyColumnSample river(boolean connectedFluid) {
        HydrologyFeatureRef feature = new HydrologyFeatureRef(
                1L,
                HydrologyFeatureType.SURFACE_POOL,
                2L,
                3L,
                0,
                63,
                0,
                1,
                0,
                false
        );
        HydrologyColumnLayer layer = new HydrologyColumnLayer(
                feature,
                60,
                63,
                63,
                true,
                false,
                false,
                connectedFluid,
                false,
                false,
                true,
                connectedFluid,
                false,
                "water",
                "river",
                "mouth",
                "shore",
                "dry",
                "cave"
        );
        return new HydrologyColumnSample(0, 0, 70, 63, false, "parent", List.of(layer));
    }
}
