package art.arcane.iris.core.project;

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.MeteredCache;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.Answers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisCodeWorkspaceTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private IrisPlatform previousPlatform;
    private IrisSettings previousSettings;
    private IrisData data;

    @Before
    public void bindPlatform() {
        previousPlatform = IrisPlatforms.isBound() ? IrisPlatforms.get() : null;
        previousSettings = IrisSettings.settings;
        IrisPlatforms.unbind();
        IrisPlatform platform = mock(IrisPlatform.class, Answers.CALLS_REAL_METHODS);
        PlatformRegistries registries = mock(PlatformRegistries.class);
        when(platform.dataFolder()).thenReturn(temporaryFolder.getRoot());
        when(platform.registries()).thenReturn(registries);
        IrisPlatforms.bind(platform);
        IrisSettings.settings = new IrisSettings();
        IrisServices.register(PreservationRegistry.class, new NoOpPreservationRegistry());
    }

    @After
    public void restorePlatform() {
        if (data != null) {
            data.close();
            data = null;
        }
        IrisServices.remove(PreservationRegistry.class);
        IrisPlatforms.unbind();
        if (previousPlatform != null) {
            IrisPlatforms.bind(previousPlatform);
        }
        IrisSettings.settings = previousSettings;
    }

    @Test
    public void updateWorkspaceDoesNotRewriteAnUnchangedWorkspaceFile() throws Exception {
        File pack = temporaryFolder.newFolder("overworld");
        IrisCodeWorkspace workspace = new IrisCodeWorkspace(new IrisProject(pack));

        assertTrue(workspace.updateWorkspace());
        data = IrisData.get(pack);
        File file = workspace.getCodeWorkspaceFile();
        byte[] first = Files.readAllBytes(file.toPath());
        FileTime stamp = FileTime.fromMillis(Files.getLastModifiedTime(file.toPath()).toMillis() - 60_000L);
        Files.setLastModifiedTime(file.toPath(), stamp);

        assertTrue(workspace.updateWorkspace());

        assertArrayEquals("Unchanged workspace bytes must stay identical",
                first, Files.readAllBytes(file.toPath()));
        assertEquals("Unchanged workspace content must not be rewritten",
                stamp.toMillis(), Files.getLastModifiedTime(file.toPath()).toMillis());
    }

    @Test
    public void workspaceSchemaEntriesAreEmittedInStableSortedOrder() throws Exception {
        File pack = temporaryFolder.newFolder("sorted");
        JSONObject configuration = new IrisCodeWorkspace(new IrisProject(pack)).createCodeWorkspaceConfig();
        data = IrisData.get(pack);

        JSONArray schemas = configuration.getJSONObject("settings").getJSONArray("json.schemas");
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < schemas.length(); i++) {
            urls.add(schemas.getJSONObject(i).getString("url"));
        }
        List<String> sorted = new ArrayList<>(urls);
        Collections.sort(sorted);

        assertFalse("Expected the workspace to declare schemas", urls.isEmpty());
        assertEquals("Schema entries must be emitted in a boot-stable order", sorted, urls);
    }

    @Test
    public void studioOpenCreatesAndRefreshesCanonicalWorkspaceAndMaterializesSchemas() throws Exception {
        File pack = temporaryFolder.newFolder("studio-pack");
        File canonicalWorkspace = new File(pack, "studio-pack.code-workspace");
        File unrelatedWorkspace = new File(pack, "unrelated.code-workspace");
        File intellijMappings = new File(pack, ".idea/jsonSchemas.xml");
        Files.writeString(unrelatedWorkspace.toPath(), "unrelated", StandardCharsets.UTF_8);
        Files.createDirectories(intellijMappings.toPath().getParent());
        Files.writeString(intellijMappings.toPath(), "not xml", StandardCharsets.UTF_8);
        IrisSettings.get().getStudio().setOpenVSCode(false);

        IrisCodeWorkspace workspace = new IrisCodeWorkspace(new IrisProject(pack));
        workspace.prepareAndOpenVSCode();
        data = IrisData.get(pack);

        JSONArray schemas = assertWorkspaceSchemasMaterialized(pack, canonicalWorkspace);
        String firstSchemaPath = schemas.getJSONObject(0).getString("url").substring(2);
        Files.writeString(canonicalWorkspace.toPath(), "{\"settings\":{\"json.schemas\":[]}}", StandardCharsets.UTF_8);
        Files.delete(new File(pack, firstSchemaPath).toPath());

        workspace.prepareAndOpenVSCode();
        assertWorkspaceSchemasMaterialized(pack, canonicalWorkspace);
        assertEquals("Studio open must target only the canonical workspace", "unrelated",
                Files.readString(unrelatedWorkspace.toPath(), StandardCharsets.UTF_8));
    }

    private static JSONArray assertWorkspaceSchemasMaterialized(File pack, File canonicalWorkspace) throws Exception {
        JSONObject configuration = new JSONObject(Files.readString(canonicalWorkspace.toPath(), StandardCharsets.UTF_8));
        JSONArray schemas = configuration.getJSONObject("settings").getJSONArray("json.schemas");
        assertTrue("Studio open must declare schemas", schemas.length() > 0);
        for (int index = 0; index < schemas.length(); index++) {
            String relativePath = schemas.getJSONObject(index).getString("url").substring(2);
            assertTrue("Studio open must write schema " + relativePath, new File(pack, relativePath).isFile());
        }
        return schemas;
    }

    private static final class NoOpPreservationRegistry implements PreservationRegistry {
        @Override
        public void register(Thread thread) {
        }

        @Override
        public void register(ExecutorService service) {
        }

        @Override
        public void registerCache(MeteredCache cache) {
        }

        @Override
        public void dereference() {
        }
    }
}
