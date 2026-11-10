package art.arcane.iris.core.modules;

import art.arcane.iris.core.loader.IrisData;
import com.volmit.host.LoadedPack;
import com.volmit.host.ModuleHost;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * A pack opened for validation whose built-in modules are the classes of the test class path, not the built-ins jar.
 * Tests that call a built-in module's classes directly need this: a jar loads its own copy of every class, so what
 * the pack serves would not be the classes the test names.
 */
public final class ClasspathPack {
    private ClasspathPack() {
    }

    /** {@code pack} opened for validation with {@link BuiltInPack#allBuiltIns()} as its modules; close it after the test. */
    public static IrisData open(File pack, TemporaryFolder folder) throws IOException {
        return withClasspathModules(IrisData.openValidation(pack), pack, folder);
    }

    /** Like {@link #open}, opened for a running world as {@link IrisData#openRuntime} does. */
    public static IrisData openRuntime(File pack, TemporaryFolder folder) throws IOException {
        return withClasspathModules(IrisData.openRuntime(pack), pack, folder);
    }

    private static IrisData withClasspathModules(IrisData opened, File pack, TemporaryFolder folder) throws IOException {
        IrisData data = spy(opened);
        LoadedPack loaded = LoadedPack.load(pack.toPath(), ModuleHost.of(BuiltInPack.allBuiltIns())
                .exposing(IrisModuleHost.EXPOSED_PACKAGES.toArray(String[]::new))
                .reservingFields(ModuleTargets.reservedFields())
                .describingHostTypesIn(IrisModuleHost.HOST_TYPE_PACKAGES)
                .copyingJarsTo(folder.newFolder().toPath()));
        PackModuleSet modules = mock(PackModuleSet.class);
        when(modules.pack()).thenReturn(loaded);
        when(modules.settings()).thenReturn(new ModuleSettingsDecoder(loaded.catalog(), pack.toPath(), opened::getGson));
        doReturn(modules).when(data).getModules();
        data.invalidateStructureResources();
        return data;
    }
}
