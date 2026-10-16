package art.arcane.volmlib.nativelib.minecraft26_2.forge;

import art.arcane.volmlib.nativelib.modded.NativeLoaderOptions;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
        when(mob.checkSpawnRules(level, MobSpawnType.CHUNK_GENERATION)).thenReturn(true);
        when(mob.checkSpawnObstruction(level)).thenReturn(true);
        AtomicReference<MobSpawnType> observed = new AtomicReference<>();
        Consumer<MobSpawnEvent.PositionCheck> listener = event -> {
            observed.set(event.getSpawnType());
            event.setResult(Event.Result.DENY);
        };
        MinecraftForge.EVENT_BUS.addListener(listener);
        try {
            assertFalse(loader.checkSpawnPosition(mob, level, MobSpawnType.CHUNK_GENERATION));
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener);
        }
        assertEquals(MobSpawnType.CHUNK_GENERATION, observed.get());
        assertTrue(loader.checkSpawnPosition(mob, level, MobSpawnType.CHUNK_GENERATION));
    }
}
