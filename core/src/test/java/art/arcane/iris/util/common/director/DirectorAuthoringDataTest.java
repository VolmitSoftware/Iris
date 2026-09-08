package art.arcane.iris.util.common.director;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.testsupport.PlatformLeakGuard;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DirectorAuthoringDataTest {
    @ClassRule
    public static final PlatformLeakGuard PLATFORM_GUARD = PlatformLeakGuard.clean();

    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void studioCommandsWriteTheEditablePackWithoutTouchingTheEpoch() throws Exception {
        Path source = temporary.newFolder("source").toPath();
        Path epoch = temporary.newFolder("epoch").toPath();
        Files.writeString(source.resolve("object.iob"), "editable");
        Files.writeString(epoch.resolve("object.iob"), "retained");
        Engine engine = mock(Engine.class);
        IrisData editable = mock(IrisData.class);
        IrisData retained = mock(IrisData.class);
        when(engine.isStudio()).thenReturn(true);
        when(engine.getPackSource()).thenReturn(source);
        when(engine.getData()).thenReturn(retained);
        when(editable.getDataFolder()).thenReturn(source.toFile());
        when(retained.getDataFolder()).thenReturn(epoch.toFile());
        DirectorExecutor command = mock(DirectorExecutor.class, CALLS_REAL_METHODS);
        when(command.engine()).thenReturn(engine);
        try (MockedStatic<IrisData> data = mockStatic(IrisData.class)) {
            data.when(() -> IrisData.get(source.toFile())).thenReturn(editable);
            IrisData selected = command.data();
            assertSame(editable, selected);
            Files.writeString(selected.getDataFolder().toPath().resolve("object.iob"), "edited");
            assertEquals("edited", Files.readString(source.resolve("object.iob")));
            assertEquals("retained", Files.readString(epoch.resolve("object.iob")));
            data.verify(() -> IrisData.get(epoch.toFile()), never());
        }
        verify(engine, never()).getData();
    }

    @Test
    public void productionCommandsResolveOnlyAnUnambiguousInstalledSource() throws Exception {
        File packs = temporary.newFolder("packs");
        File first = new File(packs, "first");
        File second = new File(packs, "second");
        Engine engine = mock(Engine.class);
        IrisDimension dimension = mock(IrisDimension.class);
        IrisPlatform platform = mock(IrisPlatform.class);
        IrisData firstData = mock(IrisData.class, RETURNS_DEEP_STUBS);
        IrisData secondData = mock(IrisData.class, RETURNS_DEEP_STUBS);
        when(engine.getDimension()).thenReturn(dimension);
        when(dimension.getLoadKey()).thenReturn("overworld");
        when(platform.packsFolder()).thenReturn(packs);
        when(firstData.getDimensionLoader().getPossibleKeys()).thenReturn(new String[]{"overworld"});
        when(secondData.getDimensionLoader().getPossibleKeys()).thenReturn(new String[]{"underworld"});
        try (MockedStatic<IrisData> data = mockStatic(IrisData.class);
             MockedStatic<IrisPlatforms> platforms = mockStatic(IrisPlatforms.class);
             MockedStatic<PackDirectoryResolver> directories = mockStatic(PackDirectoryResolver.class)) {
            platforms.when(IrisPlatforms::get).thenReturn(platform);
            directories.when(() -> PackDirectoryResolver.listVisiblePackDirectories(packs)).thenReturn(List.of(first, second));
            data.when(() -> IrisData.get(first)).thenReturn(firstData);
            data.when(() -> IrisData.get(second)).thenReturn(secondData);
            assertSame(firstData, DirectorExecutor.authoringData(engine));
            when(secondData.getDimensionLoader().getPossibleKeys()).thenReturn(new String[]{"overworld"});
            assertNull(DirectorExecutor.authoringData(engine));
            when(firstData.getDimensionLoader().getPossibleKeys()).thenReturn(new String[]{"other"});
            when(secondData.getDimensionLoader().getPossibleKeys()).thenReturn(new String[]{"underworld"});
            assertNull(DirectorExecutor.authoringData(engine));
        }
        verify(engine, never()).getData();
        verify(engine, never()).getPackSource();
    }
}
