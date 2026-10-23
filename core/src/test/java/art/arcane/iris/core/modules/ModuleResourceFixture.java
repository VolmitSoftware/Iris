package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import com.volmit.IrisModule;
import com.volmit.host.DiscoveredModule;
import com.volmit.host.FeatureCatalog;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleRegistry;
import com.volmit.host.ModuleSetups;
import com.volmit.host.StageGraphBuilder;
import com.volmit.resource.ResourceId;
import com.volmit.resource.ResourceKind;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A pack whose module resources (structures, jigsaw pools, pieces or any other kind) a test sets by key, served by a
 * mocked {@link IrisPackResources} the way lookups by pack key find them, such as
 * {@code StructureResources.structure(data, key)}: ids in namespace {@code test}, a key absent unless set.
 */
public final class ModuleResourceFixture {
    private static final String NAMESPACE = "test";
    private static final Map<IrisPackResources, ModuleResourceFixture> SERVED = new IdentityHashMap<>();

    private final IrisPackResources resources;
    private final Map<ResourceKind<?>, List<ResourceId>> ids = new HashMap<>();

    private ModuleResourceFixture(IrisPackResources resources) {
        this.resources = resources;
    }

    /** Makes {@code data} (a mock) serve the structures, pools and pieces set on the returned fixture, none at first. */
    public static synchronized ModuleResourceFixture serve(IrisData data) {
        IrisPackResources resources = mock(IrisPackResources.class);
        when(data.getPackResources()).thenReturn(resources);
        when(resources.idOf(anyString())).thenAnswer(invocation -> idOf(invocation.getArgument(0)));
        when(resources.keyOf(any(ResourceId.class))).thenAnswer(invocation -> keyOf(invocation.getArgument(0)));
        when(resources.keyOf(any(ResourceKind.class), any(ResourceId.class))).thenAnswer(invocation -> keyOf(invocation.getArgument(1)));
        ModuleResourceFixture fixture = new ModuleResourceFixture(resources);
        SERVED.put(resources, fixture);
        return fixture;
    }

    /** The fixture {@code data} (a mock) already serves from, or a new one. */
    public static synchronized ModuleResourceFixture of(IrisData data) {
        ModuleResourceFixture served = SERVED.get(data.getPackResources());
        return served != null ? served : serve(data);
    }

    /**
     * Makes {@code data} (a mock) a pack folder named {@code pack} whose modules are {@code modules}, as built-ins, so what
     * reads the pack's catalog (vanilla structure hooks, validators) finds their registrations.
     */
    public static void serveModules(IrisData data, IrisModule... modules) {
        LoadedPack pack = mock(LoadedPack.class);
        PackModuleSet moduleSet = mock(PackModuleSet.class);
        FeatureCatalog catalog = catalogOf(modules);
        when(pack.catalog()).thenReturn(catalog);
        when(moduleSet.pack()).thenReturn(pack);
        when(data.getModules()).thenReturn(moduleSet);
        when(data.getDataFolder()).thenReturn(new File("pack"));
    }

    /** The catalog of a pack with only {@code modules}, as built-ins: their resource kinds, validators and other registrations. */
    public static FeatureCatalog catalogOf(IrisModule... modules) {
        ModuleRegistry registry = new ModuleRegistry();
        for (IrisModule module : modules) {
            ModuleSetups.run(DiscoveredModule.builtIn(module), new StageGraphBuilder(), registry);
        }
        return registry.freeze();
    }

    /** Serves {@code resource} as the resource of {@code kind} the pack names by {@code key}. */
    public <T> ModuleResourceFixture resource(ResourceKind<T> kind, String key, T resource) {
        ResourceId id = idOf(key);
        when(resources.find(kind, id)).thenReturn(Optional.of(resource));
        List<ResourceId> kindIds = ids.computeIfAbsent(kind, ignored -> new ArrayList<>());
        kindIds.add(id);
        when(resources.ids(kind)).thenReturn(kindIds.stream().sorted().toList());
        return this;
    }

    private static ResourceId idOf(String key) {
        return new ResourceId(NAMESPACE, key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_./-]", "_"));
    }

    private static String keyOf(ResourceId id) {
        return id.namespace().equals(NAMESPACE) ? id.path() : id.toString();
    }
}
