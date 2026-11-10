package com.volmit.tooling;

import com.volmit.resource.ResourceId;
import com.volmit.service.Providers;
import com.volmit.service.Service;
import java.util.Collection;

/** Turns the structures and templates a datapack brought into editable resources of the pack, once the host installed the
 * datapack. A module that owns a kind of structure provides it; the host asks the pack's first provider and, when the pack has
 * none, leaves the datapack's structures pending. Re-importing a source replaces what an earlier import made of it.
 */
@Service(Providers.FIRST)
public interface DatapackStructureImport {
    /** Imports {@code structures} (ids of the platform's registered structures) and {@code templates} (ids of its structure
     * templates) into {@code pack}, reporting progress to {@code sender}. A source that fails does not stop the others.
     */
    DatapackImportReport importAll(ToolPack pack, PlatformStructures platform, Sender sender,
                                   Collection<ResourceId> structures, Collection<ResourceId> templates);
}
