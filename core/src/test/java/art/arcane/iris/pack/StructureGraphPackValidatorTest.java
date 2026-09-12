/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.pack;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StructureGraphPackValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void reportsCompilerOnlyWeightAndConnectorBoundsFailures() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\",\"weight\":0}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[{\"position\":{\"x\":2,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"castle/start\",\"name\":\"path\",\"targetName\":\"path\"}]}");
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().stream().anyMatch(message -> message.contains("non-positive weight 0")));
        assertTrue(validation.errors().stream().anyMatch(message -> message.contains("outside object bounds")));
    }

    @Test
    public void reportsFallbackCyclesBeforeRuntime() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/a\"}");
        write(pack, "jigsaw-pools/castle/a.json",
                "{\"pieces\":[{\"piece\":\"castle/piece\"}],\"fallback\":\"castle/b\"}");
        write(pack, "jigsaw-pools/castle/b.json",
                "{\"pieces\":[{\"piece\":\"castle/piece\"}],\"fallback\":\"castle/a\"}");
        write(pack, "jigsaw-pieces/castle/piece.json",
                "{\"object\":\"castle/piece\",\"connectors\":[]}");
        writeObjectHeader(pack.resolve("objects/castle/piece.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().stream().anyMatch(message -> message.contains("fallback cycle")));
    }

    @Test
    public void blocksGraphsThatCanOnlyProducePartialAssemblies() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\",\"maxDepth\":2}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pools/castle/target.json",
                "{\"pieces\":[{\"piece\":\"castle/target\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"castle/target\",\"name\":\"source\",\"targetName\":\"door\"}]}");
        write(pack, "jigsaw-pieces/castle/target.json",
                "{\"object\":\"castle/target\",\"rotatable\":false,\"connectors\":[{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"castle/target\",\"name\":\"door\",\"targetName\":\"unused\"}]}");
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 1, 1, 1);
        writeObjectHeader(pack.resolve("objects/castle/target.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().stream().anyMatch(
                message -> message.contains("does not produce a complete deterministic assembly")));
    }

    @Test
    public void branchTerminationPolicyAcceptsTheSameOptionalPartialGraph() throws Exception {
        Path pack = temporaryFolder.newFolder("terminating-pack").toPath();
        write(pack, "structures/castle.json",
                "{\"startPool\":\"castle/start\",\"maxDepth\":2,"
                        + "\"branchFailurePolicy\":\"TERMINATE_BRANCH\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pools/castle/target.json",
                "{\"pieces\":[{\"piece\":\"castle/target\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"castle/target\","
                        + "\"name\":\"source\",\"targetName\":\"door\"}]}");
        write(pack, "jigsaw-pieces/castle/target.json",
                "{\"object\":\"castle/target\",\"rotatable\":false,\"connectors\":[{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"castle/target\","
                        + "\"name\":\"door\",\"targetName\":\"unused\"}]}");
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 1, 1, 1);
        writeObjectHeader(pack.resolve("objects/castle/target.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
    }

    @Test
    public void inactiveLibraryGraphDoesNotEnterTheRuntimeGate() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/library.json", "{\"startPool\":\"library/start\",\"maxDepth\":2}");
        write(pack, "jigsaw-pools/library/start.json",
                "{\"pieces\":[{\"piece\":\"library/start\"}]}");
        write(pack, "jigsaw-pools/library/target.json",
                "{\"pieces\":[{\"piece\":\"library/target\"}]}");
        write(pack, "jigsaw-pieces/library/start.json",
                "{\"object\":\"library/start\",\"connectors\":[{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"library/target\",\"name\":\"source\",\"targetName\":\"door\"}]}");
        write(pack, "jigsaw-pieces/library/target.json",
                "{\"object\":\"library/target\",\"rotatable\":false,\"connectors\":[{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"library/target\",\"name\":\"door\",\"targetName\":\"unused\"}]}");
        writeObjectHeader(pack.resolve("objects/library/start.iob"), 1, 1, 1);
        writeObjectHeader(pack.resolve("objects/library/target.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation =
                StructureGraphPackValidator.validate(pack, Set.of());

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertTrue(validation.warnings().toString(), validation.warnings().isEmpty());
    }

    @Test
    public void rejectsTruncatedObjectsBeforeRuntime() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[]}");
        Path object = pack.resolve("objects/castle/start.iob");
        writeTruncatedObjectHeader(object, 1, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertEquals(List.of("Malformed Iris object resource " + object
                + ": EOFException"), validation.errors());
    }

    @Test
    public void doesNotReadMalformedObjectsOutsideTheActiveGraph() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/library.json", "{\"startPool\":\"library/start\"}");
        write(pack, "jigsaw-pools/library/start.json",
                "{\"pieces\":[{\"piece\":\"library/start\"}]}");
        write(pack, "jigsaw-pieces/library/start.json",
                "{\"object\":\"library/start\",\"connectors\":[]}");
        writeTruncatedObjectHeader(pack.resolve("objects/library/start.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation =
                StructureGraphPackValidator.validate(pack, Set.of());

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
    }

    @Test
    public void acceptsSignedObjectCoordinates() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[]}");
        writeSignedObject(pack.resolve("objects/castle/start.iob"), 18, 31, 41, -9, -15, -20);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertFalse(validation.errors().toString(),
                validation.errors().stream().anyMatch(message -> message.contains("missing object")));
        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
    }

    @Test
    public void capturesSampledVerticalEnvelopesFromRuntimeAssembly() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[]}");
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 3, 9, 3);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);
        List<StructureGraphPackValidator.SampledVerticalEnvelope> envelopes =
                validation.sampledVerticalEnvelopes().get("castle");

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertEquals(16, envelopes.size());
        assertTrue(envelopes.stream().allMatch(envelope -> envelope.pieceCount() == 1
                && envelope.minimumYOffset() == -4
                && envelope.maximumYOffset() == 4));
        assertThrows(UnsupportedOperationException.class,
                () -> envelopes.add(new StructureGraphPackValidator.SampledVerticalEnvelope(1L, 1, 0, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> validation.sampledVerticalEnvelopes().put("other", List.of()));
    }

    @Test
    public void treatsAnEmptyTargetPoolAsIntentionalTermination() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pools/castle/empty.json", "{\"pieces\":[]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"direction\":\"NORTH_NEGATIVE_Z\",\"pool\":\"castle/empty\","
                        + "\"name\":\"source\",\"targetName\":\"door\"}]}" );
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
    }

    @Test
    public void acceptsIntentionalEmptyStartButDoesNotApproveItAsAReplacement() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"empty\":true,\"weight\":1}]}");

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertFalse(validation.replacementOutputStructures().contains("castle"));
    }

    @Test
    public void rareWeightedEmptyStartCannotApproveNativeReplacement() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json", "{\"startPool\":\"castle/start\"}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\",\"weight\":2147483647},"
                        + "{\"empty\":true,\"weight\":1}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[]}");
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 1, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertFalse(validation.replacementOutputStructures().contains("castle"));
    }

    @Test
    public void oversizedStartCannotApproveNativeReplacement() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        write(pack, "structures/castle.json",
                "{\"startPool\":\"castle/start\",\"maxSizeChunks\":1}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\"}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"connectors\":[]}");
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 34, 1, 1);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().isEmpty());
        assertFalse(validation.replacementOutputStructures().contains("castle"));
    }

    @Test
    public void reportsAuthoritativeGeometryFailureWithoutEscapingValidation() throws Exception {
        Path pack = temporaryFolder.newFolder("pack").toPath();
        String source = "{\"position\":{\"x\":1,\"y\":1,\"z\":0},"
                + "\"direction\":\"NORTH_NEGATIVE_Z\",\"top\":\"UP_POSITIVE_Y\","
                + "\"pool\":\"castle/recursive\",\"name\":\"source\",\"targetName\":\"door\","
                + "\"joint\":\"ROLLABLE\"}";
        String target = "{\"position\":{\"x\":1,\"y\":1,\"z\":2},"
                + "\"direction\":\"SOUTH_POSITIVE_Z\",\"top\":\"UP_POSITIVE_Y\","
                + "\"pool\":\"castle/recursive\",\"name\":\"door\",\"targetName\":\"unused\","
                + "\"joint\":\"ROLLABLE\"}";
        write(pack, "structures/castle.json",
                "{\"startPool\":\"castle/start\",\"maxDepth\":30,\"maxSizeChunks\":32}");
        write(pack, "jigsaw-pools/castle/start.json",
                "{\"pieces\":[{\"piece\":\"castle/start\",\"weight\":1}]}");
        write(pack, "jigsaw-pools/castle/recursive.json",
                "{\"pieces\":[{\"piece\":\"castle/recursive\",\"weight\":1}]}");
        write(pack, "jigsaw-pieces/castle/start.json",
                "{\"object\":\"castle/start\",\"rotatable\":false,\"connectors\":[" + source + "]}");
        write(pack, "jigsaw-pieces/castle/recursive.json",
                "{\"object\":\"castle/recursive\",\"rotatable\":false,\"connectors\":["
                        + target + "," + source + "," + source + "]}");
        writeObjectHeader(pack.resolve("objects/castle/start.iob"), 3, 3, 3);
        writeObjectHeader(pack.resolve("objects/castle/recursive.iob"), 3, 3, 3);

        StructureGraphPackValidator.Validation validation = StructureGraphPackValidator.validate(pack);

        assertTrue(validation.errors().toString(), validation.errors().stream().anyMatch(
                message -> message.contains("does not produce a complete deterministic assembly")
                        && message.contains("seed 0")));
    }

    private void write(Path root, String relativePath, String content) throws Exception {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void writeObjectHeader(Path file, int width, int height, int depth) throws Exception {
        Files.createDirectories(file.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(depth);
            output.writeUTF("Iris V2 IOB;");
            output.writeShort(0);
            output.writeInt(0);
            output.writeInt(0);
        }
    }

    private void writeTruncatedObjectHeader(Path file, int width, int height, int depth) throws Exception {
        Files.createDirectories(file.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(depth);
        }
    }

    private void writeSignedObject(
            Path file,
            int width,
            int height,
            int depth,
            int x,
            int y,
            int z
    ) throws Exception {
        Files.createDirectories(file.getParent());
        try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(file))) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(depth);
            output.writeUTF("Iris V2 IOB;");
            output.writeShort(1);
            output.writeUTF("minecraft:stone");
            output.writeInt(1);
            output.writeShort(x);
            output.writeShort(y);
            output.writeShort(z);
            output.writeShort(0);
            output.writeInt(0);
        }
    }
}
