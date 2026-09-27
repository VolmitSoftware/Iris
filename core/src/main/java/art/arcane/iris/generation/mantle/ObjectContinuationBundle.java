package art.arcane.iris.generation.mantle;

import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.IrisMatter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.io.CountingDataInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import art.arcane.iris.world.storage.matter.IrisMatterSupport;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

public record ObjectContinuationBundle(List<Fragment> fragments) {
    private static final int MAX_ENTRIES = 1_048_576;
    private static final int MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;

    public ObjectContinuationBundle {
        fragments = List.copyOf(fragments);
    }

    public ObjectContinuationBundle with(Fragment fragment) {
        LinkedHashMap<PlacementKey, Fragment> merged = new LinkedHashMap<>();
        for (Fragment existing : fragments) {
            merged.put(existing.key(), existing);
        }
        merged.put(fragment.key(), fragment);
        return new ObjectContinuationBundle(new ArrayList<>(merged.values()));
    }

    public void write(DataOutputStream output) throws IOException {
        output.writeInt(fragments.size());
        for (Fragment fragment : fragments) {
            output.writeUTF(fragment.key().kind().name());
            output.writeInt(fragment.key().sourceChunkX());
            output.writeInt(fragment.key().sourceChunkZ());
            output.writeInt(fragment.key().ordinal());
            output.writeInt(fragment.bounds().minimumX());
            output.writeInt(fragment.bounds().minimumZ());
            output.writeInt(fragment.bounds().maximumX());
            output.writeInt(fragment.bounds().maximumZ());
            output.writeInt(fragment.touchedChunks().size());
            for (ChunkPosition chunk : fragment.touchedChunks()) {
                output.writeInt(chunk.x());
                output.writeInt(chunk.z());
            }
            output.writeInt(fragment.payload.length);
            output.write(fragment.payload);
        }
    }

    public static ObjectContinuationBundle read(DataInputStream input) throws IOException {
        int count = checkedCount(input.readInt(), MAX_ENTRIES);
        ArrayList<Fragment> fragments = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            PlacementKey key = new PlacementKey(Kind.valueOf(input.readUTF()), input.readInt(), input.readInt(), input.readInt());
            Bounds bounds = new Bounds(input.readInt(), input.readInt(), input.readInt(), input.readInt());
            int touchedCount = checkedCount(input.readInt(), MAX_ENTRIES);
            ArrayList<ChunkPosition> touched = new ArrayList<>(touchedCount);
            for (int chunk = 0; chunk < touchedCount; chunk++) {
                touched.add(new ChunkPosition(input.readInt(), input.readInt()));
            }
            int length = checkedCount(input.readInt(), MAX_PAYLOAD_BYTES);
            byte[] payload = new byte[length];
            input.readFully(payload);
            fragments.add(new Fragment(key, bounds, touched, payload));
        }
        return new ObjectContinuationBundle(fragments);
    }

    public static byte[] encode(Matter payload) {
        IrisMatterSupport.ensureRegistered();
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            payload.write(output);
            return output.toByteArray();
        } catch (IOException failure) {
            throw new UncheckedIOException("Unable to encode object continuation", failure);
        }
    }

    private static int checkedCount(int count, int maximum) throws IOException {
        if (count < 0 || count > maximum) {
            throw new IOException("Invalid object continuation size: " + count);
        }
        return count;
    }

    public enum Kind {
        BIOME, FLOATING, STRUCTURE, STATIC
    }

    public record PlacementKey(Kind kind, int sourceChunkX, int sourceChunkZ, int ordinal) {
        public PlacementKey {
            Objects.requireNonNull(kind, "placement kind");
            if (ordinal < 0) {
                throw new IllegalArgumentException("Placement ordinal must be nonnegative");
            }
        }
    }

    public record ChunkPosition(int x, int z) {
    }

    public record Bounds(int minimumX, int minimumZ, int maximumX, int maximumZ) {
        public Bounds {
            if (minimumX > maximumX || minimumZ > maximumZ) {
                throw new IllegalArgumentException("Object continuation bounds are inverted");
            }
        }
    }

    public record Fragment(PlacementKey key, Bounds bounds, List<ChunkPosition> touchedChunks, byte[] payload) {
        public Fragment {
            Objects.requireNonNull(key, "placement key");
            Objects.requireNonNull(bounds, "placement bounds");
            touchedChunks = List.copyOf(touchedChunks);
            if (touchedChunks.size() < 2 || payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Object continuation must span chunks with a bounded payload");
            }
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }

        public int encodedSize() {
            return payload.length;
        }

        public Matter decode() throws IOException {
            try (CountingDataInputStream input = CountingDataInputStream.wrap(new ByteArrayInputStream(payload))) {
                int width = input.readInt();
                int height = input.readInt();
                int depth = input.readInt();
                if (width != 16 || depth != 16 || height < 1 || height > 4096) {
                    throw new IOException("Invalid object continuation dimensions");
                }
                Matter matter = new IrisMatter(width, height, depth);
                int sliceCount = input.readUnsignedByte();
                matter.getHeader().read(input);
                for (int index = 0; index < sliceCount; index++) {
                    int size = input.readInt();
                    long end = input.count() + size;
                    if (size <= 0 || end > payload.length) {
                        throw new IOException("Invalid object continuation slice size");
                    }
                    String identifier = input.readUTF();
                    Class<?> type = IrisMatter.getSliceType(identifier);
                    if (type == null || matter.hasSlice(type)) {
                        throw new IOException("Unavailable or duplicate object continuation slice: " + identifier);
                    }
                    MatterSlice<?> slice = matter.createSlice(type, matter);
                    if (slice == null) {
                        throw new IOException("Unavailable object continuation codec: " + identifier);
                    }
                    slice.read(input);
                    if (input.count() != end) {
                        throw new IOException("Object continuation slice size mismatch: " + identifier);
                    }
                    matter.putSlice(type, slice);
                }
                if (input.count() != payload.length) {
                    throw new IOException("Trailing object continuation bytes");
                }
                return matter;
            }
        }
    }
}
