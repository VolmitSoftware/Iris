package art.arcane.iris.generation.runtime;

import art.arcane.iris.pack.PackValidationCache;
import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.world.history.GenerationKernelRegistry;
import art.arcane.iris.world.history.GenerationPackFingerprint;
import art.arcane.iris.world.history.TransitionGenerationPlan;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

record PreparedHydrologyCacheIdentity(String fingerprint) {
    static PreparedHydrologyCacheIdentity capture(
            EngineTarget target,
            GenerationKernelRegistry.RuntimeKernel kernel,
            TransitionGenerationPlan transition,
            boolean studio
    ) {
        if (!IrisPlatforms.isBound()) {
            return null;
        }
        try {
            String context = PackValidationCache.contextFingerprint();
            if (context.isBlank()) {
                return null;
            }
            String content = GenerationPackFingerprint.compute(
                    target.getData().getDataFolder().toPath(), GenerationPackFingerprint.CURRENT_VERSION);
            String dependencies = target.getData().hasGenerationRegistryContract() ? "closed"
                    : dependencyFingerprint(IrisPlatforms.get().packsFolderNoCreate());
            if (dependencies.isBlank()) {
                return null;
            }
            int buffetSize = studio && "bukkit".equals(IrisPlatforms.get().platformName())
                    ? target.getDimension().getStudioMode().biomeSizeChunks() : 0;
            return fromInputs(content, dependencies, context, kernel, transition == null ? null : transition.specification(),
                    target.getWorld().minHeight(), target.getWorld().maxHeight(), buffetSize);
        } catch (IOException | RuntimeException failure) {
            IrisLogging.reportError("Prepared hydrology cache identity could not be captured.", failure);
            return null;
        }
    }

    static PreparedHydrologyCacheIdentity fromInputs(
            String packContent,
            String dependencies,
            String platformContext,
            GenerationKernelRegistry.RuntimeKernel kernel,
            TransitionGenerationPlan.Specification transition,
            int minimumHeight,
            int maximumHeight,
            int biomeBuffetSize
    ) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, packContent);
            update(digest, dependencies);
            update(digest, platformContext);
            update(digest, kernel.version().toString());
            update(digest, kernel.implementationFingerprint());
            update(digest, Integer.toString(minimumHeight));
            update(digest, Integer.toString(maximumHeight));
            update(digest, Integer.toString(biomeBuffetSize));
            update(digest, Boolean.toString(transition != null));
            if (transition != null) {
                update(digest, Long.toString(transition.activationId()));
                update(digest, transition.oldEpochId());
                update(digest, transition.newEpochId());
                update(digest, Integer.toString(transition.algorithmVersion()));
                update(digest, Integer.toString(transition.widthBlocks()));
                update(digest, transition.boundaryIdentity());
                update(digest, transition.terrainSignatureIdentity());
            }
            return new PreparedHydrologyCacheIdentity(HexFormat.of().formatHex(digest.digest()));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable.", failure);
        }
    }

    void enableIfUnchanged(EngineTarget target, GenerationKernelRegistry.RuntimeKernel kernel,
                           TransitionGenerationPlan transition, IrisComplex complex, boolean studio) {
        if (!equals(capture(target, kernel, transition, studio))) {
            IrisLogging.warn("Generation inputs changed during compilation; prepared hydrology caching is disabled.");
            return;
        }
        complex.enablePreparedHydrologyCache(fingerprint,
                IrisPlatforms.get().dataFolderNoCreate("cache", "hydrology-plans").toPath());
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String dependencyFingerprint(File packsRoot) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (File pack : PackDirectoryResolver.listVisiblePackDirectoriesOrThrow(packsRoot)) {
                update(digest, pack.getName());
                update(digest, GenerationPackFingerprint.compute(
                        pack.toPath().toRealPath(), GenerationPackFingerprint.CURRENT_VERSION));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable.", failure);
        }
    }
}
