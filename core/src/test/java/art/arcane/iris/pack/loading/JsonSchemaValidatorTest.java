package art.arcane.iris.pack.loading;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

public class JsonSchemaValidatorTest {
    private static class Resource {
        int known;
    }

    @Test
    public void unknownKeyIsWarnedAbout() {
        List<String> warnings = warningsFor("{\"known\":1,\"mystery\":2}");

        assertEquals(warnings.toString(), 1, warnings.size());
        assertTrue(warnings.get(0).contains("\"mystery\""));
    }

    @Test
    public void removedKeyThatPackValidationReportsIsNotWarnedAboutAgain() {
        List<String> warnings = warningsFor("{\"known\":1,\"" + RemovedPackKeys.CAVE_PROFILE + "\":{}}");

        assertTrue(warnings.toString(), warnings.isEmpty());
    }

    @Test
    public void removedKeyDoesNotHideOtherUnknownKeys() {
        List<String> warnings = warningsFor("{\"" + RemovedPackKeys.CAVE_PROFILE + "\":{},\"mystery\":2}");

        assertEquals(warnings.toString(), 1, warnings.size());
        assertTrue(warnings.get(0).contains("\"mystery\""));
    }

    private List<String> warningsFor(String rawText) {
        try (MockedStatic<IrisLogging> logging = mockStatic(IrisLogging.class)) {
            JsonSchemaValidator.validateTopLevelKeys(new JSONObject(rawText), rawText, new File("dimensions/main.json"), "Dimension", Resource.class);
            ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
            logging.verify(() -> IrisLogging.warn(message.capture(), any(Object[].class)), org.mockito.Mockito.atLeast(0));
            return message.getAllValues();
        }
    }
}
