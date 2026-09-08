package art.arcane.iris.core.structure.authoring;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.MeteredCache;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

public class IrisDataStructureRecoveryTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisData data;

    @Before
    public void registerPreservationService() {
        IrisServices.register(PreservationRegistry.class, new NoOpPreservationRegistry());
    }

    @After
    public void closeDataAndServices() {
        if (data != null) {
            data.close();
        }
        IrisServices.remove(PreservationRegistry.class);
    }

    @Test
    public void packLoadingRecoversPreparedTransactionsBeforeCreatingLoaders() throws Exception {
        File packDirectory = temporaryFolder.newFolder("pack");
        Path packRoot = packDirectory.toPath();
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "replacement".getBytes(StandardCharsets.UTF_8);
        Path target = packRoot.resolve("objects/temple.iob");
        Files.createDirectories(target.getParent());
        Files.write(target, replacement);
        UUID transactionId = UUID.randomUUID();
        Path transactionRoot = packRoot.resolve(".iris/structure-staging").resolve(transactionId.toString());
        Path backup = transactionRoot.resolve("backup/objects/temple.iob");
        Files.createDirectories(backup.getParent());
        Files.write(backup, original);
        StructureTransactionJournal journal = StructureTransactionJournal.prepared(transactionId, List.of(
                new StructureTransactionJournal.Target(
                        "objects/temple.iob",
                        true,
                        StructureHash.sha256(original),
                        StructureHash.sha256(replacement)
                )
        ));
        Files.write(transactionRoot.resolve(StructureTransactionJournal.FILE_NAME), journal.toJson());

        data = IrisData.get(packDirectory);

        assertArrayEquals(original, Files.readAllBytes(target));
        assertFalse(Files.exists(transactionRoot));
    }

    private static final class NoOpPreservationRegistry implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }
}
