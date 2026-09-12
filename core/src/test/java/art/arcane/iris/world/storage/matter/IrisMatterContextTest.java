package art.arcane.iris.world.storage.matter;

import art.arcane.iris.pack.loading.IrisData;
import org.junit.Test;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

public class IrisMatterContextTest {
    @Test
    public void nestedScopesRestoreAndClearData() {
        IrisData outerData = mock(IrisData.class);
        IrisData innerData = mock(IrisData.class);

        assertThrows(IllegalStateException.class, IrisMatterContext::require);
        try (IrisMatterContext.Scope outerScope = IrisMatterContext.open(outerData)) {
            assertSame(outerData, IrisMatterContext.require());
            try (IrisMatterContext.Scope innerScope = IrisMatterContext.open(innerData)) {
                assertSame(innerData, IrisMatterContext.require());
            }
            assertSame(outerData, IrisMatterContext.require());
        }
        assertThrows(IllegalStateException.class, IrisMatterContext::require);
    }

    @Test
    public void closingScopesOutOfOrderFails() {
        IrisMatterContext.Scope outerScope = IrisMatterContext.open(mock(IrisData.class));
        IrisMatterContext.Scope innerScope = IrisMatterContext.open(mock(IrisData.class));

        assertThrows(IllegalStateException.class, outerScope::close);
        innerScope.close();
        outerScope.close();
        assertThrows(IllegalStateException.class, IrisMatterContext::require);
    }
}
