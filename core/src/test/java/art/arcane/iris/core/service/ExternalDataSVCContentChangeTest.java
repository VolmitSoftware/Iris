package art.arcane.iris.core.service;

import art.arcane.iris.core.link.ExternalDataProvider;
import art.arcane.iris.core.link.Identifier;
import art.arcane.iris.core.link.data.DataType;
import org.junit.Test;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ExternalDataSVCContentChangeTest {
    @Test
    public void enabledPluginIsPendingUntilItsProviderActivates() {
        ExternalDataSVC service = new ExternalDataSVC();
        Plugin plugin = mock(Plugin.class);
        PluginManager plugins = mock(PluginManager.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugins.getPlugin("ItemsAdder")).thenReturn(plugin);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);

            assertTrue(service.hasPendingBlockProvider("itemsadder:example/stone"));
            assertTrue(service.hasPendingBlockProvider("example:stone"));
            assertFalse(service.hasPendingBlockProvider("minecraft:stone"));
            assertFalse(service.hasPendingBlockProvider("oraxen:oraxen/stone"));
        }
    }

    @Test
    public void contentChangesAdvanceTheRevisionWithoutAListener() {
        ExternalDataSVC service = new ExternalDataSVC();
        long revision = service.contentRevision();

        service.notifyContentChanged();

        assertEquals(revision + 1L, service.contentRevision());
    }

    @Test
    public void activationAndRegistryReloadNotifyTheListener() {
        ExternalDataSVC service = new ExternalDataSVC();
        service.notifyContentChanged();
        AtomicInteger changes = new AtomicInteger();
        service.setContentChangeListener(changes::incrementAndGet);
        ExternalDataProvider provider = enabledProvider("TestBlocks");

        service.registerProvider(provider);
        assertEquals(1, changes.get());
        service.notifyContentChanged();
        assertEquals(2, changes.get());
        service.onDisable();
        service.notifyContentChanged();
        assertEquals(2, changes.get());
    }

    @Test
    public void unavailableRegistryDoesNotHideOtherProviderBlocks() {
        ExternalDataSVC service = new ExternalDataSVC();
        ExternalDataProvider loading = enabledProvider("LoadingBlocks");
        when(loading.getTypes(DataType.BLOCK)).thenThrow(new IllegalStateException("Content is still loading"));
        service.registerProvider(loading);
        ExternalDataProvider ready = enabledProvider("ReadyBlocks");
        when(ready.getTypes(DataType.BLOCK)).thenReturn(List.of(Identifier.fromString("example:stone")));
        service.registerProvider(ready);

        assertEquals(Set.of(Identifier.fromString("example:stone"), Identifier.fromString("readyblocks:example/stone")),
                Set.copyOf(service.getAllIdentifiers(DataType.BLOCK)));
    }

    @Test
    public void delayedRegistryInitializesBeforeItsContentIsReady() {
        ExternalDataSVC service = new ExternalDataSVC();
        ExternalDataProvider provider = enabledProvider("DelayedBlocks");
        when(provider.isReady()).thenReturn(false);
        when(provider.getTypes(DataType.BLOCK)).thenReturn(List.of());

        service.registerProvider(provider);

        verify(provider).init();
        assertTrue(service.hasPendingBlockProvider("delayedblocks:example/stone"));
        assertTrue(service.getAllIdentifiers(DataType.BLOCK).isEmpty());

        Identifier identifier = Identifier.fromString("example:stone");
        when(provider.isReady()).thenReturn(true);
        when(provider.getTypes(DataType.BLOCK)).thenReturn(List.of(identifier));
        service.notifyContentChanged();

        assertFalse(service.hasPendingBlockProvider("delayedblocks:example/stone"));
        assertEquals(Set.of(identifier, Identifier.fromString("delayedblocks:example/stone")),
                Set.copyOf(service.getAllIdentifiers(DataType.BLOCK)));
    }

    private static ExternalDataProvider enabledProvider(String pluginId) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        ExternalDataProvider provider = mock(ExternalDataProvider.class);
        when(provider.getPluginId()).thenReturn(pluginId);
        when(provider.getPlugin()).thenReturn(plugin);
        when(provider.isReady()).thenReturn(true);
        return provider;
    }
}
