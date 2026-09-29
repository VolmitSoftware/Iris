package art.arcane.volmlib.nativelib.minecraft26_2.neoforge;

import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeNeoForgeSpawnPositionTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void chunkGenerationSpawnsFireThePositionCheckEventAndHonorItsDenial() {
        NativeNeoForgeLoader loader = new NativeNeoForgeLoader(
                new NativeLoaderOptions("irisworldgen", "treefeller", BooleanSupplier::getAsBoolean));
        ServerLevelAccessor level = mock(ServerLevelAccessor.class);
        Mob mob = mock(Mob.class);
        when(mob.checkSpawnRules(level, EntitySpawnReason.CHUNK_GENERATION)).thenReturn(true);
        when(mob.checkSpawnObstruction(level)).thenReturn(true);
        AtomicBoolean deny = new AtomicBoolean(true);
        AtomicReference<EntitySpawnReason> observed = new AtomicReference<>();
        Consumer<MobSpawnEvent.PositionCheck> listener = (MobSpawnEvent.PositionCheck event) -> {
            if (event.getEntity() == mob && deny.get()) {
                observed.set(event.getSpawnType());
                event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
            }
        };
        NeoForge.EVENT_BUS.addListener(MobSpawnEvent.PositionCheck.class, listener);
        try {
            assertFalse(loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION));
            assertEquals(EntitySpawnReason.CHUNK_GENERATION, observed.get());
            deny.set(false);
            assertTrue(loader.checkSpawnPosition(mob, level, EntitySpawnReason.CHUNK_GENERATION));
        } finally {
            NeoForge.EVENT_BUS.unregister(listener);
        }
    }
}
