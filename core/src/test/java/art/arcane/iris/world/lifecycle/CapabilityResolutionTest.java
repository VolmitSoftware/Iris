package art.arcane.iris.world.lifecycle;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class CapabilityResolutionTest {
    @Test
    public void resolvesExactCreateLevelMethod() throws Exception {
        Method method = CapabilityResolution.resolveCreateLevelMethod(CurrentCreateLevelOwner.class);

        assertEquals("createLevel", method.getName());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    public void resolvesDeclaredLegacyCreateLevelMethod() throws Exception {
        Method method = CapabilityResolution.resolveCreateLevelMethod(DeclaredLegacyCreateLevelOwner.class);

        assertEquals("createLevel", method.getName());
        assertEquals(4, method.getParameterCount());
    }

    @Test
    public void resolvesTwoArgLevelStorageAccessMethod() throws Exception {
        Method method = CapabilityResolution.resolveLevelStorageAccessMethod(TwoArgLevelStorageSource.class);

        assertEquals("validateAndCreateAccess", method.getName());
        assertEquals(2, method.getParameterCount());
    }

    @Test
    public void resolvesOneArgLevelStorageAccessMethod() throws Exception {
        Method method = CapabilityResolution.resolveLevelStorageAccessMethod(OneArgLevelStorageSource.class);

        assertEquals("validateAndCreateAccess", method.getName());
        assertEquals(1, method.getParameterCount());
    }

    @Test
    public void resolvesPublicWorldDataHelper() throws Exception {
        Method method = CapabilityResolution.resolvePaperWorldDataMethod(PublicWorldLoader.class);

        assertEquals("loadWorldData", method.getName());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    public void resolvesDeclaredWorldDataHelper() throws Exception {
        Method method = CapabilityResolution.resolvePaperWorldDataMethod(DeclaredWorldLoader.class);

        assertEquals("loadWorldData", method.getName());
        assertEquals(3, method.getParameterCount());
    }

    @Test
    public void resolvesDeclaredServerRegistryAccessMethod() throws Exception {
        Method method = CapabilityResolution.resolveServerRegistryAccessMethod(DeclaredRegistryAccessOwner.class);

        assertEquals("registryAccess", method.getName());
        assertEquals(0, method.getParameterCount());
    }

    @Test
    public void resolvesCurrentWorldLoadingInfoConstructor() throws Exception {
        Constructor<?> constructor = CapabilityResolution.resolveWorldLoadingInfoConstructor(CurrentWorldLoadingInfo.class);

        assertEquals(4, constructor.getParameterCount());
    }

    @Test
    public void resolvesLegacyWorldLoadingInfoConstructor() throws Exception {
        Constructor<?> constructor = CapabilityResolution.resolveWorldLoadingInfoConstructor(LegacyWorldLoadingInfo.class);

        assertEquals(5, constructor.getParameterCount());
    }

    public static final class LevelStem {
    }

    public static final class WorldLoadingInfo {
    }

    public static final class WorldLoadingInfoAndData {
    }

    public static final class WorldDataAndGenSettings {
    }

    public static final class PrimaryLevelData {
    }

    public static final class LevelStorageAccess {
    }

    public static final class ResourceKey {
    }

    public static final class LoadedWorldData {
    }

    public static final class MinecraftServer {
    }

    public static final class RegistryAccess {
    }

    public enum Environment {
        NORMAL
    }

    public static final class CurrentCreateLevelOwner {
        public void createLevel(LevelStem levelStem, WorldLoadingInfoAndData worldLoadingInfoAndData, WorldDataAndGenSettings worldDataAndGenSettings) {
        }
    }

    public static final class DeclaredLegacyCreateLevelOwner {
        private void createLevel(LevelStem levelStem, WorldLoadingInfo worldLoadingInfo, LevelStorageAccess levelStorageAccess, PrimaryLevelData primaryLevelData) {
        }
    }

    public static final class TwoArgLevelStorageSource {
        public LevelStorageAccess validateAndCreateAccess(String worldName, ResourceKey resourceKey) {
            return null;
        }
    }

    public static final class OneArgLevelStorageSource {
        public LevelStorageAccess validateAndCreateAccess(String worldName) {
            return null;
        }
    }

    public static final class PublicWorldLoader {
        public static LoadedWorldData loadWorldData(MinecraftServer minecraftServer, ResourceKey dimensionKey, String worldName) {
            return null;
        }
    }

    public static final class DeclaredWorldLoader {
        private static LoadedWorldData loadWorldData(MinecraftServer minecraftServer, ResourceKey dimensionKey, String worldName) {
            return null;
        }
    }

    public static final class DeclaredRegistryAccessOwner {
        private RegistryAccess registryAccess() {
            return null;
        }
    }

    public static final class CurrentWorldLoadingInfo {
        public CurrentWorldLoadingInfo(Environment environment, ResourceKey stemKey, ResourceKey dimensionKey, boolean enabled) {
        }
    }

    public static final class LegacyWorldLoadingInfo {
        private LegacyWorldLoadingInfo(int index, String worldName, String environment, ResourceKey stemKey, boolean enabled) {
        }
    }
}
