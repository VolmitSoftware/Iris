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

package art.arcane.iris.structure.graph;

import art.arcane.iris.structure.authoring.StructureBackend;
import art.arcane.iris.structure.authoring.StructureKey;
import art.arcane.iris.structure.authoring.StructureResourceBundle;
import art.arcane.iris.structure.authoring.StructureSource;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StructureResourceBundleGraphCompilerTest {
    @Test
    public void completeBundleCompilesAndPassesRuntimeGeometry() throws IOException {
        StructureResourceBundle bundle = baseBundle()
                .resource("objects/start.iob", object(1, 1, 1))
                .textResource("jigsaw-pieces/start.json",
                        "{\"object\":\"start\",\"connectors\":[],\"rotatable\":false}")
                .textResource("jigsaw-pools/start.json",
                        "{\"pieces\":[{\"piece\":\"start\",\"weight\":1}]}")
                .textResource("structures/test.json",
                        "{\"startPool\":\"start\",\"maxDepth\":1,\"maxSizeChunks\":1}")
                .build();

        StructureResourceBundleGraphCompiler.requireViable(bundle);

        assertEquals(1, StructureResourceBundleGraphCompiler.compile(bundle).size());
    }

    @Test
    public void topologicalGraphThatCannotFitItsRadiusIsRejectedBeforePublish() throws IOException {
        String sourceConnector = "{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                + "\"direction\":\"EAST_POSITIVE_X\",\"top\":\"UP_POSITIVE_Y\","
                + "\"pool\":\"target\",\"name\":\"source\",\"targetName\":\"door\","
                + "\"joint\":\"ROLLABLE\"}";
        String targetConnector = "{\"position\":{\"x\":50,\"y\":0,\"z\":0},"
                + "\"direction\":\"WEST_NEGATIVE_X\",\"top\":\"UP_POSITIVE_Y\","
                + "\"pool\":\"target\",\"name\":\"door\",\"targetName\":\"unused\","
                + "\"joint\":\"ROLLABLE\"}";
        StructureResourceBundle bundle = baseBundle()
                .resource("objects/start.iob", object(1, 1, 1))
                .resource("objects/target.iob", object(100, 1, 1))
                .textResource("jigsaw-pieces/start.json",
                        "{\"object\":\"start\",\"connectors\":[" + sourceConnector + "],\"rotatable\":false}")
                .textResource("jigsaw-pieces/target.json",
                        "{\"object\":\"target\",\"connectors\":[" + targetConnector + "],\"rotatable\":false}")
                .textResource("jigsaw-pools/start.json",
                        "{\"pieces\":[{\"piece\":\"start\",\"weight\":1}]}")
                .textResource("jigsaw-pools/target.json",
                        "{\"pieces\":[{\"piece\":\"target\",\"weight\":1}]}")
                .textResource("structures/test.json",
                        "{\"startPool\":\"start\",\"maxDepth\":1,\"maxSizeChunks\":1}")
                .build();

        assertThrows(StructureGraphValidationException.class,
                () -> StructureResourceBundleGraphCompiler.requireViable(bundle));
    }

    @Test
    public void intentionalEmptyStartIsAValidNoOpGraph() {
        StructureResourceBundle bundle = baseBundle()
                .textResource("jigsaw-pools/start.json",
                        "{\"pieces\":[{\"empty\":true,\"weight\":1}]}")
                .textResource("structures/test.json",
                        "{\"startPool\":\"start\",\"maxDepth\":1,\"maxSizeChunks\":1}")
                .build();

        StructureResourceBundleGraphCompiler.requireViable(bundle);
    }

    @Test
    public void sampledFailureReportsItsSeedStatusAndRuntimeDetailInsteadOfAWarning() throws IOException {
        String sourceConnector = "{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                + "\"direction\":\"EAST_POSITIVE_X\",\"top\":\"UP_POSITIVE_Y\","
                + "\"pool\":\"target\",\"name\":\"source\",\"targetName\":\"door\","
                + "\"joint\":\"ROLLABLE\"}";
        StructureResourceBundle bundle = baseBundle()
                .resource("objects/start.iob", object(1, 1, 1))
                .resource("objects/orphan.iob", object(1, 1, 1))
                .textResource("jigsaw-pieces/start.json",
                        "{\"object\":\"start\",\"connectors\":[" + sourceConnector
                                + "],\"rotatable\":false}")
                .textResource("jigsaw-pieces/orphan.json",
                        "{\"object\":\"orphan\",\"connectors\":[],\"rotatable\":false}")
                .textResource("jigsaw-pools/start.json",
                        "{\"pieces\":[{\"piece\":\"start\",\"weight\":1}]}")
                .textResource("jigsaw-pools/target.json",
                        "{\"pieces\":[{\"piece\":\"orphan\",\"weight\":1}]}")
                .textResource("structures/test.json",
                        "{\"startPool\":\"start\",\"maxDepth\":2,\"maxSizeChunks\":1}")
                .build();

        StructureGraphValidationException failure = assertThrows(
                StructureGraphValidationException.class,
                () -> StructureResourceBundleGraphCompiler.requireViable(bundle));

        assertTrue(failure.getMessage(), failure.getMessage().contains(
                "sampled assembly at seed 0 returned FAILED_UNCAPPED"));
        assertTrue(failure.getMessage(), failure.getMessage().contains(
                "Connector pool 'target' could not place a piece and has no direct fallback"));
    }

    @Test
    public void branchTerminationPolicyAcceptsTheSameUnmatchedOptionalBranch() throws IOException {
        String sourceConnector = "{\"position\":{\"x\":0,\"y\":0,\"z\":0},"
                + "\"direction\":\"EAST_POSITIVE_X\",\"top\":\"UP_POSITIVE_Y\","
                + "\"pool\":\"target\",\"name\":\"source\",\"targetName\":\"door\","
                + "\"joint\":\"ROLLABLE\"}";
        StructureResourceBundle bundle = baseBundle()
                .resource("objects/start.iob", object(1, 1, 1))
                .resource("objects/orphan.iob", object(1, 1, 1))
                .textResource("jigsaw-pieces/start.json",
                        "{\"object\":\"start\",\"connectors\":[" + sourceConnector
                                + "],\"rotatable\":false}")
                .textResource("jigsaw-pieces/orphan.json",
                        "{\"object\":\"orphan\",\"connectors\":[],\"rotatable\":false}")
                .textResource("jigsaw-pools/start.json",
                        "{\"pieces\":[{\"piece\":\"start\",\"weight\":1}]}")
                .textResource("jigsaw-pools/target.json",
                        "{\"pieces\":[{\"piece\":\"orphan\",\"weight\":1}]}")
                .textResource("structures/test.json",
                        "{\"startPool\":\"start\",\"maxDepth\":2,\"maxSizeChunks\":1,"
                                + "\"branchFailurePolicy\":\"TERMINATE_BRANCH\"}")
                .build();

        StructureResourceBundleGraphCompiler.requireViable(bundle);

        StructureGraphCompilation compilation = StructureResourceBundleGraphCompiler.compile(bundle).getFirst();
        assertTrue(compilation.isAssemblyViable());
        assertTrue(compilation.getAssemblySamples().stream()
                .allMatch(sample -> sample.outcome().status() == StructureAssemblyStatus.COMPLETE));
    }

    @Test
    public void structuralErrorDiagnosticRemainsAuthoritative() {
        StructureResourceBundle bundle = baseBundle()
                .textResource("jigsaw-pieces/start.json",
                        "{\"object\":\"missing\",\"connectors\":[],\"rotatable\":false}")
                .textResource("jigsaw-pools/start.json",
                        "{\"pieces\":[{\"piece\":\"start\",\"weight\":1}]}")
                .textResource("structures/test.json",
                        "{\"startPool\":\"start\",\"maxDepth\":1,\"maxSizeChunks\":1}")
                .build();

        StructureGraphValidationException failure = assertThrows(
                StructureGraphValidationException.class,
                () -> StructureResourceBundleGraphCompiler.requireViable(bundle));

        assertTrue(failure.getMessage(), failure.getMessage().contains(
                "Jigsaw piece 'start' references missing object 'missing'"));
    }

    @Test
    public void truncatedObjectFrameIsRejectedBeforePublish() throws IOException {
        StructureResourceBundle bundle = baseBundle()
                .resource("objects/start.iob", truncatedObject(1, 1, 1))
                .textResource("jigsaw-pieces/start.json",
                        "{\"object\":\"start\",\"connectors\":[],\"rotatable\":false}")
                .textResource("jigsaw-pools/start.json",
                        "{\"pieces\":[{\"piece\":\"start\",\"weight\":1}]}")
                .textResource("structures/test.json",
                        "{\"startPool\":\"start\",\"maxDepth\":1,\"maxSizeChunks\":1}")
                .build();

        assertThrows(StructureGraphValidationException.class,
                () -> StructureResourceBundleGraphCompiler.requireViable(bundle));
    }

    private StructureResourceBundle.Builder baseBundle() {
        StructureKey key = StructureKey.parse("iris:test");
        return StructureResourceBundle.builder(key)
                .source(StructureSource.of(StructureSource.Kind.IRIS, key))
                .backend(StructureBackend.IRIS_ASSEMBLY);
    }

    private byte[] object(int width, int height, int depth) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(depth);
            output.writeUTF("Iris V2 IOB;");
            output.writeShort(0);
            output.writeInt(0);
            output.writeInt(0);
        }
        return bytes.toByteArray();
    }

    private byte[] truncatedObject(int width, int height, int depth) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(width);
            output.writeInt(height);
            output.writeInt(depth);
            output.writeUTF("Iris V2 IOB;");
        }
        return bytes.toByteArray();
    }
}
