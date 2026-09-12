package art.arcane.iris.integration;

import art.arcane.iris.integration.data.DataType;
import art.arcane.iris.platform.bukkit.nms.container.BlockProperty;
import art.arcane.iris.platform.bukkit.nms.container.Pair;
import art.arcane.iris.generation.runtime.Engine;
import art.arcane.iris.generation.block.IrisCustomData;
import art.arcane.volmlib.util.collection.KMap;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExternalDataSVCBlockRoutingTest {
    @Test
    public void directPlacementRetainsQualifiedProviderAndProperties() throws Exception {
        Identifier nativeId = Identifier.fromString("default:palm_log");
        Identifier stateId = Identifier.fromString("default:palm_log[axis=x]");
        ExternalDataProvider provider = provider("CraftEngine", nativeId);
        Block block = mock(Block.class);
        when(provider.placeBlock(block, stateId)).thenReturn(true);

        assertEquals(true, service(provider).placeBlock(block,
                Identifier.fromString("craftengine:default/palm_log[axis=x]")));
        verify(provider).placeBlock(block, stateId);
    }

    @Test
    public void capturedBlocksRetainSemanticPropertiesAndProviderIdentity() throws Exception {
        Identifier id = Identifier.fromString("example:palm_log[axis=x]");
        ExternalDataProvider provider = provider("CraftEngine", id);
        BlockData base = mock(BlockData.class);
        when(base.getAsString()).thenReturn("craftengine:custom_18");
        when(provider.identifyBlock(base)).thenReturn(Optional.of(id));
        ExternalDataSVC service = service(provider);

        IrisCustomData captured = (IrisCustomData) service.captureBlockData(base);

        assertSame(base, captured.getBase());
        assertEquals("craftengine:example/palm_log[axis=x]", captured.getCustom().toString());
        assertSame(captured, service.captureBlockData(captured));
    }

    @Test
    public void vanillaCaptureKeepsOriginalBlockData() throws Exception {
        ExternalDataProvider provider = provider("CraftEngine", Identifier.fromString("example:wood"));
        BlockData base = mock(BlockData.class);

        assertSame(base, service(provider).captureBlockData(base));
    }

    @Test
    public void nativeLookupPinsProviderAndRetainsPropertiesUntilPlacement() throws Exception {
        Identifier nativeId = Identifier.fromString("example:wood");
        ExternalDataProvider provider = provider("CraftEngine", nativeId);
        BlockData base = mock(BlockData.class);
        KMap<String, String> properties = new KMap<>();
        properties.put("facing", "east");
        when(provider.getBlockData(nativeId, properties)).thenReturn(IrisCustomData.of(base,
                Identifier.fromString("example:wood[facing=east]")));
        ExternalDataSVC service = service(provider);

        IrisCustomData resolved = (IrisCustomData) service.getBlockData(
                Identifier.fromString("example:wood[facing=east]")).orElseThrow();

        assertSame(base, resolved.getBase());
        assertEquals("craftengine:example/wood[facing=east]", resolved.getCustom().toString());
        ExternalDataProvider competing = provider("ItemsAdder", nativeId);
        addProvider(service, competing);
        Engine engine = mock(Engine.class);
        Block block = mock(Block.class);
        service.processUpdate(engine, block, resolved.getCustom());
        verify(provider).processUpdate(engine, block, Identifier.fromString("example:wood[facing=east]"));
        verify(competing, never()).processUpdate(any(), any(), any());
    }

    @Test
    public void qualifiedLookupSelectsProviderDespiteNativeCollision() throws Exception {
        Identifier nativeId = Identifier.fromString("example:wood");
        ExternalDataProvider first = provider("CraftEngine", nativeId);
        ExternalDataProvider second = provider("ItemsAdder", nativeId);
        BlockData base = mock(BlockData.class);
        when(second.getBlockData(eq(nativeId), any())).thenReturn(IrisCustomData.of(base, nativeId));
        ExternalDataSVC service = service(first, second);

        assertFalse(service.getBlockData(nativeId).isPresent());
        IrisCustomData resolved = (IrisCustomData) service.getBlockData(
                Identifier.fromString("itemsadder:example/wood")).orElseThrow();

        assertEquals("itemsadder:example/wood", resolved.getCustom().toString());
        verify(first, never()).getBlockData(any(), any());
        assertFalse(service.getBlockProperties(nativeId).isPresent());
        assertEquals(List.of(), service.getBlockProperties(Identifier.fromString("itemsadder:example/wood")).orElseThrow());
    }

    @Test
    public void missingQualifiedProviderDoesNotLeakIntoAnotherNamespaceOwner() throws Exception {
        Identifier id = Identifier.fromString("itemsadder:example/wood");
        ExternalDataProvider unrelated = provider("OtherBlocks", id);
        ExternalDataSVC service = service(unrelated);

        assertFalse(service.getBlockData(id).isPresent());
        verify(unrelated, never()).getBlockData(any(), any());
    }

    @Test
    public void qualifiedNestedKeysAndPropertiesAppearInSchemas() throws Exception {
        Identifier nativeId = Identifier.fromString("example:ores/copper");
        ExternalDataProvider provider = provider("ItemsAdder", nativeId);
        List<BlockProperty> properties = List.of(BlockProperty.ofBoolean("lit", false));
        when(provider.getBlockProperties(nativeId)).thenReturn(properties);
        BlockData base = mock(BlockData.class);
        when(provider.getBlockData(eq(nativeId), any())).thenReturn(base);
        ExternalDataSVC service = service(provider);
        Identifier qualified = Identifier.fromString("itemsadder:example/ores/copper");

        assertSame(base, service.getBlockData(qualified).orElseThrow());
        assertEquals(Set.of(nativeId, qualified), Set.copyOf(service.getAllIdentifiers(DataType.BLOCK)));
        assertEquals(properties, service.getBlockProperties(qualified).orElseThrow());
        assertEquals(2, service.getAllBlockProperties().size());
    }

    @Test
    public void nativeProviderKeysCanContainSlashes() throws Exception {
        Identifier nativeId = Identifier.fromString("oraxen:ores/copper");
        ExternalDataProvider provider = provider("Oraxen", nativeId);
        BlockData base = mock(BlockData.class);
        when(provider.getBlockData(eq(nativeId), any())).thenReturn(base);
        ExternalDataSVC service = service(provider);

        assertSame(base, service.getBlockData(nativeId).orElseThrow());
        assertSame(base, service.getBlockData(Identifier.fromString("oraxen:oraxen/ores/copper")).orElseThrow());
    }

    @Test
    public void providerFailureDoesNotEscapeIntoVanillaFallbackParsing() throws Exception {
        Identifier id = Identifier.fromString("example:stone");
        ExternalDataProvider provider = provider("ItemsAdder", id);
        when(provider.getBlockData(eq(id), any())).thenThrow(new IllegalStateException("Registry is unavailable"));
        ExternalDataSVC service = service(provider);

        assertFalse(service.getBlockData(id).isPresent());
    }

    @Test
    public void unavailableProviderDoesNotReportPlacementSuccess() throws Exception {
        ExternalDataSVC service = service();

        assertThrows(MissingResourceException.class, () -> service.processUpdate(
                mock(Engine.class), mock(Block.class), Identifier.fromString("itemsadder:example/wood")));
    }

    @Test
    public void malformedStateSuffixesFailBeforeProviderLookup() throws Exception {
        ExternalDataProvider provider = provider("ItemsAdder", Identifier.fromString("example:wood"));
        ExternalDataSVC service = service(provider);
        for (String state : List.of("wood[", "wood]", "wood[a=b]tail", "wood[a=b,]", "wood[a=b,a=c]", "wood[[a=b]]", "wood[a]")) {
            Identifier id = new Identifier("example", state);
            assertThrows(state, IllegalArgumentException.class, () -> ExternalDataSVC.parseState(id));
            assertFalse(state, service.getBlockData(id).isPresent());
        }
        verify(provider, never()).getBlockData(any(), any());
    }

    @Test
    public void stateParserPreservesDecimalsAndTrimsProperties() {
        Pair<Identifier, KMap<String, String>> parsed = ExternalDataSVC.parseState(
                Identifier.fromString("craftengine:example/chair[ yaw = 22.5 ]"));

        assertEquals(Identifier.fromString("craftengine:example/chair"), parsed.getA());
        assertEquals("22.5", parsed.getB().get("yaw"));
        assertEquals("craftengine:example/chair[yaw=22.5]", ExternalDataSVC.buildState(parsed.getA(), parsed.getB()).toString());
    }

    private static ExternalDataProvider provider(String pluginId, Identifier id) {
        ExternalDataProvider provider = mock(ExternalDataProvider.class);
        when(provider.getPluginId()).thenReturn(pluginId);
        when(provider.isValidProvider(id, DataType.BLOCK)).thenReturn(true);
        when(provider.getTypes(DataType.BLOCK)).thenReturn(List.of(id));
        when(provider.getBlockProperties(id)).thenReturn(List.of());
        return provider;
    }

    private static ExternalDataSVC service(ExternalDataProvider... providers) throws Exception {
        ExternalDataSVC service = new ExternalDataSVC();
        for (ExternalDataProvider provider : providers) {
            addProvider(service, provider);
        }
        return service;
    }

    @SuppressWarnings("unchecked")
    private static void addProvider(ExternalDataSVC service, ExternalDataProvider provider) throws Exception {
        Field field = ExternalDataSVC.class.getDeclaredField("activeProviders");
        field.setAccessible(true);
        List<ExternalDataProvider> providers = (List<ExternalDataProvider>) field.get(service);
        providers.add(provider);
    }
}
