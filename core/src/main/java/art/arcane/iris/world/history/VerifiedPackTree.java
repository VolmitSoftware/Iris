package art.arcane.iris.world.history;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * A pack tree together with the fingerprint this process computed from it. Only {@link #hash(Path)} creates one,
 * so a holder can hand the tree to the epoch repository without it being hashed again.
 */
public final class VerifiedPackTree {
    private final Path root;
    private final String fingerprint;
    private final int fingerprintVersion;

    private VerifiedPackTree(Path root, String fingerprint, int fingerprintVersion) {
        this.root = root;
        this.fingerprint = fingerprint;
        this.fingerprintVersion = fingerprintVersion;
    }

    public static VerifiedPackTree hash(Path root) throws IOException {
        Path normalizedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        int version = GenerationPackFingerprint.CURRENT_VERSION;
        return new VerifiedPackTree(normalizedRoot, GenerationPackFingerprint.compute(normalizedRoot, version), version);
    }

    public Path root() {
        return root;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public int fingerprintVersion() {
        return fingerprintVersion;
    }
}
