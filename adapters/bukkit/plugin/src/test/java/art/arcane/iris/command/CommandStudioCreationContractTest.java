package art.arcane.iris.command;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.command.specialhandlers.NullableDimensionHandler;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CommandStudioCreationContractTest {
    @Test
    public void createRunsAsynchronouslyAndTemplateIsOptional() throws NoSuchMethodException {
        Method command = CommandStudio.class.getDeclaredMethod("create", String.class, IrisDimension.class);
        Director director = command.getAnnotation(Director.class);
        Parameter templateParameter = command.getParameters()[1];
        Param template = templateParameter.getAnnotation(Param.class);

        assertFalse(director.sync());
        assertEquals("null", template.defaultValue());
        assertEquals(NullableDimensionHandler.class, template.customHandler());
    }

    @Test
    public void openExposesAnOptInForceFlag() throws NoSuchMethodException {
        Method command = CommandStudio.class.getDeclaredMethod(
                "open",
                IrisDimension.class,
                long.class,
                boolean.class
        );
        Param force = command.getParameters()[2].getAnnotation(Param.class);

        assertEquals("false", force.defaultValue());
        assertTrue(List.of(force.aliases()).contains("f"));
    }
}
