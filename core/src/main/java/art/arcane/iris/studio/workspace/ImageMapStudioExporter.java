package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.loading.IrisData;
import art.arcane.iris.pack.PackValidationResult;
import art.arcane.iris.pack.PackValidator;
import art.arcane.iris.generation.image.CompiledIrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapCompiler;
import art.arcane.iris.generation.image.IrisImageMapRuntime;
import art.arcane.iris.generation.image.IrisImageMapMaskSampler;
import art.arcane.iris.generation.image.IrisImageMapValidationException;
import art.arcane.iris.generation.terrain.IrisDimension;
import art.arcane.iris.generation.image.IrisImage;
import art.arcane.iris.generation.image.IrisImageMap;
import art.arcane.iris.generation.image.IrisImageMapApplication;
import art.arcane.iris.generation.image.IrisImageMapBinding;
import art.arcane.iris.generation.image.IrisImageMapMask;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.json.JSONObject;
import com.google.gson.Gson;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public final class ImageMapStudioExporter {
    private static final Pattern RESOURCE_KEY = Pattern.compile("[a-z0-9][a-z0-9/._-]*");

    private ImageMapStudioExporter() {
    }

    public static ExportResult export(ExportRequest request) throws IOException {
        Objects.requireNonNull(request, "Image-map Studio export request");
        Path pack = requirePack(request.packFolder());
        String dimensionKey = requireKey(request.dimensionKey(), "Dimension key");
        String bindingKey = requireKey(request.bindingKey(), "Binding key");
        String imageMapKey = requireKey(request.imageMapKey(), "Image-map key");
        String imageKey = requireKey(request.imageKey(), "Image key");
        IrisImageMapApplication application = Objects.requireNonNull(
                request.application(), "Image-map application"
        );
        IrisImageMap definition = Objects.requireNonNull(request.definition(), "Image-map definition");
        Path source = Objects.requireNonNull(request.sourcePng(), "Source PNG").toAbsolutePath().normalize();
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
            throw new IOException("Source PNG is missing or unsafe: " + source);
        }

        DecodedSource decoded = decodePng(source);
        definition.setSource(imageKey);
        CompiledIrisImageMap compiled = CompiledIrisImageMap.compile(
                definition, new IrisImage(decoded.image(), decoded.format())
        );

        Path dimensionTarget = safeTarget(pack, "dimensions", dimensionKey + ".json");
        Path mapTarget = safeTarget(pack, "image-maps", imageMapKey + ".json");
        Path imageTarget = safeTarget(pack, "images", imageKey + ".png");
        IrisDimension dimension;
        String mapJson;
        String dimensionJson;
        IrisData data = IrisData.openDatapackCompiler(pack.toFile());
        try {
            dimension = data.getDimensionLoader().load(dimensionKey);
            if (dimension == null) {
                throw new IOException("Dimension resource '" + dimensionKey + "' could not be loaded from " + pack);
            }
            IrisDimension updated = data.getGson().fromJson(data.getGson().toJson(dimension), IrisDimension.class);
            bind(updated, bindingKey, imageMapKey, application, request.masks());
            Gson gson = new Gson();
            mapJson = new JSONObject(gson.toJson(definition)).toString(4);
            dimensionJson = new JSONObject(gson.toJson(updated)).toString(4);
        } finally {
            data.close();
        }

        AtomicFileSet files = new AtomicFileSet();
        try {
            files.stageCopy(source, imageTarget);
            files.stageText(mapJson, mapTarget);
            files.stageText(dimensionJson, dimensionTarget);
            files.publish();

            PackValidationResult validation = validatePublished(pack, dimensionKey);
            if (!validation.isLoadable()) {
                throw new IrisImageMapValidationException(validation.getBlockingErrors());
            }
            files.commit();
            return new ExportResult(
                    imageTarget,
                    mapTarget,
                    dimensionTarget,
                    compiled.getContentHash(),
                    validation.getWarnings()
            );
        } finally {
            files.close();
        }
    }

    public static PreviewResult preview(Path sourcePng, IrisImageMap definition) throws IOException {
        return preview(sourcePng, definition, null, null, List.of());
    }

    public static PreviewResult preview(
            Path sourcePng,
            IrisImageMap definition,
            File packFolder,
            String dimensionKey,
            List<IrisImageMapMask> masks
    ) throws IOException {
        Path source = Objects.requireNonNull(sourcePng, "Source PNG").toAbsolutePath().normalize();
        DecodedSource decoded = decodePng(source);
        IrisImage image = new IrisImage(decoded.image(), decoded.format());
        CompiledIrisImageMap compiled = CompiledIrisImageMap.compile(definition, image);
        IrisImageMapMaskSampler maskSampler = compilePreviewMasks(packFolder, dimensionKey, masks);
        return new PreviewResult(decoded.image(), compiled, decoded.colorProfile(), maskSampler);
    }

    public static SourceInspection inspectSource(Path sourcePng) throws IOException {
        Path source = Objects.requireNonNull(sourcePng, "Source PNG").toAbsolutePath().normalize();
        DecodedSource decoded = decodePng(source);
        IrisImage image = new IrisImage(decoded.image(), decoded.format());
        double minimumAlpha = 1D;
        double maximumAlpha = image.hasAlpha() ? 0D : 1D;
        if (image.hasAlpha()) {
            for (int sourceZ = 0; sourceZ < image.getHeight(); sourceZ++) {
                for (int sourceX = 0; sourceX < image.getWidth(); sourceX++) {
                    double alpha = image.getAlphaNormalized(sourceX, sourceZ);
                    minimumAlpha = Math.min(minimumAlpha, alpha);
                    maximumAlpha = Math.max(maximumAlpha, alpha);
                }
            }
        }
        return new SourceInspection(
                decoded.image(), decoded.format(), decoded.colorProfile(), minimumAlpha, maximumAlpha
        );
    }

    private static IrisImageMapMaskSampler compilePreviewMasks(
            File packFolder,
            String dimensionKey,
            List<IrisImageMapMask> masks
    ) throws IOException {
        if (masks == null || masks.isEmpty()) {
            return IrisImageMapMaskSampler.empty();
        }
        Path pack = requirePack(packFolder);
        String dimensionResource = requireKey(dimensionKey, "Dimension key");
        IrisData data = IrisData.openDatapackCompiler(pack.toFile());
        try {
            IrisDimension dimension = data.getDimensionLoader().load(dimensionResource);
            if (dimension == null) {
                throw new IOException("Dimension resource '" + dimensionResource + "' could not be loaded");
            }
            List<CompiledIrisImageMap> compiledMasks = new ArrayList<>(masks.size());
            for (IrisImageMapMask mask : masks) {
                IrisImageMapBinding binding = findBinding(dimension, mask.getMap());
                if (binding == null || binding.getApplication() != IrisImageMapApplication.MASK) {
                    throw new IrisImageMapValidationException(
                            "Composed mask '" + mask.getMap() + "' does not reference a MASK binding"
                    );
                }
                IrisImageMap maskDefinition = data.getImageMapLoader().load(binding.getMap());
                if (maskDefinition == null) {
                    throw new IrisImageMapValidationException(
                            "Composed mask '" + mask.getMap() + "' references missing image-map '"
                                    + binding.getMap() + "'"
                    );
                }
                IrisImage maskImage = data.getImageLoader().load(maskDefinition.getSource());
                if (maskImage == null) {
                    throw new IrisImageMapValidationException(
                            "Composed mask '" + mask.getMap() + "' references missing PNG '"
                                    + maskDefinition.getSource() + "'"
                    );
                }
                try {
                    compiledMasks.add(CompiledIrisImageMap.compile(maskDefinition, maskImage));
                } finally {
                    data.getImageLoader().unload(maskDefinition.getSource());
                }
            }
            return IrisImageMapMaskSampler.of(compiledMasks, masks);
        } finally {
            data.close();
        }
    }

    private static IrisImageMapBinding findBinding(IrisDimension dimension, String key) {
        if (key == null) {
            return null;
        }
        for (IrisImageMapBinding binding : dimension.getImageMaps()) {
            if (binding != null && key.equals(binding.getKey())) {
                return binding;
            }
        }
        return null;
    }

    private static PackValidationResult validatePublished(Path pack, String dimensionKey) throws IOException {
        IrisData validationData = IrisData.openDatapackCompiler(pack.toFile());
        try {
            IrisDimension published = validationData.getDimensionLoader().load(dimensionKey);
            if (published == null) {
                throw new IOException("Published dimension '" + dimensionKey + "' could not be reloaded");
            }
            IrisImageMapRuntime.compile(validationData, published, published.getMinHeight());
        } finally {
            validationData.close();
        }
        return PackValidator.validate(pack.toFile());
    }

    private static void bind(
            IrisDimension dimension,
            String bindingKey,
            String imageMapKey,
            IrisImageMapApplication application,
            List<IrisImageMapMask> masks
    ) {
        IrisImageMapBinding existing = null;
        for (IrisImageMapBinding candidate : dimension.getImageMaps()) {
            if (candidate != null && bindingKey.equals(candidate.getKey())) {
                existing = candidate;
                break;
            }
        }
        KList<IrisImageMapMask> configuredMasks = new KList<>();
        configuredMasks.addAll(masks);
        if (existing == null) {
            dimension.getImageMaps().add(new IrisImageMapBinding()
                    .setKey(bindingKey)
                    .setMap(imageMapKey)
                    .setApplication(application)
                    .setMasks(configuredMasks));
            return;
        }
        existing.setMap(imageMapKey);
        existing.setApplication(application);
        existing.setMasks(configuredMasks);
    }

    private static DecodedSource decodePng(Path source) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Image source is not a file: " + source);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
            if (input == null) {
                throw new IOException("Unable to open image source " + source);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported or corrupt image source " + source);
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                if (!"png".equals(format)) {
                    throw new IOException("Image-map data must be PNG, got " + format);
                }
                reader.setInput(input, true, false);
                validateDimensions(reader.getWidth(0), reader.getHeight(0));
                String colorProfile = colorProfile(reader);
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("PNG decoder returned no image for " + source);
                }
                return new DecodedSource(image, format, colorProfile);
            } finally {
                reader.dispose();
            }
        }
    }

    private static void validateDimensions(int width, int height) {
        List<String> diagnostics = new ArrayList<>();
        if (width < IrisImageMapCompiler.MINIMUM_DIMENSION || width > IrisImageMapCompiler.MAXIMUM_DIMENSION) {
            diagnostics.add("Image width must be " + IrisImageMapCompiler.MINIMUM_DIMENSION + ".."
                    + IrisImageMapCompiler.MAXIMUM_DIMENSION + ", got " + width);
        }
        if (height < IrisImageMapCompiler.MINIMUM_DIMENSION || height > IrisImageMapCompiler.MAXIMUM_DIMENSION) {
            diagnostics.add("Image height must be " + IrisImageMapCompiler.MINIMUM_DIMENSION + ".."
                    + IrisImageMapCompiler.MAXIMUM_DIMENSION + ", got " + height);
        }
        long pixels = (long) width * height;
        if (pixels > IrisImageMapCompiler.MAXIMUM_PIXELS) {
            diagnostics.add("Image contains " + pixels + " pixels; maximum is "
                    + IrisImageMapCompiler.MAXIMUM_PIXELS);
        }
        if (!diagnostics.isEmpty()) {
            throw new IrisImageMapValidationException(diagnostics);
        }
    }

    private static Path requirePack(File folder) throws IOException {
        if (folder == null) {
            throw new IOException("Pack folder is required");
        }
        Path pack = folder.toPath().toAbsolutePath().normalize();
        if (!Files.isDirectory(pack) || Files.isSymbolicLink(pack)) {
            throw new IOException("Pack folder is missing or unsafe: " + pack);
        }
        return pack;
    }

    private static String colorProfile(ImageReader reader) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(0);
            Node root = metadata.getAsTree("javax_imageio_png_1.0");
            Node embedded = findNode(root, "iCCP");
            if (embedded != null) {
                return "embedded ICC: " + attribute(embedded, "profileName", "unnamed");
            }
            Node standard = findNode(root, "sRGB");
            if (standard != null) {
                return "sRGB: " + attribute(standard, "renderingIntent", "intent unspecified");
            }
            Node gamma = findNode(root, "gAMA");
            if (gamma != null) {
                return "gamma: " + attribute(gamma, "value", "unspecified");
            }
            return "none declared";
        } catch (IOException | RuntimeException exception) {
            return "metadata unavailable";
        }
    }

    private static Node findNode(Node node, String name) {
        if (node == null) {
            return null;
        }
        if (name.equals(node.getNodeName())) {
            return node;
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            Node found = findNode(child, name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String attribute(Node node, String name, String fallback) {
        NamedNodeMap attributes = node.getAttributes();
        Node value = attributes == null ? null : attributes.getNamedItem(name);
        return value == null || value.getNodeValue().isBlank() ? fallback : value.getNodeValue();
    }

    private static String requireKey(String value, String name) {
        if (value == null || !RESOURCE_KEY.matcher(value).matches() || value.contains("..")) {
            throw new IllegalArgumentException(name + " must match " + RESOURCE_KEY.pattern() + " without '..'");
        }
        return value;
    }

    private static Path safeTarget(Path pack, String folder, String relativeName) throws IOException {
        Path target = pack.resolve(folder).resolve(relativeName).normalize();
        if (!target.startsWith(pack) || target.equals(pack)) {
            throw new IOException("Export target escapes the pack: " + target);
        }
        if (Files.isSymbolicLink(target)) {
            throw new IOException("Export target must not be a symbolic link: " + target);
        }
        return target;
    }

    public record ExportRequest(
            File packFolder,
            String dimensionKey,
            String bindingKey,
            IrisImageMapApplication application,
            String imageMapKey,
            String imageKey,
            IrisImageMap definition,
            Path sourcePng,
            List<IrisImageMapMask> masks
    ) {
        public ExportRequest {
            masks = masks == null ? List.of() : List.copyOf(masks);
        }
    }

    public record ExportResult(
            Path imageFile,
            Path imageMapFile,
            Path dimensionFile,
            String contentHash,
            List<String> warnings
    ) {
        public ExportResult {
            warnings = List.copyOf(warnings);
        }
    }

    public record PreviewResult(
            BufferedImage source,
            CompiledIrisImageMap compiled,
            String colorProfile,
            IrisImageMapMaskSampler maskSampler
    ) {
    }

    public record SourceInspection(
            BufferedImage source,
            String format,
            String colorProfile,
            double minimumAlpha,
            double maximumAlpha
    ) {
    }

    private record DecodedSource(BufferedImage image, String format, String colorProfile) {
    }

    private static final class AtomicFileSet implements AutoCloseable {
        private final List<StagedFile> files = new ArrayList<>();
        private boolean published;
        private boolean committed;

        private void stageCopy(Path source, Path target) throws IOException {
            Files.createDirectories(target.getParent());
            Path staged = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".studio");
            Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
            files.add(new StagedFile(staged, target, null, false));
        }

        private void stageText(String content, Path target) throws IOException {
            Files.createDirectories(target.getParent());
            Path staged = Files.createTempFile(target.getParent(), "." + target.getFileName(), ".studio");
            Files.writeString(staged, content, StandardCharsets.UTF_8);
            files.add(new StagedFile(staged, target, null, false));
        }

        private void publish() throws IOException {
            published = true;
            for (int index = 0; index < files.size(); index++) {
                StagedFile file = files.get(index);
                Path backup = null;
                if (Files.exists(file.target())) {
                    backup = file.target().resolveSibling("." + file.target().getFileName()
                            + ".backup-" + UUID.randomUUID());
                    move(file.target(), backup, false);
                }
                files.set(index, new StagedFile(file.staged(), file.target(), backup, true));
                move(file.staged(), file.target(), true);
            }
        }

        private void commit() throws IOException {
            committed = true;
            for (StagedFile file : files) {
                if (file.backup() != null) {
                    Files.deleteIfExists(file.backup());
                }
            }
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            if (published && !committed) {
                for (int index = files.size() - 1; index >= 0; index--) {
                    StagedFile file = files.get(index);
                    if (!file.published()) {
                        continue;
                    }
                    try {
                        Files.deleteIfExists(file.target());
                        if (file.backup() != null && Files.exists(file.backup())) {
                            move(file.backup(), file.target(), true);
                        }
                    } catch (IOException rollbackFailure) {
                        if (failure == null) {
                            failure = rollbackFailure;
                        } else {
                            failure.addSuppressed(rollbackFailure);
                        }
                    }
                }
            }
            for (StagedFile file : files) {
                try {
                    Files.deleteIfExists(file.staged());
                    if (committed && file.backup() != null) {
                        Files.deleteIfExists(file.backup());
                    }
                } catch (IOException cleanupFailure) {
                    if (failure == null) {
                        failure = cleanupFailure;
                    } else {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private static void move(Path source, Path target, boolean replace) throws IOException {
            try {
                if (replace) {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                }
            } catch (AtomicMoveNotSupportedException exception) {
                if (replace) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(source, target);
                }
            }
        }
    }

    private record StagedFile(Path staged, Path target, Path backup, boolean published) {
    }
}
