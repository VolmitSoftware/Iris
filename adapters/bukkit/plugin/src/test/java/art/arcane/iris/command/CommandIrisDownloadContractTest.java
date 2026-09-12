package art.arcane.iris.command;

import art.arcane.volmlib.util.director.annotations.Param;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class CommandIrisDownloadContractTest {
    @Test
    public void commandExposesOnlyPackAndLinkParameters() throws NoSuchMethodException {
        Method method = CommandIris.class.getDeclaredMethod("download", String.class, String.class);
        Parameter[] parameters = method.getParameters();
        Param pack = parameters[0].getAnnotation(Param.class);
        Param link = parameters[1].getAnnotation(Param.class);
        List<Method> downloadMethods = Arrays.stream(CommandIris.class.getDeclaredMethods())
                .filter((Method candidate) -> candidate.getName().equals("download"))
                .toList();

        assertEquals(1, downloadMethods.size());
        assertEquals(2, downloadMethods.getFirst().getParameterCount());
        assertEquals("pack", pack.name());
        assertEquals(0L, Arrays.stream(pack.aliases()).filter((String alias) -> !alias.isBlank()).count());
        assertEquals(CommandIris.DownloadPackHandler.class, pack.customHandler());
        assertEquals("link", link.name());
        assertEquals(0L, Arrays.stream(link.aliases()).filter((String alias) -> !alias.isBlank()).count());
    }

    @Test
    public void packHandlerAcceptsOnlyTheTwoBuiltInPacks() throws Exception {
        CommandIris.DownloadPackHandler handler = new CommandIris.DownloadPackHandler();

        assertEquals("overworld", handler.parse("OVERWORLD", false));
        assertEquals("underworld", handler.parse("underworld", false));
        assertEquals(Arrays.asList("overworld", "underworld"), handler.getPossibilities());
        assertNull(handler.parse("__none__", false));
        assertThrows(Exception.class, () -> handler.parse("custom", false));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0);
        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int index = openBrace; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, index + 1);
                }
            }
        }
        throw new IllegalArgumentException("Unclosed method: " + signature);
    }
}
