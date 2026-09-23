package art.arcane.iris.probe;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

import java.util.List;

final class HeadlessNativeBootstrap {
    private static boolean initialized;

    private HeadlessNativeBootstrap() {
    }

    static synchronized void bindComponents(HolderLookup.Provider registries) {
        List<DataComponentInitializers.PendingComponents<?>> pending =
                BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(registries);
        for (DataComponentInitializers.PendingComponents<?> components : pending) {
            components.apply();
        }
    }

    static synchronized void initialize() {
        if (initialized) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try (MultiPackResourceManager resources = new MultiPackResourceManager(PackType.SERVER_DATA,
                List.of(ServerPacksSource.createVanillaPackSource().fullResources()))) {
            List<Registry.PendingTags<?>> pending = TagLoader.loadTagsForExistingRegistries(resources,
                    RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
            for (Registry.PendingTags<?> tags : pending) {
                tags.apply();
            }
        }
        initialized = true;
    }
}
