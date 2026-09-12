package art.arcane.iris.world.lifecycle;

import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WorldReplacementSeedTest {
    private static final long SEED = -734829104958217364L;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readsAuthoritativeLongSeed() throws Exception {
        CompoundTag data = new CompoundTag();
        data.putLong("seed", SEED);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        Path worldDirectory = writeSettings("valid", root);

        assertEquals(SEED, WorldReplacementSeed.readAuthoritativeSeed(worldDirectory));
    }

    @Test
    public void copiesSettingsAndRewritesOnlyAuthoritativeSeed() throws Exception {
        CompoundTag dimensions = new CompoundTag();
        dimensions.putString("marker", "preserved");
        CompoundTag data = new CompoundTag();
        data.putLong("seed", 1337L);
        data.putString("generator", "preserved");
        data.put("dimensions", dimensions);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        root.putString("root-marker", "preserved");
        Path sourceWorld = writeSettings("copy-source", root);
        Path targetWorld = temporaryFolder.newFolder("copy-target").toPath();

        WorldReplacementSeed.copyWithAuthoritativeSeed(sourceWorld, targetWorld, SEED);

        assertEquals(1337L, WorldReplacementSeed.readAuthoritativeSeed(sourceWorld));
        assertEquals(SEED, WorldReplacementSeed.readAuthoritativeSeed(targetWorld));
        NamedTag copied = NBTUtil.read(settingsPath(targetWorld).toFile());
        CompoundTag copiedRoot = (CompoundTag) copied.getTag();
        CompoundTag copiedData = copiedRoot.getCompoundTag("data");
        assertEquals("preserved", copiedRoot.getString("root-marker"));
        assertEquals("preserved", copiedData.getString("generator"));
        assertEquals("preserved", copiedData.getCompoundTag("dimensions").getString("marker"));
    }

    @Test
    public void stagesTheRetainedAuthoritativeSeedWhenNoOverrideWasRequested() throws Exception {
        CompoundTag data = new CompoundTag();
        data.putLong("seed", SEED);
        data.putString("generator", "preserved");
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        Path sourceWorld = writeSettings("inherited-source", root);
        Path stagedWorld = temporaryFolder.newFolder("inherited-stage").toPath();

        long effectiveSeed = WorldReplacementSeed.stageAuthoritativeSeed(
                sourceWorld,
                stagedWorld,
                OptionalLong.empty()
        );

        assertEquals(SEED, effectiveSeed);
        assertEquals(SEED, WorldReplacementSeed.readAuthoritativeSeed(sourceWorld));
        assertEquals(SEED, WorldReplacementSeed.readAuthoritativeSeed(stagedWorld));
        assertTrue(Files.notExists(stagedWorld.resolve("data/paper")));
    }

    @Test
    public void stagesAnExplicitSeedWithoutChangingTheRetainedWorld() throws Exception {
        CompoundTag data = new CompoundTag();
        data.putLong("seed", 1337L);
        data.putString("generator", "preserved");
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        Path sourceWorld = writeSettings("override-source", root);
        Path stagedWorld = temporaryFolder.newFolder("override-stage").toPath();

        long effectiveSeed = WorldReplacementSeed.stageAuthoritativeSeed(
                sourceWorld,
                stagedWorld,
                OptionalLong.of(SEED)
        );

        assertEquals(SEED, effectiveSeed);
        assertEquals(1337L, WorldReplacementSeed.readAuthoritativeSeed(sourceWorld));
        assertEquals(SEED, WorldReplacementSeed.readAuthoritativeSeed(stagedWorld));
        NamedTag staged = NBTUtil.read(settingsPath(stagedWorld).toFile());
        CompoundTag stagedRoot = (CompoundTag) staged.getTag();
        assertEquals("preserved", stagedRoot.getCompoundTag("data").getString("generator"));
        assertTrue(Files.notExists(stagedWorld.resolve("data/paper")));
    }

    @Test
    public void rejectsAnExistingStagedSettingsFileWithoutChangingIt() throws Exception {
        CompoundTag sourceData = new CompoundTag();
        sourceData.putLong("seed", 1337L);
        CompoundTag sourceRoot = new CompoundTag();
        sourceRoot.put("data", sourceData);
        Path sourceWorld = writeSettings("existing-source", sourceRoot);

        CompoundTag stagedData = new CompoundTag();
        stagedData.putLong("seed", 42L);
        CompoundTag stagedRoot = new CompoundTag();
        stagedRoot.put("data", stagedData);
        Path stagedWorld = writeSettings("existing-stage", stagedRoot);

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementSeed.stageAuthoritativeSeed(
                        sourceWorld,
                        stagedWorld,
                        OptionalLong.of(SEED)
                )
        );

        assertTrue(failure.getMessage().contains("already exist"));
        assertEquals(42L, WorldReplacementSeed.readAuthoritativeSeed(stagedWorld));
        assertEquals(1337L, WorldReplacementSeed.readAuthoritativeSeed(sourceWorld));
    }

    @Test
    public void invalidSourceDoesNotCreatePartialStageState() throws Exception {
        CompoundTag sourceRoot = new CompoundTag();
        sourceRoot.put("data", new CompoundTag());
        Path sourceWorld = writeSettings("invalid-stage-source", sourceRoot);
        Path stagedWorld = temporaryFolder.newFolder("invalid-stage-target").toPath();
        Path retainedMarker = stagedWorld.resolve("iris/pack/marker.txt");
        Files.createDirectories(retainedMarker.getParent());
        Files.writeString(retainedMarker, "retained");

        assertThrows(
                IOException.class,
                () -> WorldReplacementSeed.stageAuthoritativeSeed(
                        sourceWorld,
                        stagedWorld,
                        OptionalLong.of(SEED)
                )
        );

        assertTrue(Files.notExists(stagedWorld.resolve("data")));
        assertEquals("retained", Files.readString(retainedMarker));
    }

    @Test
    public void rejectsMissingDataCompound() throws Exception {
        Path worldDirectory = writeSettings("missing-data", new CompoundTag());

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementSeed.readAuthoritativeSeed(worldDirectory)
        );

        assertTrue(failure.getMessage().contains("missing data"));
    }

    @Test
    public void rejectsMissingSeed() throws Exception {
        CompoundTag root = new CompoundTag();
        root.put("data", new CompoundTag());
        Path worldDirectory = writeSettings("missing-seed", root);

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementSeed.readAuthoritativeSeed(worldDirectory)
        );

        assertTrue(failure.getMessage().contains("missing data.seed"));
    }

    @Test
    public void rejectsWrongDataType() throws Exception {
        CompoundTag root = new CompoundTag();
        root.putString("data", "invalid");
        Path worldDirectory = writeSettings("wrong-data-type", root);

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementSeed.readAuthoritativeSeed(worldDirectory)
        );

        assertTrue(failure.getMessage().contains("data must be a compound tag"));
    }

    @Test
    public void rejectsNonLongSeed() throws Exception {
        CompoundTag data = new CompoundTag();
        data.putInt("seed", 1337);
        CompoundTag root = new CompoundTag();
        root.put("data", data);
        Path worldDirectory = writeSettings("wrong-seed-type", root);

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementSeed.readAuthoritativeSeed(worldDirectory)
        );

        assertTrue(failure.getMessage().contains("data.seed must be a long tag"));
    }

    @Test
    public void rejectsCorruptSettings() throws Exception {
        Path worldDirectory = temporaryFolder.newFolder("corrupt").toPath();
        Path settings = settingsPath(worldDirectory);
        Files.createDirectories(settings.getParent());
        Files.write(settings, new byte[]{10, 0});

        IOException failure = assertThrows(
                IOException.class,
                () -> WorldReplacementSeed.readAuthoritativeSeed(worldDirectory)
        );

        assertTrue(failure.getMessage().contains("Could not read Paper world generation settings"));
    }

    private Path writeSettings(String name, CompoundTag root) throws Exception {
        Path worldDirectory = temporaryFolder.newFolder(name).toPath();
        Path settings = settingsPath(worldDirectory);
        Files.createDirectories(settings.getParent());
        NBTUtil.write(root, settings.toFile());
        return worldDirectory;
    }

    private Path settingsPath(Path worldDirectory) {
        return worldDirectory.resolve("data/minecraft/world_gen_settings.dat");
    }
}
