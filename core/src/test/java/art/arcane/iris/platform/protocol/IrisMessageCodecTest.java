/*
 * Iris is a World Generator for Minecraft Bukkit Servers
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

package art.arcane.iris.platform.protocol;

import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;
import art.arcane.iris.spi.protocol.IrisWireReader;
import art.arcane.iris.spi.protocol.IrisWireWriter;
import art.arcane.iris.spi.protocol.ProtocolException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisMessageCodecTest {
    @Test
    public void clientHelloRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.ClientHello original = new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN);
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
    }

    @Test
    public void serverHelloRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.ServerHello original = new IrisMessage.ServerHello(IrisProtocol.PROTOCOL_VERSION, IrisProtocol.CAPABILITY_PREGEN, "Paper", true);
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
    }

    @Test
    public void pregenProgressRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.PregenProgress original = new IrisMessage.PregenProgress(42L, 128L, 4096L, 512.25D, 60000L, IrisMessage.PregenProgress.STATE_RUNNING);
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
    }

    @Test
    public void pregenEndRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.PregenEnd completed = new IrisMessage.PregenEnd(7L, true);
        IrisMessage.PregenEnd aborted = new IrisMessage.PregenEnd(9L, false);
        assertEquals(completed, IrisMessageCodec.decode(IrisMessageCodec.encode(completed)));
        assertEquals(aborted, IrisMessageCodec.decode(IrisMessageCodec.encode(aborted)));
    }

    @Test
    public void negativeAndExtremePrimitivesRoundTrip() throws ProtocolException {
        IrisMessage.ClientHello hello = new IrisMessage.ClientHello(Integer.MIN_VALUE, Long.MIN_VALUE);
        IrisMessage.PregenProgress progress = new IrisMessage.PregenProgress(Long.MAX_VALUE, Long.MIN_VALUE, -1L, -12.5D, -1L, Integer.MIN_VALUE);
        assertEquals(hello, IrisMessageCodec.decode(IrisMessageCodec.encode(hello)));
        assertEquals(progress, IrisMessageCodec.decode(IrisMessageCodec.encode(progress)));
    }

    @Test
    public void emptyAndUnicodeStringsRoundTrip() throws ProtocolException {
        IrisMessage.ServerHello empty = new IrisMessage.ServerHello(1, 0L, "", false);
        IrisMessage.ServerHello unicode = new IrisMessage.ServerHello(1, 0L, "Paper🌍日本語", true);
        assertEquals(empty, IrisMessageCodec.decode(IrisMessageCodec.encode(empty)));
        assertEquals(unicode, IrisMessageCodec.decode(IrisMessageCodec.encode(unicode)));
    }

    @Test
    public void varintRoundTripsBoundaryValues() throws ProtocolException {
        int[] values = {0, 1, 127, 128, 16383, 16384, Integer.MAX_VALUE, -1, Integer.MIN_VALUE};
        for (int value : values) {
            IrisWireWriter writer = new IrisWireWriter();
            writer.writeVarInt(value);
            IrisWireReader reader = new IrisWireReader(writer.toByteArray());
            assertEquals(value, reader.readVarInt());
        }
    }

    @Test
    public void maxWidthVarintEncodesToFiveBytes() {
        IrisWireWriter writer = new IrisWireWriter();
        writer.writeVarInt(-1);
        assertEquals(5, writer.toByteArray().length);
    }

    @Test
    public void everyPrefixOfAValidFrameRejectsCleanly() {
        byte[][] frames = {
                IrisMessageCodec.encode(new IrisMessage.ClientHello(1, IrisProtocol.CAPABILITY_PREGEN)),
                IrisMessageCodec.encode(new IrisMessage.ServerHello(1, 0L, "Paper🌍", true)),
                IrisMessageCodec.encode(new IrisMessage.PregenProgress(1L, 2L, 3L, 4.5D, 6L, 1)),
                IrisMessageCodec.encode(new IrisMessage.PregenEnd(1L, true))
        };
        for (byte[] frame : frames) {
            for (int prefixLength = 0; prefixLength < frame.length; prefixLength++) {
                byte[] prefix = Arrays.copyOf(frame, prefixLength);
                assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(prefix));
            }
        }
    }

    @Test
    public void encodingBeyondFrameCapThrowsIllegalState() {
        String oversizedBrand = "a".repeat(IrisProtocol.MAX_FRAME_BYTES);
        IrisMessage.ServerHello oversized = new IrisMessage.ServerHello(1, 0L, oversizedBrand, true);
        assertThrows(IllegalStateException.class, () -> IrisMessageCodec.encode(oversized));
    }

    @Test
    public void decodingFrameBeyondCapRejects() {
        byte[] oversized = new byte[IrisProtocol.MAX_FRAME_BYTES + 1];
        oversized[0] = (byte) IrisProtocol.TYPE_CLIENT_HELLO;
        assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(oversized));
    }

    @Test
    public void poisonedStringLengthRejectsWithoutAllocatingClaimedSize() {
        IrisWireWriter writer = new IrisWireWriter();
        writer.writeVarInt(IrisProtocol.TYPE_SERVER_HELLO);
        writer.writeInt(1);
        writer.writeLong(0L);
        writer.writeVarInt(Integer.MAX_VALUE);
        byte[] poisoned = writer.toByteArray();
        assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(poisoned));
    }

    @Test
    public void unknownMessageTypeDecodesToIgnorableNull() throws ProtocolException {
        IrisWireWriter writer = new IrisWireWriter();
        writer.writeVarInt(9999);
        writer.writeLong(123L);
        assertNull(IrisMessageCodec.decode(writer.toByteArray()));
    }

    @Test
    public void dimensionStatusRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.DimensionStatus original = new IrisMessage.DimensionStatus("minecraft:overworld", "overworld", 1337L, -64, 320, true);
        IrisMessage.DimensionStatus disabled = new IrisMessage.DimensionStatus("", "", Long.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, false);
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
        assertEquals(disabled, IrisMessageCodec.decode(IrisMessageCodec.encode(disabled)));
    }

    @Test
    public void cursorInfoRequestRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.CursorInfoRequest original = new IrisMessage.CursorInfoRequest(1024, -2048);
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
    }

    @Test
    public void cursorInfoRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.CursorInfo original = new IrisMessage.CursorInfo(12, -34, "iris:plains", "iris:temperate", "", 72, "overworld");
        IrisMessage.CursorInfo withCave = new IrisMessage.CursorInfo(Integer.MIN_VALUE, Integer.MAX_VALUE, "iris:desert", "iris:arid", "iris:lush_cave", -1, "minecraft:the_nether");
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
        assertEquals(withCave, IrisMessageCodec.decode(IrisMessageCodec.encode(withCave)));
    }

    @Test
    public void visionTileRequestRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.VisionTileRequest original = new IrisMessage.VisionTileRequest(-9, 15, 3);
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
    }

    @Test
    public void visionTileRoundTripsThroughCodec() throws ProtocolException {
        byte[] payload = {1, 2, 3, 4, 5, -1, -2, 127, 0};
        IrisMessage.VisionTile original = new IrisMessage.VisionTile(3, -4, 2, 7, 1, 5, payload);
        IrisMessage.VisionTile decoded = (IrisMessage.VisionTile) IrisMessageCodec.decode(IrisMessageCodec.encode(original));
        assertEquals(original.tileX(), decoded.tileX());
        assertEquals(original.tileZ(), decoded.tileZ());
        assertEquals(original.zoomLevel(), decoded.zoomLevel());
        assertEquals(original.sequence(), decoded.sequence());
        assertEquals(original.chunkIndex(), decoded.chunkIndex());
        assertEquals(original.chunkCount(), decoded.chunkCount());
        assertArrayEquals(payload, decoded.data());
    }

    @Test
    public void visionTileEmptyDataRoundTrips() throws ProtocolException {
        IrisMessage.VisionTile original = new IrisMessage.VisionTile(0, 0, 0, 0, 0, 1, new byte[0]);
        IrisMessage.VisionTile decoded = (IrisMessage.VisionTile) IrisMessageCodec.decode(IrisMessageCodec.encode(original));
        assertArrayEquals(new byte[0], decoded.data());
    }

    @Test
    public void visionMarkersRoundTripsThroughCodec() throws ProtocolException {
        List<IrisMessage.VisionMarkers.Marker> markers = List.of(
                new IrisMessage.VisionMarkers.Marker(10, 20, 1, "spawn"),
                new IrisMessage.VisionMarkers.Marker(-5, 7, 2, ""),
                new IrisMessage.VisionMarkers.Marker(Integer.MIN_VALUE, Integer.MAX_VALUE, 3, "日本語"));
        IrisMessage.VisionMarkers original = new IrisMessage.VisionMarkers(4, -8, 1, markers);
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
    }

    @Test
    public void visionMarkersEmptyListRoundTrips() throws ProtocolException {
        IrisMessage.VisionMarkers original = new IrisMessage.VisionMarkers(0, 0, 0, List.of());
        assertEquals(original, IrisMessageCodec.decode(IrisMessageCodec.encode(original)));
    }

    @Test
    public void everyPrefixOfANewFrameRejectsCleanly() {
        byte[][] frames = {
                IrisMessageCodec.encode(new IrisMessage.DimensionStatus("minecraft:overworld", "overworld", 1337L, -64, 320, true)),
                IrisMessageCodec.encode(new IrisMessage.CursorInfoRequest(100, -200)),
                IrisMessageCodec.encode(new IrisMessage.CursorInfo(1, 2, "plains", "region", "", 72, "overworld")),
                IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(3, 4, 2)),
                IrisMessageCodec.encode(new IrisMessage.VisionTile(1, 2, 0, 0, 0, 1, new byte[]{9, 8, 7})),
                IrisMessageCodec.encode(new IrisMessage.VisionMarkers(1, 2, 0, List.of(new IrisMessage.VisionMarkers.Marker(1, 2, 0, "a"))))
        };
        for (byte[] frame : frames) {
            for (int prefixLength = 0; prefixLength < frame.length; prefixLength++) {
                byte[] prefix = Arrays.copyOf(frame, prefixLength);
                assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(prefix));
            }
        }
    }

    @Test
    public void visionTileChunkAtCapFitsUnderFrameCap() {
        byte[] data = new byte[IrisProtocol.VISION_TILE_MAX_CHUNK_BYTES];
        IrisMessage.VisionTile tile = new IrisMessage.VisionTile(Integer.MAX_VALUE, Integer.MIN_VALUE, IrisProtocol.MAX_FRAME_BYTES, Integer.MAX_VALUE, 4095, 4096, data);
        byte[] frame = IrisMessageCodec.encode(tile);
        assertTrue("vision tile chunk frame must fit under cap", frame.length <= IrisProtocol.MAX_FRAME_BYTES);
        assertTrue("header math must reserve room for the max chunk", IrisProtocol.VISION_TILE_HEADER_BYTES + 3 + IrisProtocol.VISION_TILE_MAX_CHUNK_BYTES <= IrisProtocol.MAX_FRAME_BYTES);
    }

    @Test
    public void visionMarkersAtCapFitUnderFrameCap() {
        List<IrisMessage.VisionMarkers.Marker> markers = new ArrayList<>(IrisProtocol.MAX_VISION_MARKERS);
        for (int index = 0; index < IrisProtocol.MAX_VISION_MARKERS; index++) {
            markers.add(new IrisMessage.VisionMarkers.Marker(index, -index, index & 7, "m" + index));
        }
        byte[] frame = IrisMessageCodec.encode(new IrisMessage.VisionMarkers(1, 2, 0, markers));
        assertTrue("marker frame at cap must fit under cap", frame.length <= IrisProtocol.MAX_FRAME_BYTES);
    }

    @Test
    public void poisonedMarkerCountRejectsWithoutOverread() {
        IrisWireWriter writer = new IrisWireWriter();
        writer.writeVarInt(IrisProtocol.TYPE_VISION_MARKERS);
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeInt(0);
        writer.writeVarInt(Integer.MAX_VALUE);
        byte[] poisoned = writer.toByteArray();
        assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(poisoned));
    }

    @Test
    public void pregenRegionDeltaRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.PregenRegionDelta generating = new IrisMessage.PregenRegionDelta(42L, 3, -7, IrisMessage.PregenRegionDelta.STATE_GENERATING);
        IrisMessage.PregenRegionDelta done = new IrisMessage.PregenRegionDelta(Long.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, IrisMessage.PregenRegionDelta.STATE_DONE);
        assertEquals(generating, IrisMessageCodec.decode(IrisMessageCodec.encode(generating)));
        assertEquals(done, IrisMessageCodec.decode(IrisMessageCodec.encode(done)));
    }

    @Test
    public void studioHotloadRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.StudioHotload ok = new IrisMessage.StudioHotload("overworld", 12, false, "");
        IrisMessage.StudioHotload failed = new IrisMessage.StudioHotload("iris:desert", Integer.MIN_VALUE, true, "IllegalStateException: bad pack🌍");
        assertEquals(ok, IrisMessageCodec.decode(IrisMessageCodec.encode(ok)));
        assertEquals(failed, IrisMessageCodec.decode(IrisMessageCodec.encode(failed)));
    }

    @Test
    public void toastRoundTripsThroughCodec() throws ProtocolException {
        IrisMessage.Toast info = new IrisMessage.Toast(IrisMessage.Toast.KIND_INFO, "Title", "Body");
        IrisMessage.Toast error = new IrisMessage.Toast(IrisMessage.Toast.KIND_ERROR, "", "日本語");
        assertEquals(info, IrisMessageCodec.decode(IrisMessageCodec.encode(info)));
        assertEquals(error, IrisMessageCodec.decode(IrisMessageCodec.encode(error)));
    }

    @Test
    public void everyPrefixOfAStudioComfortFrameRejectsCleanly() {
        byte[][] frames = {
                IrisMessageCodec.encode(new IrisMessage.PregenRegionDelta(9L, 4, -5, IrisMessage.PregenRegionDelta.STATE_GENERATING)),
                IrisMessageCodec.encode(new IrisMessage.StudioHotload("overworld", 3, true, "boom")),
                IrisMessageCodec.encode(new IrisMessage.Toast(IrisMessage.Toast.KIND_SUCCESS, "Studio Hotload", "overworld"))
        };
        for (byte[] frame : frames) {
            for (int prefixLength = 0; prefixLength < frame.length; prefixLength++) {
                byte[] prefix = Arrays.copyOf(frame, prefixLength);
                assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(prefix));
            }
        }
    }

    @Test
    public void studioHotloadOversizePackKeyThrowsIllegalState() {
        String oversized = "a".repeat(IrisProtocol.MAX_FRAME_BYTES);
        IrisMessage.StudioHotload hotload = new IrisMessage.StudioHotload(oversized, 0, false, "");
        assertThrows(IllegalStateException.class, () -> IrisMessageCodec.encode(hotload));
    }

    @Test
    public void poisonedStudioHotloadPackKeyLengthRejectsWithoutAllocatingClaimedSize() {
        IrisWireWriter writer = new IrisWireWriter();
        writer.writeVarInt(IrisProtocol.TYPE_STUDIO_HOTLOAD);
        writer.writeVarInt(Integer.MAX_VALUE);
        byte[] poisoned = writer.toByteArray();
        assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(poisoned));
    }

    @Test
    public void poisonedToastTitleLengthRejectsWithoutAllocatingClaimedSize() {
        IrisWireWriter writer = new IrisWireWriter();
        writer.writeVarInt(IrisProtocol.TYPE_TOAST);
        writer.writeInt(IrisMessage.Toast.KIND_INFO);
        writer.writeVarInt(Integer.MAX_VALUE);
        byte[] poisoned = writer.toByteArray();
        assertThrows(ProtocolException.class, () -> IrisMessageCodec.decode(poisoned));
    }
}
