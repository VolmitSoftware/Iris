package art.arcane.iris.structure.nativegen;

import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.structure.placement.IrisStructureLocator;
import art.arcane.volmlib.nativelib.terrain.StructureLocateProbe;
import org.junit.Test;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class StructureLocateSearchTest {
    private static final IrisStructureLocator.LocateResult RESULT = new IrisStructureLocator.LocateResult(
            IrisStructureLocator.LocateStatus.FOUND, -17, 64, 33);

    @Test
    @SuppressWarnings("unchecked")
    public void missingStartDoesNotResolveOwnership() {
        StructureLocateProbe<String> probe = mock(StructureLocateProbe.class);
        Function<String, NativeStructureOwnershipRecord> ownership = mock(Function.class);
        StructureLocateSearch<String> search = search(probe, ownership);

        assertNull(search.verify(RESULT));
        verify(probe).verifySelected(-2, 2);
        verifyNoInteractions(ownership);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void existingStartRequiresProvenOwnership() {
        StructureLocateProbe<String> probe = mock(StructureLocateProbe.class);
        Function<String, NativeStructureOwnershipRecord> ownership = mock(Function.class);
        when(probe.verifySelected(-2, 2)).thenReturn("native-start");

        assertNull(search(probe, ownership).verify(RESULT));
        verify(ownership).apply("native-start");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void verifiedSelectionReferencesTheOriginalStart() {
        StructureLocateProbe<String> probe = mock(StructureLocateProbe.class);
        Function<String, NativeStructureOwnershipRecord> ownership = mock(Function.class);
        NativeStructureOwnershipRecord record = mock(NativeStructureOwnershipRecord.class);
        when(probe.verifySelected(-2, 2)).thenReturn("native-start");
        when(ownership.apply("native-start")).thenReturn(record);
        StructureLocateSearch<String> search = search(probe, ownership);

        StructureLocateSearch.VerifiedStart<String> verified = search.verify(RESULT);

        assertEquals("native-start", verified.start());
        assertSame(record, verified.ownership());
        search.reference(verified);
        verify(probe).reference("native-start");
    }

    private static StructureLocateSearch<String> search(StructureLocateProbe<String> probe,
                                                        Function<String, NativeStructureOwnershipRecord> ownership) {
        return new StructureLocateSearch<>(new StructureLocateSearch.Options<>(
                mock(Engine.class), "minecraft:village_plains", 0, 0, 16, probe, ownership));
    }
}
