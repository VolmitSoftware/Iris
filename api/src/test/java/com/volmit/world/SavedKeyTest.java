package com.volmit.world;

import com.volmit.data.Codec;
import org.junit.jupiter.api.Test;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SavedKeyTest {
    private static final Codec<String> TEXT = new Codec<>() {
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
        for (String id : new String[]{"example:../book", "example:a/../b", "example:./book", "example:/book", "example:book/", "example:a//b"}) {
            assertThrows(IllegalArgumentException.class, () -> key(id), id);
        }
    }

    @Test
    void rejectsMissingPartsAndPrimitiveTypes() {
        assertThrows(NullPointerException.class, () -> new SavedKey<>(null, String.class, TEXT, () -> ""));
        assertThrows(NullPointerException.class, () -> new SavedKey<>("a:b", null, TEXT, () -> ""));
        assertThrows(NullPointerException.class, () -> new SavedKey<>("a:b", String.class, null, () -> ""));
        assertThrows(NullPointerException.class, () -> new SavedKey<>("a:b", String.class, TEXT, null));
        Codec<Integer> number = new Codec<>() {
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
        assertThrows(IllegalArgumentException.class, () -> new SavedKey<>("a:b", int.class, number, () -> 0));
    }

    private static SavedKey<String> key(String id) {
        return new SavedKey<>(id, String.class, TEXT, () -> "");
    }
}
