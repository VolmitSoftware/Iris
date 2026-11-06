package com.volmit.data;

import com.volmit.resource.ResourceId;
import org.junit.jupiter.api.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedKeyTest {
    private static final ResourceId ID = ResourceId.parse("a:b");

    private static final StorageCodec<String> TEXT = new StorageCodec<>() {
        @Override
        public int version() {
            return 1;
        }

        @Override
        public void write(DataOutput output, String value) throws IOException {
            output.writeUTF(value);
        }

        @Override
        public String read(DataInput input, int storedVersion) throws IOException {
            return input.readUTF();
        }
    };

    @Test
    void acceptsNamespacedIdsWithNestedPaths() {
        assertDoesNotThrow(() -> key("example:spawn/cooldowns"));
        assertDoesNotThrow(() -> key("example:book.v2"));
    }

    @Test
    void rejectsIdsThatAreNotNamespaced() {
        for (String id : new String[]{"", "book", ":book", "example:", "Example:book", "a:b:c"}) {
            assertThrows(IllegalArgumentException.class, () -> key(id));
        }
    }

    @Test
    void rejectsIdsThatCouldLeaveTheWorldsStorage() {
        for (String id : new String[]{"example:../book", "example:a/../b", "example:./book", "example:/book", "example:book/", "example:a//b", "..:book", ".:book"}) {
            assertThrows(IllegalArgumentException.class, () -> key(id), id);
        }
    }

    @Test
    void rejectsMissingPartsAndPrimitiveTypes() {
        assertThrows(NullPointerException.class, () -> new SavedKey<>(null, String.class, TEXT, () -> ""));
        assertThrows(NullPointerException.class, () -> new SavedKey<>(ID, null, TEXT, () -> ""));
        assertThrows(NullPointerException.class, () -> new SavedKey<>(ID, String.class, null, () -> ""));
        assertThrows(NullPointerException.class, () -> new SavedKey<>(ID, String.class, TEXT, null));
        StorageCodec<Integer> number = new StorageCodec<>() {
            @Override
            public int version() {
                return 1;
            }

            @Override
            public void write(DataOutput output, Integer value) throws IOException {
                output.writeInt(value);
            }

            @Override
            public Integer read(DataInput input, int storedVersion) throws IOException {
                return input.readInt();
            }
        };
        assertThrows(IllegalArgumentException.class, () -> new SavedKey<>(ID, int.class, number, () -> 0));
    }

    private static SavedKey<String> key(String id) {
        return new SavedKey<>(ResourceId.parse(id), String.class, TEXT, () -> "");
    }
}
