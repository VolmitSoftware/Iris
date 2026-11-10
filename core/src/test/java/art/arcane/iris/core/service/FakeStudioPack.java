package art.arcane.iris.core.service;

import art.arcane.iris.engine.framework.structure.StructureResources;
import art.arcane.iris.engine.object.structure.jigsaw.IrisJigsawPiece;
import com.volmit.block.BlockRegistry;
import com.volmit.geometry.BlockBox;
import com.volmit.geometry.BlockPosition;
import com.volmit.object.GeneratedObject;
import com.volmit.object.PackObjects;
import com.volmit.object.PlaceableObject;
import com.volmit.resource.PackResources;
import com.volmit.resource.ResourceId;
import com.volmit.tooling.ToolPack;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A pack whose pieces and objects a test sets by key, over a block registry that knows every block. */
final class FakeStudioPack {
    private static final String NAMESPACE = "test";

    final ToolPack pack = mock(ToolPack.class);
    final BlockRegistry blocks = FakeBlockRegistry.create();
    private final PackResources resources = mock(PackResources.class);
    private final PackObjects objects = mock(PackObjects.class);

    FakeStudioPack() {
        when(pack.resources()).thenReturn(resources);
        when(resources.objects()).thenReturn(objects);
        when(resources.blocks()).thenReturn(blocks);
        when(resources.idOf(anyString())).thenAnswer(invocation -> idOf(invocation.getArgument(0)));
    }

    FakeStudioPack piece(String key, IrisJigsawPiece piece) {
        when(resources.find(StructureResources.PIECES, idOf(key))).thenReturn(Optional.of(piece));
        return this;
    }

    /** An object without blocks of the given size, kept under {@code key}. */
    FakeStudioPack emptyObject(String key, int width, int height, int depth) {
        when(objects.object(idOf(key))).thenReturn(Optional.of(new EmptyObject(width, height, depth)));
        return this;
    }

    private static ResourceId idOf(String key) {
        return ResourceId.ofPackKey(NAMESPACE, key);
    }

    private record EmptyObject(int width, int height, int depth) implements PlaceableObject {
        @Override
        public String name() {
            return "empty";
        }

        @Override
        public BlockPosition center() {
            return new BlockPosition(width / 2, height / 2, depth / 2);
        }

        @Override
        public GeneratedObject geometry() {
            return new GeneratedObject(new BlockBox(0, 0, 0, 0, 0, 0), new BlockPosition(0, 0, 0), List.of(), List.of());
        }
    }
}
