package art.arcane.iris.command.handlers;

import org.junit.Test;

import static org.junit.Assert.assertNull;

public class GeneratorHandlerTest {
    @Test
    public void nullDefaultOpensTheBuiltInNoiseExplorer() throws Exception {
        assertNull(new GeneratorHandler().parse("null", true));
    }
}
