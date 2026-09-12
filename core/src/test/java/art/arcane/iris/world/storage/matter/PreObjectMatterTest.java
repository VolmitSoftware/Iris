package art.arcane.iris.world.storage.matter;

import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveCell;
import art.arcane.iris.generation.hydrology.cave.HydrologyCaveAction;
import art.arcane.iris.testsupport.BukkitTestServer;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PreObjectMatterTest {
    @BeforeClass
    public static void setUpBukkit() {
        BukkitTestServer.install();
    }

    @Test
    public void sparseEntriesRemainSparseThroughIterationAndClear() throws IOException {
        PreObjectMatter matter = new PreObjectMatter(16, 16, 16);
        PreObjectMatterCell cell = PreObjectMatterCell.string("object-owner");
        assertTrue(matter.isEmpty());
        matter.set(2, 3, 4, cell);
        assertFalse(matter.isEmpty());
        int[] calls = new int[2];
        matter.iterateSync((x, y, z, value) -> {
            assertEquals(2, x.intValue());
            assertEquals(3, y.intValue());
            assertEquals(4, z.intValue());
            assertEquals(cell, value);
            calls[0]++;
        });
        matter.iterateSyncIO((x, y, z, value) -> {
            assertEquals(cell, value);
            calls[1]++;
        });
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);

        matter.empty(cell);
        assertTrue(matter.isEmpty());
        assertEquals(0, matter.getEntryCount());
        assertNull(matter.get(2, 3, 4));
    }

    @Test
    public void capturedNullsAndValuesRoundTripThroughMatter() throws IOException {
        IrisMatterSupport.ensureRegistered();
        MatterCavern cavern = new MatterCavern(true, "iris:flooded", (byte) 1);
        PreObjectMatterCell expected = PreObjectMatterCell.block(null)
                .captureString("object-owner")
                .captureCavern(cavern)
                .captureHydrology(new HydrologyCaveCell(HydrologyCaveAction.WET_SOURCE, "river", "flooded"));
        Matter matter = new IrisMatter(16, 16, 16);
        matter.<PreObjectMatterCell>slice(PreObjectMatterCell.class).set(2, 3, 4, expected);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.write(bytes);
        Matter restored = Matter.read(new ByteArrayInputStream(bytes.toByteArray()));
        PreObjectMatterCell actual = restored.<PreObjectMatterCell>getSlice(PreObjectMatterCell.class)
                .get(2, 3, 4);

        assertEquals(expected, actual);
        assertTrue(actual.blockCaptured());
        assertNull(actual.block());
        assertEquals(1, restored.<PreObjectMatterCell>getSlice(PreObjectMatterCell.class).getEntryCount());
    }

    @Test
    public void everySupportedNullTombstoneRoundTripsIndependently() throws IOException {
        IrisMatterSupport.ensureRegistered();
        Matter matter = new IrisMatter(16, 16, 16);
        MatterSlice<PreObjectMatterCell> journal = matter.slice(PreObjectMatterCell.class);
        journal.set(1, 1, 1, PreObjectMatterCell.block(null));
        journal.set(2, 2, 2, PreObjectMatterCell.string(null));
        journal.set(3, 3, 3, PreObjectMatterCell.cavern(null));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.write(bytes);
        Matter restored = Matter.read(new ByteArrayInputStream(bytes.toByteArray()));
        MatterSlice<PreObjectMatterCell> restoredJournal = restored.getSlice(PreObjectMatterCell.class);

        PreObjectMatterCell block = restoredJournal.get(1, 1, 1);
        PreObjectMatterCell string = restoredJournal.get(2, 2, 2);
        PreObjectMatterCell cavern = restoredJournal.get(3, 3, 3);
        assertTrue(block.blockCaptured());
        assertNull(block.block());
        assertTrue(string.stringCaptured());
        assertNull(string.string());
        assertTrue(cavern.cavernCaptured());
        assertNull(cavern.cavern());
        assertEquals(3, restoredJournal.getEntryCount());
    }

    @Test
    public void blockKeyHasAnExplicitPresenceFlag() throws IOException {
        PlatformBlockState block = mock(PlatformBlockState.class);
        when(block.key()).thenReturn("minecraft:stone");
        PreObjectMatter matter = new PreObjectMatter();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        matter.writeNode(PreObjectMatterCell.block(block), new DataOutputStream(bytes));

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(3, input.readUnsignedByte());
        assertEquals("minecraft:stone", input.readUTF());
        assertEquals(0, input.available());
    }

    @Test
    public void malformedFlagsAreRejected() {
        PreObjectMatter matter = new PreObjectMatter();

        assertThrows(IOException.class, () -> matter.readNode(input(0)));
        assertThrows(IOException.class, () -> matter.readNode(input(1 << 1)));
        assertThrows(IOException.class, () -> matter.readNode(input(1 << 7)));
    }

    @Test
    public void capturesRemainIndependentAndFirstWriteWins() {
        MatterCavern cavern = new MatterCavern(true, "", (byte) 0);
        PlatformBlockState block = mock(PlatformBlockState.class);
        PreObjectMatterCell cell = PreObjectMatterCell.string(null)
                .captureBlock(block)
                .captureCavern(cavern)
                .captureString("ignored");

        assertTrue(cell.blockCaptured());
        assertTrue(cell.stringCaptured());
        assertTrue(cell.cavernCaptured());
        assertEquals(block, cell.original(PlatformBlockState.class));
        assertNull(cell.original(String.class));
        assertEquals(cavern, cell.original(MatterCavern.class));
        assertFalse(cell.captures(Integer.class));
        assertThrows(IllegalArgumentException.class, () -> cell.original(Integer.class));
    }

    private DataInputStream input(int flags) {
        return new DataInputStream(new ByteArrayInputStream(new byte[]{(byte) flags}));
    }

}
