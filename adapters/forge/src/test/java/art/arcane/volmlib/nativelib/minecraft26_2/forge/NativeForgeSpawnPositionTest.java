package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.listener.EventListener;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeForgeSpawnPositionTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void chunkGenerationSpawnsFireThePositionCheckEventAndHonorItsDenial() {
        NativeForgeLoader loader = new NativeForgeLoader(
                new NativeLoaderOptions("irisworldgen", "treefeller", BooleanSupplier::getAsBoolean));
        ServerLevelAccessor level = mock(ServerLevelAccessor.class);
        Mob mob = mock(Mob.class);
        when(mob.checkSpawnRules(level, EntitySpawnReason.CHUNK_GENERATION)).thenReturn(true);
        when(mob.checkSpawnObstruction(level)).thenReturn(true);
        AtomicReference<EntitySpawnReason> observed = new AtomicReference<>();
        EventListener listener = MobSpawnEvent.PositionCheck.BUS.addListener((MobSpawnEvent.PositionCheck event) -> {
            observed.set(event.getSpawnReason());
            event.setResult(Result.DENY);
        });
        try {
            assertFalse(loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION));
        } finally {
            MobSpawnEvent.PositionCheck.BUS.removeListener(listener);
        }
        assertEquals(EntitySpawnReason.CHUNK_GENERATION, observed.get());
        assertTrue(loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION));
    }
}
