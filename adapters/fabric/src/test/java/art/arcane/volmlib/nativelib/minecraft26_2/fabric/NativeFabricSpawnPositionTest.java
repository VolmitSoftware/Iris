package art.arcane.volmlib.nativelib.minecraft26_2.fabric;

import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeFabricSpawnPositionTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void chunkGenerationSpawnsUseTheVanillaRulesAndObstructionChecks() {
        NativeFabricLoader loader = new NativeFabricLoader(
                new NativeLoaderOptions("irisworldgen", "treefeller", BooleanSupplier::getAsBoolean));
        ServerLevelAccessor level = mock(ServerLevelAccessor.class);
        Mob mob = mock(Mob.class);
        when(mob.checkSpawnRules(level, EntitySpawnReason.CHUNK_GENERATION)).thenReturn(true);
        when(mob.checkSpawnObstruction(level)).thenReturn(true);
        assertTrue(loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION));

        when(mob.checkSpawnObstruction(level)).thenReturn(false);
        assertFalse(loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION));

        when(mob.checkSpawnObstruction(level)).thenReturn(true);
        when(mob.checkSpawnRules(level, EntitySpawnReason.CHUNK_GENERATION)).thenReturn(false);
        assertFalse(loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION));
    }
}
