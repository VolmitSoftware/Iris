package art.arcane.iris.command;

import art.arcane.volmlib.util.director.annotations.Param;
import org.bukkit.World;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CommandIrisRemovalCommandContractTest {
    @Test
    public void removalAcceptsDiskOnlyNamesAndUsesManagedSuggestions() throws NoSuchMethodException {
        Method command = CommandIris.class.getDeclaredMethod("remove", String.class, boolean.class);
        Parameter worldParameter = command.getParameters()[0];
        Param world = worldParameter.getAnnotation(Param.class);

        assertEquals(CommandIris.ManagedWorldNameHandler.class, world.customHandler());
        assertFalse(Arrays.stream(CommandIris.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("remove")
                        && method.getParameterCount() == 2
                        && method.getParameterTypes()[0] == World.class));
    }
}
