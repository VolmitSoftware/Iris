package art.arcane.iris.world.history;

import art.arcane.iris.world.storage.region.MCAFile;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;

final class SavedTerrainTestRegion {
    private SavedTerrainTestRegion() {
    }

    static void write(Path file, int[][] chunks) throws IOException {
        write(file, chunks, "minecraft:full");
    }

    static void write(Path file, int[][] chunks, String status) throws IOException {
        String[] location = file.getFileName().toString().split("\\.");
        int regionX = Integer.parseInt(location[1]);
        int regionZ = Integer.parseInt(location[2]);
        try (RandomAccessFile output = new RandomAccessFile(file.toFile(), "rw")) {
            output.setLength(0L);
            output.setLength((long) (2 + chunks.length) * 4096);
            for (int index = 0; index < chunks.length; index++) {
                int localX = chunks[index][0] & 31;
                int localZ = chunks[index][1] & 31;
                byte[] chunk = chunk(regionX * 32 + localX, regionZ * 32 + localZ, status);
                int chunkIndex = MCAFile.getChunkIndex(localX, localZ);
                output.seek((long) chunkIndex * Integer.BYTES);
                output.writeInt((2 + index) << Byte.SIZE | 1);
                output.seek((long) (2 + index) * 4096);
                output.writeInt(chunk.length + 1);
                output.writeByte(3);
                output.write(chunk);
            }
        }
    }

    private static byte[] chunk(int x, int z, String status) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeUTF("");
            output.writeByte(3);
            output.writeUTF("xPos");
            output.writeInt(x);
            output.writeByte(3);
            output.writeUTF("zPos");
            output.writeInt(z);
            output.writeByte(8);
            output.writeUTF("Status");
            output.writeUTF(status);
            output.writeByte(9);
            output.writeUTF("sections");
            output.writeByte(10);
            output.writeInt(24);
            for (int section = -4; section < 20; section++) {
                output.writeByte(1);
                output.writeUTF("Y");
                output.writeByte(section);
                output.writeByte(10);
                output.writeUTF("block_states");
                output.writeByte(9);
                output.writeUTF("palette");
                output.writeByte(10);
                output.writeInt(1);
                output.writeByte(8);
                output.writeUTF("Name");
                output.writeUTF("minecraft:air");
                output.writeByte(0);
                output.writeByte(0);
                output.writeByte(10);
                output.writeUTF("biomes");
                output.writeByte(9);
                output.writeUTF("palette");
                output.writeByte(8);
                output.writeInt(1);
                output.writeUTF("minecraft:plains");
                output.writeByte(0);
                output.writeByte(0);
            }
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }
}
