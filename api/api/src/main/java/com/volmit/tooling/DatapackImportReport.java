package com.volmit.tooling;

import com.volmit.resource.ResourceId;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** What {@link DatapackStructureImport#importAll} did.
 *
 * @param total           the sources it tried
 * @param imported        the sources it imported
 * @param skipped         the sources it left as they were
 * @param failed          the sources it could not import
 * @param importedBundles the bundle each fully imported source ended up as, by bundle name
 * @param retryRequired   whether a failure may pass when the import runs again, as opposed to the source being invalid
 */
public record DatapackImportReport(int total, int imported, int skipped, int failed, Map<String, ResourceId> importedBundles,
                                   boolean retryRequired) {
    public DatapackImportReport {
        Objects.requireNonNull(importedBundles, "importedBundles");
        importedBundles = java.util.Collections.unmodifiableMap(new TreeMap<>(importedBundles));
    }
}
