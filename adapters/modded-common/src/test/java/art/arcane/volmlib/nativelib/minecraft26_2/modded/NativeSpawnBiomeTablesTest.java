package art.arcane.volmlib.nativelib.minecraft26_2.modded;

import art.arcane.volmlib.nativelib.terrain.NativeGenerationLease;
import art.arcane.volmlib.nativelib.terrain.NativeGenerationScope;
import art.arcane.volmlib.nativelib.terrain.NativeSpawnBiomePolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Optional;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NativeSpawnBiomeTablesTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void runtimeRetirementDropsMappingsAndRebuildsUnderAdmission() {
        Fixture fixture = new Fixture();
        fixture.tables.initializeVanillaSpawnBiomes(fixture.registry);
        fixture.tables.initializeVanillaSpawnBiomes(fixture.registry);
        assertSame(fixture.vanilla, fixture.tables.vanillaSpawnBiome(fixture.custom.value()));
        verify(fixture.policy, times(1)).populate(eq(1), any());
        verify(fixture.lease, times(1)).close();
        verify(fixture.scope, times(1)).close();
        fixture.tables.evictRuntime(11);
        assertNull(fixture.tables.vanillaSpawnBiome(fixture.custom.value()));
        fixture.tables.initializeVanillaSpawnBiomes(fixture.registry);
        verify(fixture.policy, times(2)).populate(eq(1), any());
        assertSame(fixture.vanilla, fixture.tables.vanillaSpawnBiome(fixture.custom.value()));
    }

    @Test
    public void absentRuntimeDoesNotReadTheRegistryOrAcquireAdmission() {
        Fixture fixture = new Fixture();
        when(fixture.policy.current()).thenReturn(null);
        fixture.tables.initializeVanillaSpawnBiomes(null);
        assertNull(fixture.tables.vanillaSpawnBiome(fixture.custom.value()));
        verify(fixture.policy, times(0)).lease(any());
    }

    private static final class Fixture {
        private final NativeSpawnBiomePolicy<Integer> policy;
        private final Registry<Biome> registry;
        private final Holder.Reference<Biome> vanilla;
        private final Holder.Reference<Biome> custom;
        private final NativeGenerationLease lease = mock(NativeGenerationLease.class);
        private final NativeGenerationScope scope = mock(NativeGenerationScope.class);
        private final NativeSpawnBiomeTables<Integer> tables;

        @SuppressWarnings("unchecked")
        private Fixture() {
            policy = mock(NativeSpawnBiomePolicy.class);
            registry = mock(Registry.class);
            vanilla = mock(Holder.Reference.class);
            custom = mock(Holder.Reference.class);
            when(vanilla.value()).thenReturn(mock(Biome.class));
            when(custom.value()).thenReturn(mock(Biome.class));
            when(registry.get(Identifier.parse("minecraft:plains"))).thenReturn(Optional.of(vanilla));
            when(registry.get(Identifier.parse("test:custom"))).thenReturn(Optional.of(custom));
            when(policy.current()).thenReturn(1);
            when(policy.runtimeId(1)).thenReturn(11);
            when(policy.lease(1)).thenReturn(lease);
            when(policy.context(1, lease)).thenReturn(scope);
            doAnswer(invocation -> {
                NativeSpawnBiomePolicy.MappingTarget target = invocation.getArgument(1);
                target.vanilla("minecraft:plains").accept("test:custom");
                return null;
            }).when(policy).populate(eq(1), any());
            tables = new NativeSpawnBiomeTables<>(policy, new Object());
        }
    }
}
