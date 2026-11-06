package art.arcane.iris.core.modules;

import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StructureVerificationContractTest {
    private static String verificationSource() throws IOException {
        return Files.readString(Path.of("src/main/java/art/arcane/iris/core/modules/StructureVerification.java"))
                .replace("\r\n", "\n");
    }

    @Test
    public void reportsDensityLimitAndUsesExactLocatedOrigin() throws IOException {
        String source = verificationSource();

        assertTrue(source.contains("[iris-search-limit]"));
        assertTrue(source.contains("density searches safety-limited"));
        assertTrue(source.contains("result.x() + \",\" + result.y() + \",\" + result.z()"));
        assertFalse(source.contains("at[0] + 8"));
        assertFalse(source.contains("at[2] + 8"));
    }

    @Test
    public void partitionsPolicyBeforeNativeReachability() throws IOException {
        String source = verificationSource();
        int methodStart = source.indexOf("private Set<String> reachableNativeKeys(");
        int methodEnd = source.indexOf("private void verify(", methodStart);
        String method = source.substring(methodStart, methodEnd);
        int placedCheck = method.indexOf("!ModuleServices.placesStructure(engine, key)");
        int policyResolution = method.indexOf("NativeStructureGenerationPolicy.resolve(engine, key) == NativeStructureGenerationStatus.GENERATE_NATIVE", placedCheck);
        int reachabilityLookup = method.indexOf("StructureReachability.reachableKeys(engine)", policyResolution);

        assertTrue(placedCheck >= 0);
        assertTrue(policyResolution > placedCheck);
        assertTrue(reachabilityLookup > policyResolution);
    }
}
