package art.arcane.volmlib.nativelib.minecraft26_2.terrain;

import com.mojang.datafixers.util.Either;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertSame;

public class NativeStructureTemplateAccessTest {
    @BeforeClass
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    public void resolvesRuntimeTemplateThroughWidenedPoolField() {
        StructureTemplate template = new StructureTemplate();
        SinglePoolElement element = new RuntimePoolElement(template);
        assertSame(template, NativeStructureReflection.resolveTemplate(element, () -> {
            throw new AssertionError("Runtime templates must not require a template manager");
        }));
    }

    private static final class RuntimePoolElement extends SinglePoolElement {
        private RuntimePoolElement(StructureTemplate template) {
            super(Either.right(template), Holder.direct(new StructureProcessorList(List.of())),
                    StructureTemplatePool.Projection.RIGID, Optional.empty());
        }
    }
}
