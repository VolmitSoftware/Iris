package art.arcane.iris.pack.datapack.export.compile.structure;

import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.pack.datapack.DataVersion;
import art.arcane.iris.pack.datapack.DatapackExportArchives;
import art.arcane.iris.pack.datapack.export.VanillaResourceIdentifier;
import art.arcane.iris.pack.datapack.export.compile.PackCompileContext;
import art.arcane.iris.pack.datapack.export.ir.BinaryResource;
import art.arcane.iris.pack.datapack.export.ir.ExportDiagnostic;
import art.arcane.iris.pack.datapack.export.ir.ExportLimit;
import art.arcane.iris.pack.datapack.export.ir.JsonResource;
import art.arcane.iris.structure.nativegen.IrisImportedStructureControl;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

final class DatapackBundle {
    private static final String DATA = "data/";
    private static final String TAGS = "tags";
    private static final String JSON_SUFFIX = ".json";
    private static final List<String> VANILLA_STRUCTURE_TREES = List.of(
            "data/minecraft/worldgen/structure_set/",
            "data/minecraft/worldgen/structure/",
            "data/minecraft/worldgen/template_pool/",
            "data/minecraft/structure/");

    private final SortedMap<String, byte[]> files;
    private final SortedMap<String, TagFile> tags;

    private DatapackBundle(SortedMap<String, byte[]> files, SortedMap<String, TagFile> tags) {
        this.files = files;
        this.tags = tags;
    }

    static DatapackBundle load(PackCompileContext context) {
        IrisDimension dimension = context.dimension();
        DataVersion target = context.capabilities().target();
        IrisImportedStructureControl control = dimension.getImportedStructures();
        boolean stripOverrides = control != null && !control.isDatapackOverrides();
        Set<String> claimed = claimedPaths(context);
        String reservedPrefix = DATA + context.namespace() + "/";
        String modePath = switch (context.mode()) {
            case PRESET -> "data/minecraft/tags/worldgen/world_preset/normal.json";
            case OVERWORLD -> "data/minecraft/dimension/overworld.json";
            case DIMENSION -> null;
        };
        SortedMap<String, byte[]> files = new TreeMap<>();
        SortedMap<String, TagFile> tags = new TreeMap<>();
        Map<String, String> owners = new HashMap<>();
        List<String> imports = dimension.getDatapackImports() == null ? List.of() : dimension.getDatapackImports();
        for (String source : imports) {
            if (source == null || source.isBlank()) {
                continue;
            }
            DatapackArchiveReader.Pack pack = read(source.trim(), target);
            checkFormats(context, source.trim(), pack);
            for (Map.Entry<String, byte[]> file : pack.data().entrySet()) {
                String path = file.getKey();
                if (!validDataPath(path)) {
                    diagnose(context, ExportLimit.DATAPACK_FILE, path, "Datapack import '" + source.trim()
                            + "' has a file whose path is not a valid resource location; the file is not exported");
                    continue;
                }
                if (stripOverrides && overridesVanillaStructures(path)) {
                    continue;
                }
                if (path.startsWith(reservedPrefix) || path.equals(modePath)) {
                    diagnose(context, ExportLimit.DATAPACK_COLLISION, path, "Datapack import '" + source.trim()
                            + "' writes a file the export itself writes; the export keeps its own file");
                    continue;
                }
                if (tag(path)) {
                    mergeTag(context, source.trim(), path, file.getValue(), tags);
                } else if (claimed.contains(path)) {
                    diagnose(context, ExportLimit.DATAPACK_COLLISION, path, "Datapack import '" + source.trim()
                            + "' writes a file the export itself writes; the export keeps its own file");
                } else {
                    byte[] previous = files.put(path, file.getValue());
                    if (previous != null && !Arrays.equals(previous, file.getValue())) {
                        diagnose(context, ExportLimit.DATAPACK_COLLISION, path, "Datapack import '" + source.trim()
                                + "' replaces the file from '" + owners.get(path) + "'");
                    }
                    owners.put(path, source.trim());
                }
            }
        }
        return new DatapackBundle(files, tags);
    }

    SortedMap<String, byte[]> files() {
        return files;
    }

    SortedMap<String, TagFile> tags() {
        return tags;
    }

    void emitFiles(PackCompileContext context) {
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            context.binaryResources().add(new BinaryResource(file.getKey(), file.getValue()));
        }
    }

    private static DatapackArchiveReader.Pack read(String source, DataVersion target) {
        try {
            Path archive = DatapackExportArchives.resolve(source, target);
            return DatapackArchiveReader.read(archive, target);
        } catch (IOException exception) {
            throw new IllegalStateException("Datapack import '" + source + "' could not be bundled for Minecraft "
                    + target.getVersion() + ": " + exception.getMessage(), exception);
        }
    }

    private static void checkFormats(PackCompileContext context, String source, DatapackArchiveReader.Pack pack) {
        DataVersion target = context.capabilities().target();
        if (pack.formats().isEmpty()) {
            diagnose(context, ExportLimit.DATAPACK_VERSION, source, "Datapack import declares no readable pack format range; it is bundled as is for Minecraft "
                    + target.getVersion());
        } else if (!pack.formats().get().contains(target)) {
            diagnose(context, ExportLimit.DATAPACK_VERSION, source, "Datapack import supports pack formats " + pack.formats().get().describe()
                    + " but the export targets format " + target.getPackFormat() + "." + target.getPackFormatMinor()
                    + " (Minecraft " + target.getVersion() + "); it is bundled as is");
        }
    }

    private static void mergeTag(PackCompileContext context, String source, String path, byte[] bytes, SortedMap<String, TagFile> tags) {
        TagFile tag;
        try {
            tag = TagFile.parse(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)));
        } catch (JsonParseException | IllegalArgumentException exception) {
            diagnose(context, ExportLimit.DATAPACK_FILE, path, "Datapack import '" + source
                    + "' has an unreadable tag file (" + exception.getMessage() + "); the file is not exported");
            return;
        }
        tags.merge(path, tag, TagFile::then);
    }

    private static Set<String> claimedPaths(PackCompileContext context) {
        Set<String> claimed = new HashSet<>();
        for (JsonResource resource : context.jsonResources()) {
            claimed.add(resource.path());
        }
        for (BinaryResource resource : context.binaryResources()) {
            claimed.add(resource.path());
        }
        return claimed;
    }

    private static boolean validDataPath(String path) {
        if (!path.startsWith(DATA)) {
            return false;
        }
        int separator = path.indexOf('/', DATA.length());
        return separator > DATA.length()
                && VanillaResourceIdentifier.validNamespace(path.substring(DATA.length(), separator))
                && VanillaResourceIdentifier.validPath(path.substring(separator + 1));
    }

    private static boolean tag(String path) {
        String[] segments = path.split("/");
        return segments.length > 3 && segments[2].equals(TAGS) && path.endsWith(JSON_SUFFIX);
    }

    private static boolean overridesVanillaStructures(String path) {
        for (String tree : VANILLA_STRUCTURE_TREES) {
            if (path.startsWith(tree)) {
                return true;
            }
        }
        return false;
    }

    private static void diagnose(PackCompileContext context, ExportLimit limit, String resource, String message) {
        context.diagnostics().add(new ExportDiagnostic(ExportDiagnostic.Severity.WARNING, limit, resource, message));
    }
}
