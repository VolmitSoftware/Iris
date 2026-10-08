package com.volmit.host.schema;

import com.volmit.ModuleInfo;
import com.volmit.resource.ResourceId;
import java.util.Objects;

/** A module resource kind and what its files hold, for the host to give studio a schema for every JSON file below
 * the kind's folder.
 *
 * @param id the kind's id
 * @param module the module that registered the kind
 * @param folder the folder below the pack root that holds the kind's files
 * @param type what each file holds: an {@link ValueKind.ObjectKind} naming an entry of {@link PackSchema#types()}, or
 *     a {@link ValueKind.HostKind} for a built-in kind whose class the host describes itself
 */
public record ResourceKindSchema(ResourceId id, ModuleInfo module, String folder, ValueKind type) {
    public ResourceKindSchema {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(type, "type");
        if (!(type instanceof ValueKind.ObjectKind) && !(type instanceof ValueKind.HostKind)) {
            throw new IllegalArgumentException("Resource kind " + id + " files hold an object, not " + type);
        }
    }
}
