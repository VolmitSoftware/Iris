package art.arcane.iris.modded;

import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedLoader;
import art.arcane.volmlib.nativelib.terrain.NativeWorld;

import net.minecraft.core.BlockPos;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.NativeModdedServer;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerAccess;
import art.arcane.volmlib.nativelib.minecraft26_2.modded.ModdedServerLevels;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class ModdedPlatformPathsTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ModdedPlatform platform() {
        Path configDir = temporaryFolder.getRoot().toPath();
        return new ModdedPlatform(new NativeModdedLoader() {
            @Override
            public String platformName() {
                return "test";
            }

            @Override
            public String minecraftVersion() {
                return "26.2";
            }

            @Override
            public String modVersion() {
                return "0.0.0";
            }

            @Override
            public NativeModdedServer currentServer() {
                return null;
            }

            @Override
            public ModdedServerAccess serverAccess() {
                return new ModdedServerLevels(server -> {});
            }

            @Override
            public void fireDynamicLevelLoad(NativeWorld level) {
            }

            @Override
            public void fireDynamicLevelUnload(NativeWorld level) {
            }

            @Override
            public boolean clientEnvironment() {
                return false;
            }

            @Override
            public Path configDir() {
                return configDir;
            }

            @Override
            public File modJar() {
                return null;
            }

            @Override
            public boolean hasBlockBreakPermission(ServerPlayer player) {
                return false;
            }

            @Override
            public boolean canBreakBlock(ServerLevel level, ServerPlayer player, BlockPos position, BlockState state) {
                return false;
            }
        });
    }

    @Test
    public void packsResolveUnderIrisworldgen() {
        ModdedPlatform platform = platform();
        File root = temporaryFolder.getRoot();
        File packsRoot = new File(new File(root, "irisworldgen"), "packs");

        assertEquals(packsRoot, platform.packsFolder());
        assertEquals(packsRoot, platform.dataFolder("packs"));
        assertEquals(new File(packsRoot, "overworld"), platform.dataFolderNoCreate("packs", "overworld"));
        assertEquals(new File(packsRoot, "overworld" + File.separator + "dimensions" + File.separator + "overworld.json"),
                platform.dataFile("packs", "overworld", "dimensions", "overworld.json"));
    }

    @Test
    public void everythingElseStaysUnderIris() {
        ModdedPlatform platform = platform();
        File iris = new File(temporaryFolder.getRoot(), "iris");

        assertEquals(iris, platform.dataFolder());
        assertEquals(new File(iris, "iris.json"), platform.dataFile("iris.json"));
        assertEquals(new File(iris, "parity"), platform.dataFolder("parity"));
    }

    @Test
    public void packsMatchIsExactSegmentOnly() {
        ModdedPlatform platform = platform();
        File iris = new File(temporaryFolder.getRoot(), "iris");

        assertEquals(new File(iris, "packbenchmarks"), platform.dataFolder("packbenchmarks"));
        assertEquals(new File(iris, "packsx"), platform.dataFolderNoCreate("packsx"));
    }
}
