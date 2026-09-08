package art.arcane.iris.core.pack;

import art.arcane.iris.core.lifecycle.BukkitStartupPaths;
import art.arcane.iris.engine.history.GenerationRegistryContractFactory;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.testsupport.DurabilityMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DefaultPackBootstrapProvisionerTest {
    @ClassRule
    public static final DurabilityMode DURABILITY = DurabilityMode.relaxed();

    @Test
    public void startupDoesNotRequireOrDownloadDefaultPacks() {
        assertTrue(DefaultPackBootstrapProvisioner.defaultPacks().isEmpty());
    }

    @Test
    public void emptyInstallPublishesValidDatapackWithoutNetworkRequests() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(packArchive("overworld", "unused"), requests);
        Path root = Files.createTempDirectory("iris-bootstrap-empty");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions options = new DefaultPackBootstrapProvisioner.ProvisionOptions(
                    List.of(),
                    List.of(),
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                    Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
                    Duration.ofHours(1),
                    Duration.ofSeconds(2),
                    1,
                    Duration.ZERO,
                    8L * 1024L * 1024L,
                    root
            );

            DefaultPackBootstrapProvisioner.ProvisionResult result = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );

            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.INSTALLED, result.status());
            assertTrue(result.packRoots().isEmpty());
            assertEquals(0, requests.get());
            assertTrue(Files.isRegularFile(result.datapackRoot().resolve("pack.mcmeta")));
            assertTrue(DefaultPackBootstrapProvisioner.isProvisioned(
                    dataDirectory,
                    root,
                    List.of(),
                    List.of()
            ));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void coldInstallUsesFreshCacheWithoutSecondRequest() throws Exception {
        byte[] archive = packArchive("overworld", "bootstrap_biome");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(archive, requests);
        Path root = Files.createTempDirectory("iris-bootstrap-cold");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            Path legacyDatapack = dataDirectory.resolve("bootstrap/datapack");
            Files.createDirectories(legacyDatapack);
            Files.writeString(legacyDatapack.resolve("obsolete.txt"), "obsolete", StandardCharsets.UTF_8);
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ofHours(1));
            DefaultPackBootstrapProvisioner.ProvisionResult installed = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );
            DefaultPackBootstrapProvisioner.ProvisionResult unchanged = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );

            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.INSTALLED, installed.status());
            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.UNCHANGED, unchanged.status());
            assertEquals(2, requests.get());
            assertTrue(Files.isRegularFile(installed.packRoots().get("overworld").resolve("dimensions/overworld.json")));
            assertTrue(Files.isRegularFile(installed.packRoots().get("underworld").resolve("dimensions/underworld.json")));
            assertTrue(Files.isRegularFile(installed.packRoots().get("underworld").resolve("dimensions/underworld_roof.json")));
            assertEquals(root.resolve("datapacks/iris"), installed.datapackRoot());
            assertTrue(Files.isRegularFile(installed.datapackRoot().resolve("pack.mcmeta")));
            assertTrue(Files.isRegularFile(installedBiomePath(installed.datapackRoot(), "overworld", "bootstrap_biome")));
            assertTrue(Files.isRegularFile(installedBiomePath(installed.datapackRoot(), "underworld", "underworld_biome")));
            assertFalse(Files.exists(dataDirectory.resolve("bootstrap/datapack")));
            assertTrue(DefaultPackBootstrapProvisioner.isProvisioned(
                    dataDirectory,
                    root,
                    options.packs(),
                    options.bindings()
            ));
            assertTrue(DefaultPackBootstrapProvisioner.wasProvisionedThisStartup());
            Properties marker = loadProperties(dataDirectory.resolve("bootstrap/provisioned.properties"));
            assertEquals("true", marker.getProperty("pack.overworld.managed"));
            assertEquals("true", marker.getProperty("pack.underworld.managed"));
            assertEquals("underworld", marker.getProperty("pack.underworld.requiredDimension"));
            delete(installed.packRoots().get("underworld"));
            assertFalse(DefaultPackBootstrapProvisioner.isProvisioned(
                    dataDirectory,
                    root,
                    options.packs(),
                    options.bindings()
            ));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void missingArchiveRebuildsWorldDatapackFromValidatedInstalledPack() throws Exception {
        byte[] archive = packArchive("overworld", "bootstrap_biome");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(archive, requests);
        boolean serverStopped = false;
        Path root = Files.createTempDirectory("iris-bootstrap-offline-migration");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ofHours(1));
            DefaultPackBootstrapProvisioner.ProvisionResult installed = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );
            Files.delete(dataDirectory.resolve("cache/bootstrap/default-overworld.zip"));
            delete(installed.datapackRoot());
            server.stop(0);
            serverStopped = true;

            DefaultPackBootstrapProvisioner.ProvisionResult rebuilt = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );

            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.UPDATED, rebuilt.status());
            assertEquals(2, requests.get());
            assertTrue(Files.isRegularFile(rebuilt.datapackRoot().resolve("pack.mcmeta")));
            assertTrue(Files.isRegularFile(installedBiomePath(rebuilt.datapackRoot(), "overworld", "bootstrap_biome")));
            assertTrue(Files.isRegularFile(installedBiomePath(rebuilt.datapackRoot(), "underworld", "underworld_biome")));
        } finally {
            if (!serverStopped) {
                server.stop(0);
            }
            delete(root);
        }
    }

    @Test
    public void coldInstallPreservesSymbolicLinkPackWorkspace() throws Exception {
        byte[] archive = packArchive("overworld", "bootstrap_biome");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(archive, requests);
        Path root = Files.createTempDirectory("iris-bootstrap-linked-workspace");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            Path sharedPacks = root.resolve("shared-plugin-data/iris/packs");
            Files.createDirectories(dataDirectory);
            Files.createDirectories(sharedPacks);
            try {
                Files.createSymbolicLink(dataDirectory.resolve("packs"), sharedPacks);
            } catch (IOException | UnsupportedOperationException | SecurityException exception) {
                Assume.assumeNoException(exception);
            }
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ofHours(1));

            DefaultPackBootstrapProvisioner.ProvisionResult installed = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );

            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.INSTALLED, installed.status());
            assertEquals(2, requests.get());
            assertTrue(Files.isSymbolicLink(dataDirectory.resolve("packs")));
            assertTrue(Files.isRegularFile(sharedPacks.resolve("overworld/dimensions/overworld.json")));
            assertTrue(Files.isRegularFile(sharedPacks.resolve("underworld/dimensions/underworld.json")));
            assertTrue(Files.isRegularFile(installed.datapackRoot().resolve("pack.mcmeta")));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void corruptCacheRedownloadsAndRepairsArchive() throws Exception {
        byte[] archive = packArchive("overworld", "bootstrap_biome");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(archive, requests);
        Path root = Files.createTempDirectory("iris-bootstrap-corrupt-cache");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ofHours(1));
            DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
            }, options);
            Path cache = dataDirectory.resolve("cache/bootstrap/default-overworld.zip");
            Files.writeString(cache, "corrupt", StandardCharsets.UTF_8);

            DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
            }, options);

            assertEquals(3, requests.get());
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(cache))) {
                assertTrue(zip.getNextEntry() != null);
            }
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void traversalArchiveFailsWithoutMarkerOrEscape() throws Exception {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("dimensions/overworld.json", dimensionJson("overworld"));
        files.put("../escape.txt", "unsafe");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(zip(files), requests);
        Path root = Files.createTempDirectory("iris-bootstrap-traversal");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ZERO);

            assertThrows(IOException.class, () -> DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            ));
            assertFalse(Files.exists(dataDirectory.resolve("bootstrap/provisioned.properties")));
            assertFalse(Files.exists(root.resolve("escape.txt")));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void missingDimensionLayoutFailsWithoutInstallingPack() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(zip(Map.of("README.md", "not an Iris pack")), requests);
        Path root = Files.createTempDirectory("iris-bootstrap-layout");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ZERO);

            assertThrows(IOException.class, () -> DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            ));
            assertFalse(Files.exists(dataDirectory.resolve("packs/overworld")));
            assertFalse(Files.exists(dataDirectory.resolve("bootstrap/provisioned.properties")));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void existingSymlinkedPackIsCompiledWithoutDownloadOrReplacement() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(packArchive("remote", "remote_biome"), requests);
        Path root = Files.createTempDirectory("iris-bootstrap-symlink");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            Path target = root.resolve("custom-overworld");
            writePack(target, "overworld", "local_biome");
            Files.createDirectories(dataDirectory.resolve("packs"));
            Path link = dataDirectory.resolve("packs/overworld");
            Files.createSymbolicLink(link, target);
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ZERO);

            DefaultPackBootstrapProvisioner.ProvisionResult result = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );

            assertEquals(1, requests.get());
            assertTrue(Files.isSymbolicLink(link));
            assertEquals(target.toRealPath(), link.toRealPath());
            assertTrue(Files.isRegularFile(installedBiomePath(result.datapackRoot(), "overworld", "local_biome")));

            Files.writeString(target.resolve("biomes/local.json"), biomeJson("changed_biome"), StandardCharsets.UTF_8);
            assertFalse(DefaultPackBootstrapProvisioner.isProvisioned(
                    dataDirectory,
                    root,
                    options.packs(),
                    options.bindings()
            ));
            DefaultPackBootstrapProvisioner.ProvisionResult updated = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );
            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.UPDATED, updated.status());
            assertEquals(2, requests.get());
            assertTrue(Files.isSymbolicLink(link));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void additionalPackRebuildsAggregateWithoutNetwork() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(packArchive("overworld", "first_biome"), requests);
        Path root = Files.createTempDirectory("iris-bootstrap-aggregate");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions options = options(server, root, Duration.ofHours(1));
            DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
            }, options);
            writePack(dataDirectory.resolve("packs/second"), "second", "second_biome");
            writePack(root.resolve("dimensions/example/world/iris/pack"), "world_local", "world_local_biome");

            assertFalse(DefaultPackBootstrapProvisioner.isProvisioned(
                    dataDirectory,
                    root,
                    options.packs(),
                    options.bindings()
            ));
            DefaultPackBootstrapProvisioner.ProvisionResult updated = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options
            );

            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.UPDATED, updated.status());
            assertEquals(2, requests.get());
            assertTrue(Files.isRegularFile(installedBiomePath(updated.datapackRoot(), "second", "second_biome")));
            assertTrue(Files.isRegularFile(updated.datapackRoot().resolve("data/world_local/worldgen/biome/world_local_biome.json")));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void localEditRelinquishesManagedOwnershipWithoutRemoteReplacement() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(packArchive("overworld", "managed_biome"), requests);
        Path root = Files.createTempDirectory("iris-bootstrap-managed-edit");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions initialOptions = options(server, root, Duration.ofHours(1));
            DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
            }, initialOptions);
            Path editedBiome = dataDirectory.resolve("packs/overworld/biomes/local.json");
            Files.writeString(editedBiome, biomeJson("locally_edited_biome"), StandardCharsets.UTF_8);
            DefaultPackBootstrapProvisioner.ProvisionOptions refreshOptions = options(server, root, Duration.ZERO);

            DefaultPackBootstrapProvisioner.ProvisionResult updated = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    refreshOptions
            );

            assertEquals(3, requests.get());
            assertTrue(Files.readString(editedBiome).contains("locally_edited_biome"));
            assertTrue(Files.isRegularFile(installedBiomePath(updated.datapackRoot(), "overworld", "locally_edited_biome")));
            Properties marker = new Properties();
            try (InputStream input = Files.newInputStream(dataDirectory.resolve("bootstrap/provisioned.properties"))) {
                marker.load(input);
            }
            assertEquals("false", marker.getProperty("pack.overworld.managed"));
            assertEquals("true", marker.getProperty("pack.underworld.managed"));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void underworldLocalEditRelinquishesOnlyUnderworldOwnership() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(packArchive("overworld", "overworld_managed"), requests);
        Path root = Files.createTempDirectory("iris-bootstrap-underworld-edit");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionOptions initialOptions = options(
                    server,
                    root,
                    Duration.ofHours(1)
            );
            DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
            }, initialOptions);
            Path editedBiome = dataDirectory.resolve("packs/underworld/biomes/local.json");
            Files.writeString(editedBiome, biomeJson("underworld_local_edit"), StandardCharsets.UTF_8);
            DefaultPackBootstrapProvisioner.ProvisionOptions refreshOptions = options(server, root, Duration.ZERO);

            DefaultPackBootstrapProvisioner.ProvisionResult updated = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    refreshOptions
            );

            assertEquals(3, requests.get());
            assertTrue(Files.readString(editedBiome).contains("underworld_local_edit"));
            assertTrue(Files.isRegularFile(installedBiomePath(updated.datapackRoot(), "underworld", "underworld_local_edit")));
            Properties marker = loadProperties(dataDirectory.resolve("bootstrap/provisioned.properties"));
            assertEquals("true", marker.getProperty("pack.overworld.managed"));
            assertEquals("false", marker.getProperty("pack.underworld.managed"));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void releasePacksUpdateIndependentlyAndRecompileOneAggregateDatapack() throws Exception {
        byte[] overworld = packArchive("overworld", "overworld_first");
        byte[] underworldFirst = underworldArchive("underworld_first");
        AtomicInteger requests = new AtomicInteger();
        HttpServer initialServer = server(overworld, underworldFirst, requests);
        HttpServer updateServer = null;
        Path root = Files.createTempDirectory("iris-bootstrap-independent-update");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options(initialServer, root, Duration.ofHours(1))
            );
            initialServer.stop(0);
            initialServer = null;

            byte[] underworldSecond = underworldArchive("underworld_second");
            updateServer = server(overworld, underworldSecond, requests);
            DefaultPackBootstrapProvisioner.ProvisionResult updated = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options(updateServer, root, Duration.ZERO)
            );

            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.UPDATED, updated.status());
            assertEquals(4, requests.get());
            assertTrue(Files.readString(dataDirectory.resolve("packs/overworld/biomes/local.json"))
                    .contains("overworld_first"));
            assertTrue(Files.readString(dataDirectory.resolve("packs/underworld/biomes/local.json"))
                    .contains("underworld_second"));
            assertTrue(Files.isRegularFile(installedBiomePath(updated.datapackRoot(), "overworld", "overworld_first")));
            assertTrue(Files.isRegularFile(installedBiomePath(updated.datapackRoot(), "underworld", "underworld_second")));
            assertFalse(Files.exists(installedBiomePath(updated.datapackRoot(), "underworld", "underworld_first")));
        } finally {
            if (initialServer != null) {
                initialServer.stop(0);
            }
            if (updateServer != null) {
                updateServer.stop(0);
            }
            delete(root);
        }
    }

    @Test
    public void invalidUnderworldArchivePublishesNeitherRequiredPack() throws Exception {
        byte[] overworld = packArchive("overworld", "overworld_valid");
        byte[] invalidUnderworld = packArchive("underworld_roof", "roof_only");
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(overworld, invalidUnderworld, requests);
        Path root = Files.createTempDirectory("iris-bootstrap-underworld-invalid");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");

            assertThrows(IOException.class, () -> DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options(server, root, Duration.ZERO)
            ));

            assertEquals(2, requests.get());
            assertFalse(Files.exists(dataDirectory.resolve("packs/overworld")));
            assertFalse(Files.exists(dataDirectory.resolve("packs/underworld")));
            assertFalse(Files.exists(dataDirectory.resolve("bootstrap/provisioned.properties")));
            assertFalse(Files.exists(root.resolve("datapacks/iris")));
        } finally {
            server.stop(0);
            delete(root);
        }
    }

    @Test
    public void failedAggregateCompilationRollsBackBothPackUpdatesAndDatapack() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer initialServer = server(
                packArchive("overworld", "overworld_first"),
                underworldArchive("underworld_first"),
                requests
        );
        HttpServer updateServer = null;
        Path root = Files.createTempDirectory("iris-bootstrap-rollback");
        try {
            Path dataDirectory = root.resolve("plugins/Iris");
            DefaultPackBootstrapProvisioner.ProvisionResult first = DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    options(initialServer, root, Duration.ofHours(1))
            );
            initialServer.stop(0);
            initialServer = null;
            byte[] marker = Files.readAllBytes(dataDirectory.resolve("bootstrap/provisioned.properties"));
            byte[] metadata = Files.readAllBytes(first.datapackRoot().resolve("pack.mcmeta"));
            byte[] originalOverworld = Files.readAllBytes(dataDirectory.resolve("packs/overworld/biomes/local.json"));
            byte[] originalUnderworld = Files.readAllBytes(dataDirectory.resolve("packs/underworld/biomes/local.json"));
            Path invalidPack = dataDirectory.resolve("packs/invalid");
            Files.createDirectories(invalidPack.resolve("dimensions"));
            Files.writeString(invalidPack.resolve("dimensions/broken.json"), "{", StandardCharsets.UTF_8);
            updateServer = server(
                    packArchive("overworld", "overworld_second"),
                    underworldArchive("underworld_second"),
                    requests
            );
            DefaultPackBootstrapProvisioner.ProvisionOptions updateOptions = options(
                    updateServer,
                    root,
                    Duration.ZERO
            );

            assertThrows(IOException.class, () -> DefaultPackBootstrapProvisioner.provision(
                    dataDirectory,
                    ignored -> {
                    },
                    updateOptions
            ));
            assertTrue(Arrays.equals(marker, Files.readAllBytes(dataDirectory.resolve("bootstrap/provisioned.properties"))));
            assertTrue(Arrays.equals(metadata, Files.readAllBytes(first.datapackRoot().resolve("pack.mcmeta"))));
            assertTrue(Arrays.equals(originalOverworld,
                    Files.readAllBytes(dataDirectory.resolve("packs/overworld/biomes/local.json"))));
            assertTrue(Arrays.equals(originalUnderworld,
                    Files.readAllBytes(dataDirectory.resolve("packs/underworld/biomes/local.json"))));
            assertNoBootstrapTransactionPaths(dataDirectory.resolve("packs"));
            assertNoBootstrapTransactionPaths(root.resolve("datapacks"));
        } finally {
            if (initialServer != null) {
                initialServer.stop(0);
            }
            if (updateServer != null) {
                updateServer.stop(0);
            }
            delete(root);
        }
    }

    @Test
    public void resolvesConfiguredLevelRootFromServerProperties() throws Exception {
        Path serverRoot = Files.createTempDirectory("iris-bootstrap-level-root");
        try {
            Files.writeString(
                    serverRoot.resolve("server.properties"),
                    "level-name=levels/primary\n",
                    StandardCharsets.UTF_8
            );

            assertEquals(
                    serverRoot.toRealPath().resolve("levels/primary").normalize(),
                    DefaultPackBootstrapProvisioner.resolveLevelRoot(serverRoot)
            );
        } finally {
            delete(serverRoot);
        }
    }

    @Test
    public void effectiveStartupBindingsInstallAndRefreshBootNativeLevelStem() throws Exception {
        Path serverRoot = Files.createTempDirectory("iris-bootstrap-level-stem");
        try {
            Path dataDirectory = serverRoot.resolve("plugins/Iris");
            Path levelRoot = serverRoot.resolve("levels/primary");
            Path packRoot = levelRoot.resolve("dimensions/iris/moon/iris/pack");
            Files.createDirectories(levelRoot);
            Files.writeString(
                    serverRoot.resolve("server.properties"),
                    "level-name=levels/primary\n",
                    StandardCharsets.UTF_8
            );
            writePack(packRoot, "overworld", "overworld_biome");
            Files.writeString(
                    packRoot.resolve("dimensions/underworld.json"),
                    dimensionJson("underworld"),
                    StandardCharsets.UTF_8
            );
            Files.writeString(
                    serverRoot.resolve("bukkit.yml"),
                    "worlds:\n  primary_iris_moon:\n    generator: Iris:overworld\n",
                    StandardCharsets.UTF_8
            );
            BukkitStartupPaths startupPaths = BukkitStartupPaths.resolve(serverRoot, new String[0]);

            DefaultPackBootstrapProvisioner.ProvisionResult installed =
                    DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
                    }, startupPaths);

            Path levelStem = installed.datapackRoot().resolve("data/iris/dimension/moon.json");
            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.INSTALLED, installed.status());
            assertTrue(Files.readString(levelStem).contains("\"type\": \"iris:overworld\""));

            Files.writeString(
                    serverRoot.resolve("bukkit.yml"),
                    "worlds:\n  primary_iris_moon:\n    generator: Iris:underworld\n",
                    StandardCharsets.UTF_8
            );
            DefaultPackBootstrapProvisioner.ProvisionResult updated =
                    DefaultPackBootstrapProvisioner.provision(dataDirectory, ignored -> {
                    }, startupPaths);

            assertEquals(DefaultPackBootstrapProvisioner.ProvisionStatus.UPDATED, updated.status());
            assertTrue(Files.readString(levelStem).contains("\"type\": \"iris:underworld\""));
        } finally {
            delete(serverRoot);
        }
    }

    private static Path installedBiomePath(Path datapackRoot, String dimension, String id) {
        String resourceKey = GenerationRegistryContractFactory.customBiomeResourceKey(
                dimension, new IrisBiomeCustom().setId(id)
        );
        int separator = resourceKey.indexOf(':');
        return datapackRoot.resolve("data")
                .resolve(resourceKey.substring(0, separator))
                .resolve("worldgen/biome")
                .resolve(resourceKey.substring(separator + 1) + ".json");
    }

    private static DefaultPackBootstrapProvisioner.ProvisionOptions options(
            HttpServer server,
            Path serverRoot,
            Duration refreshInterval
    ) {
        String sourceRoot = "http://127.0.0.1:" + server.getAddress().getPort();
        return new DefaultPackBootstrapProvisioner.ProvisionOptions(
                List.of(
                        new DefaultPackBootstrapProvisioner.PackSpec(
                                "overworld",
                                URI.create(sourceRoot + "/overworld.zip"),
                                "overworld"
                        ),
                        new DefaultPackBootstrapProvisioner.PackSpec(
                                "underworld",
                                URI.create(sourceRoot + "/underworld.zip"),
                                "underworld"
                        )
                ),
                List.of(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Clock.fixed(Instant.parse("2026-07-12T12:00:00Z"), ZoneOffset.UTC),
                refreshInterval,
                Duration.ofSeconds(2),
                1,
                Duration.ZERO,
                8L * 1024L * 1024L,
                serverRoot
        );
    }

    private static HttpServer server(byte[] response, AtomicInteger requests) throws IOException {
        return server(response, underworldArchive("underworld_biome"), requests);
    }

    private static HttpServer server(
            byte[] overworldResponse,
            byte[] underworldResponse,
            AtomicInteger requests
    ) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/overworld.zip", exchange -> respond(exchange, overworldResponse, requests));
        server.createContext("/underworld.zip", exchange -> respond(exchange, underworldResponse, requests));
        server.start();
        return server;
    }

    private static void respond(HttpExchange exchange, byte[] response, AtomicInteger requests) throws IOException {
        requests.incrementAndGet();
        exchange.getResponseHeaders().add("Content-Type", "application/zip");
        exchange.sendResponseHeaders(200, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private static byte[] packArchive(String dimensionKey, String biomeId) throws IOException {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("dimensions/" + dimensionKey + ".json", dimensionJson(dimensionKey));
        files.put("regions/local.json", "{\"name\":\"Local\",\"landBiomes\":[\"local\"]}");
        files.put("biomes/local.json", biomeJson(biomeId));
        return zip(files);
    }

    private static byte[] underworldArchive(String biomeId) throws IOException {
        LinkedHashMap<String, String> files = new LinkedHashMap<>();
        files.put("dimensions/underworld.json", dimensionJson("underworld"));
        files.put("dimensions/underworld_roof.json", dimensionJson("underworld_roof"));
        files.put("regions/local.json", "{\"name\":\"Local\",\"landBiomes\":[\"local\"]}");
        files.put("biomes/local.json", biomeJson(biomeId));
        return zip(files);
    }

    private static Properties loadProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return properties;
    }

    private static void assertNoBootstrapTransactionPaths(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            assertFalse(stream.anyMatch(path -> path.getFileName().toString().contains("-stage-")
                    || path.getFileName().toString().contains("-backup-")));
        }
    }

    private static void writePack(Path root, String dimensionKey, String biomeId) throws IOException {
        Files.createDirectories(root.resolve("dimensions"));
        Files.createDirectories(root.resolve("regions"));
        Files.createDirectories(root.resolve("biomes"));
        Files.writeString(root.resolve("dimensions/" + dimensionKey + ".json"), dimensionJson(dimensionKey), StandardCharsets.UTF_8);
        Files.writeString(root.resolve("regions/local.json"), "{\"name\":\"Local\",\"landBiomes\":[\"local\"]}", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("biomes/local.json"), biomeJson(biomeId), StandardCharsets.UTF_8);
    }

    private static String dimensionJson(String name) {
        return "{\"name\":\"" + name + "\",\"regions\":[\"local\"],\"logicalHeight\":256,\"dimensionHeight\":{\"min\":-64,\"max\":320}}";
    }

    private static String biomeJson(String biomeId) {
        return "{\"name\":\"Local\",\"derivative\":\"minecraft:plains\",\"customDerivitives\":[{\"id\":\""
                + biomeId + "\",\"category\":\"plains\"}]}";
    }

    private static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
