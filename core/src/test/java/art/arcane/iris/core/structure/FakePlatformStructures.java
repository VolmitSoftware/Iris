package art.arcane.iris.core.structure;

import com.volmit.resource.ResourceId;
import com.volmit.tooling.JigsawStructureInfo;
import com.volmit.tooling.PlatformStructures;
import com.volmit.tooling.Sender;
import com.volmit.tooling.StructureTemplate;
import com.volmit.tooling.TemplatePool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** A platform with exactly the structures, templates and pools a test gives it, and no way to capture. */
public final class FakePlatformStructures implements PlatformStructures {
    private final List<ResourceId> structures = new ArrayList<>();
    private final List<ResourceId> structureSets = new ArrayList<>();
    private final Map<ResourceId, StructureTemplate> templates = new LinkedHashMap<>();
    private final Map<ResourceId, JigsawStructureInfo> jigsaws = new LinkedHashMap<>();
    private final Map<ResourceId, TemplatePool> pools = new LinkedHashMap<>();

    public FakePlatformStructures withStructures(String... keys) {
        for (String key : keys) {
            structures.add(ResourceId.parse(key));
        }
        return this;
    }

    public FakePlatformStructures withStructureSets(String... keys) {
        for (String key : keys) {
            structureSets.add(ResourceId.parse(key));
        }
        return this;
    }

    public FakePlatformStructures withTemplate(String key, StructureTemplate template) {
        templates.put(ResourceId.parse(key), template);
        return this;
    }

    @Override
    public List<ResourceId> templates() {
        return new ArrayList<>(templates.keySet());
    }

    @Override
    public Optional<StructureTemplate> template(ResourceId id) {
        return Optional.ofNullable(templates.get(id));
    }

    @Override
    public List<ResourceId> structures() {
        return List.copyOf(structures);
    }

    @Override
    public List<ResourceId> structureSets() {
        return List.copyOf(structureSets);
    }

    @Override
    public Optional<JigsawStructureInfo> jigsaw(ResourceId structure) {
        return Optional.ofNullable(jigsaws.get(structure));
    }

    @Override
    public Optional<TemplatePool> pool(ResourceId pool) {
        return Optional.ofNullable(pools.get(pool));
    }

    @Override
    public boolean supportsCapture() {
        return false;
    }

    @Override
    public Optional<CaptureArea> openCaptureArea(Sender sender) {
        return Optional.empty();
    }
}
