package art.arcane.iris.world.history;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class GenerationBuildRevisionTest {
    @Test
    public void fingerprintUsesTheCompleteManifest() throws Exception {
        byte[] initial = revision("a".repeat(64));
        assertEquals(GenerationBuildRevision.fingerprint(1, "example.KernelV1", initial),
                GenerationBuildRevision.fingerprint(1, "example.KernelV1", initial));
        assertNotEquals(GenerationBuildRevision.fingerprint(1, "example.KernelV1", initial),
                GenerationBuildRevision.fingerprint(1, "example.KernelV1", revision("b".repeat(64))));
    }

    @Test
    public void rejectsMismatchedAbiFactoryAndMalformedHashes() {
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.fingerprint(2, "example.KernelV1", revision("a".repeat(64))));
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.fingerprint(1, "example.OtherKernel", revision("a".repeat(64))));
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.fingerprint(1, "example.KernelV1", revision("not-a-hash")));
        assertThrows(IOException.class,
                () -> GenerationBuildRevision.fingerprint(1, "example.KernelV1", new byte[0]));
    }

    @Test
    public void rejectsDuplicateAndUnsortedEntries() {
        String source = new String(revision("a".repeat(64)), StandardCharsets.UTF_8);
        String duplicate = source + "dependency\tmath\t" + "a".repeat(64) + "\n";
        assertThrows(IOException.class, () -> GenerationBuildRevision.fingerprint(1,
                "example.KernelV1", duplicate.getBytes(StandardCharsets.UTF_8)));
        String unordered = source + "scope\tother\n";
        assertThrows(IOException.class, () -> GenerationBuildRevision.fingerprint(1,
                "example.KernelV1", unordered.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void standardKernelUsesThePackagedBuildRevision() {
        assertEquals(GenerationBuildRevision.requireFingerprint(1, GenerationKernelV1.class),
                GenerationKernelV1.IMPLEMENTATION_FINGERPRINT);
    }

    private static byte[] revision(String fingerprint) {
        return ("iris-generation-build-revision-v1\nabi\t1\nfactory\texample.KernelV1\n"
                + "algorithm\t1\t1\nscope\tgeneration\nsource\tgeneration/Noise.java\t" + fingerprint
                + "\ndependency\tmath\t" + "c".repeat(64) + "\n").getBytes(StandardCharsets.UTF_8);
    }
}
