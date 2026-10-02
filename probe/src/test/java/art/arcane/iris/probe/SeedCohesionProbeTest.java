package art.arcane.iris.probe;

import art.arcane.iris.testsupport.IrisRuntimeState;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

public final class SeedCohesionProbeTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @AfterClass
    public static void releaseRuntime() {
        IrisRuntimeState.reset();
    }

    @Test
    public void linkedPackInputsAreCopiedIntoAnIndependentWorkspace() throws Exception {
        URL resource = SeedCohesionProbeTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        Path source = Path.of(resource.toURI());
        Path link = temporary.getRoot().toPath().resolve("linked-pack");
        Files.createSymbolicLink(link, source);
        try (RealPackProbeSupport.Workspace workspace = RealPackProbeSupport.openWorkspace(
                link.toFile(), "cohesion", "[seed-cohesion-copy]")) {
            Path copy = workspace.pack().toPath();
            assertFalse(Files.isSymbolicLink(copy));
            assertFalse(Files.isSameFile(source, copy));
            assertEquals(Files.readString(source.resolve("dimensions/cohesion.json")),
                    Files.readString(copy.resolve("dimensions/cohesion.json")));
        }
    }

    @Test(timeout = 180_000L)
    public void completeChunksRemainStableAcrossSeedOrderThreadsAndRestarts() throws Exception {
        GenerationOrderProbe.AggregateSignature first = generate(1337L, false);
        GenerationOrderProbe.AggregateSignature parallel = generate(1337L, true);
        GenerationOrderProbe.AggregateSignature alternate = generate(-8675309L, true);
        GenerationOrderProbe.AggregateSignature restarted = generate(1337L, true);
        assertEquals(first, parallel);
        assertEquals(first, restarted);
        assertNotEquals(first.blocks(), alternate.blocks());
        assertEquals(new GenerationOrderProbe.AggregateSignature(
                "3ddfe89b98273ec609574918ac461bdd0e15365a2840a8ab73285818981feb74",
                "0f54e6504dc0b66b3c0d9b9cc4406a07a63d14e5eba4c30db7c3d7f74a5d61c7",
                "9bcf2d354c6f17849046153c25beeb3ce08b81d3409037d2968f28d30d832253"), first);
        assertEquals(new GenerationOrderProbe.AggregateSignature(
                "0ad13dd2ea0d3eb467f1fded74403f7efb5245a3a06799b39c1d1d17dd32b926",
                "0f54e6504dc0b66b3c0d9b9cc4406a07a63d14e5eba4c30db7c3d7f74a5d61c7",
                "e4f6fb0c86cae9be76a1e957156e42a762b4d75458fb1483fe856bda1cfd3a1e"), alternate);
    }

    private static GenerationOrderProbe.AggregateSignature generate(long seed, boolean multicore) throws Exception {
        URL resource = SeedCohesionProbeTest.class.getResource("/seed-cohesion");
        assertNotNull(resource);
        File pack = new File(resource.toURI());
        GenerationOrderProbe.ProbeConfiguration configuration = new GenerationOrderProbe.ProbeConfiguration(
                pack, "cohesion", seed, -1, 1, -1, 1, 4, 91L, multicore, false);
        GenerationOrderProbe.ProbeResult result = GenerationOrderProbe.run(configuration);
        assertEquals("PASS", result.status());
        return result.signature();
    }
}
