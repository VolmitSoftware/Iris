package art.arcane.iris.pack.datapack.export;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class WorldgenExportServices {
    private static final AtomicReference<WorldgenExportService> SERVICE = new AtomicReference<>();

    private WorldgenExportServices() {
    }

    public static void install(WorldgenExportService service) {
        SERVICE.set(Objects.requireNonNull(service, "Datapack export service"));
    }

    public static WorldgenExportService get() {
        WorldgenExportService service = SERVICE.get();
        if (service != null) {
            return service;
        }
        return SERVICE.updateAndGet(current -> current == null ? WorldgenDatapackExporter.create() : current);
    }
}
