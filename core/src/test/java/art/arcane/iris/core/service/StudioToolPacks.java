package art.arcane.iris.core.service;

import com.volmit.resource.PackResources;
import com.volmit.tooling.PackEdit;
import com.volmit.tooling.ToolPack;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A pack of tool tests that runs every edit on the folder {@code pack}. */
public final class StudioToolPacks {
    private static final Path PACK_FOLDER = Path.of("pack");

    private StudioToolPacks() {
    }

    public static ToolPack toolPack(PackResources resources) {
        ToolPack pack = toolPack();
        when(pack.resources()).thenReturn(resources);
        return pack;
    }

    @SuppressWarnings("unchecked")
    public static ToolPack toolPack() {
        ToolPack pack = mock(ToolPack.class);
        try {
            when(pack.edit(any(PackEdit.class))).thenAnswer(invocation -> ((PackEdit<Object>) invocation.getArgument(0)).apply(PACK_FOLDER));
        } catch (java.io.IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return pack;
    }
}
