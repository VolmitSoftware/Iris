package art.arcane.iris.pack;

import art.arcane.volmlib.util.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PackStaticObjectValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsAbsentEmptyAndCompleteStaticObjects() throws Exception {
        File pack = pack();
        assertEquals(List.of(), validate(pack, "{}"));
        assertEquals(List.of(), validate(pack, "{\"staticObjects\":[]}"));
        assertEquals(List.of(), validate(pack, """
                {"staticObjects":[{
                  "object":"landmarks/tower","position":{"x":100,"y":100,"z":-100},
                  "rotation":{"x":-22.5,"y":90,"z":-180},"scale":0.5,
                  "scaleInterpolation":"TRILINEAR","seed":9223372036854775807,"bore":true,"smartBore":true,
                  "edit":[{"find":[{"block":"stone"}],"replace":{"palette":[{"block":"gold_block"}]}}]
                }]}
                """));
    }

    @Test
    public void rejectsMalformedContainersMissingObjectsAndMissingOrigins() throws Exception {
        File pack = pack();
        assertEquals(List.of("Dimension 'main'.staticObjects must be an array."),
                validate(pack, "{\"staticObjects\":null}"));
        assertEquals(List.of("Dimension 'main'.staticObjects[0] must be an object."),
                validate(pack, "{\"staticObjects\":[null]}"));
        List<String> errors = validate(pack, "{\"staticObjects\":[{\"object\":\"missing\"}]}");
        assertEquals(2, errors.size());
        assertContains(errors, ".object references missing object 'missing'");
        assertContains(errors, ".position must be an object");
    }

    @Test
    public void checksAllScalarTypesAndLimits() throws Exception {
        List<String> errors = validate(pack(), """
                {"staticObjects":[{
                  "object":"landmarks/tower","position":{"x":29999985,"y":320,"z":0.5},
                  "rotation":{"x":null,"y":-361,"z":"90"},"scale":0,
                  "scaleInterpolation":"CUBIC","seed":1.5,"bore":null,"smartBore":"true","edit":null
                }]}
                """);
        assertEquals(12, errors.size());
        assertContains(errors, ".position.y must be an integer between -64 and 319");
        assertContains(errors, ".rotation.y must be finite");
        assertContains(errors, ".seed must be an integer");
    }

    @Test
    public void resolvesHeightPositionAndReplacementSnippets() throws Exception {
        File pack = pack();
        write(pack, "snippet/range/tall.json", "{\"min\":-128,\"max\":512}");
        write(pack, "snippet/position-3d/tower.json", "{\"x\":100,\"y\":-128,\"z\":-100}");
        write(pack, "snippet/object-block-replacer/gilded.json",
                "{\"find\":[{\"block\":\"stone\"}],\"replace\":\"snippet/gold\"}");
        write(pack, "snippet/palette/gold.json", "{\"palette\":[{\"block\":\"gold_block\"}]}");

        assertEquals(List.of(), validate(pack, """
                {"dimensionHeight":"snippet/tall","staticObjects":[{
                  "object":"landmarks/tower","position":"snippet/tower","edit":["snippet/gilded"]
                }]}
                """));
    }

    @Test
    public void rejectsInvalidEditsAndUnresolvedSnippets() throws Exception {
        List<String> errors = validate(pack(), """
                {"staticObjects":[{
                  "object":"landmarks/tower","position":"snippet/missing",
                  "edit":[null,{"find":[],"replace":{"palette":[]},"chance":2,"exact":"yes"}]
                }]}
                """);
        assertEquals(6, errors.size());
        assertContains(errors, "references missing position-3d snippet");
        assertContains(errors, ".edit[1].find must be a nonempty array");
        assertContains(errors, ".edit[1].replace.palette must be a nonempty array");
    }

    @Test
    public void dimensionAdmissionRejectsMissingStaticObjectsBeforeRegions() throws Exception {
        File pack = pack();
        write(pack, "dimensions/main.json", """
                {"regions":[],"staticObjects":[{"object":"missing","position":{"y":100}}]}
                """);
        List<String> errors = new ArrayList<>();
        File dimension = new File(pack, "dimensions/main.json");

        PackDimensionValidator.validateDimensions(pack, new File[]{dimension}, errors, new ArrayList<>());

        assertContains(errors, ".object references missing object 'missing'");
        assertContains(errors, "declares no regions");
    }

    private File pack() throws Exception {
        File pack = temporaryFolder.newFolder();
        write(pack, "objects/landmarks/tower.iob", "");
        return pack;
    }

    private static List<String> validate(File pack, String json) {
        List<String> errors = new ArrayList<>();
        PackDimensionValidator.validateStaticObjects(pack, "main", new JSONObject(json), errors);
        return errors;
    }

    private static void assertContains(List<String> errors, String fragment) {
        assertTrue(errors.toString(), errors.stream().anyMatch(error -> error.contains(fragment)));
    }

    private static void write(File root, String relative, String content) throws Exception {
        Path path = root.toPath().resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
