/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.studio.workspace;

import art.arcane.iris.pack.PackDirectoryResolver;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONArray;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Stream;

public final class IrisProjectCopier {
    private IrisProjectCopier() {
    }

    public static void copyProject(File sourcePack, File targetWorkspace, String sourceKey, String targetKey) throws IOException {
        copyProject(sourcePack, targetWorkspace, sourceKey, targetKey, (source, target) -> {
        });
    }

    static void copyProject(
            File sourcePack,
            File targetWorkspace,
            String sourceKey,
            String targetKey,
            CopyHook copyHook
    ) throws IOException {
        String validatedSourceKey = requireSafeKey(sourceKey, "source");
        String validatedTargetKey = requireSafeKey(targetKey, "target");
        Path source = requireSafeSource(
                Objects.requireNonNull(sourcePack, "sourcePack").toPath().toAbsolutePath().normalize(),
                validatedSourceKey
        );
        Path workspace = requireTargetWorkspace(targetWorkspace);
        Path target = workspace.resolve(validatedTargetKey).normalize();
        requireAvailableTarget(target, workspace, validatedTargetKey);

        Path stage = Files.createTempDirectory(workspace, "." + validatedTargetKey + ".importing-");
        boolean published = false;
        Throwable operationFailure = null;
        try {
            copyTree(source, stage, Objects.requireNonNull(copyHook, "copyHook"));
            transformDimension(stage, validatedSourceKey, validatedTargetKey);
            requireAvailableTarget(target, workspace, validatedTargetKey);
            publish(stage, target);
            published = true;
        } catch (IOException | RuntimeException | Error e) {
            operationFailure = e;
            throw e;
        } finally {
            if (!published) {
                try {
                    deleteTree(stage);
                } catch (IOException cleanupFailure) {
                    if (operationFailure != null) {
                        operationFailure.addSuppressed(cleanupFailure);
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }

    private static String requireSafeKey(String value, String purpose) throws IOException {
        if (value == null || value.isBlank()) {
            throw new IOException("Project " + purpose + " key cannot be empty.");
        }

        Path key;
        try {
            key = Path.of(value);
        } catch (RuntimeException e) {
            throw new IOException("Invalid project " + purpose + " key: " + value, e);
        }
        if (key.isAbsolute()
                || key.getNameCount() != 1
                || !key.normalize().equals(key)
                || ".".equals(value)
                || "..".equals(value)
                || value.indexOf('/') >= 0
                || value.indexOf('\\') >= 0) {
            throw new IOException("Invalid project " + purpose + " key: " + value);
        }
        return value;
    }

    private static Path requireTargetWorkspace(File targetWorkspace) throws IOException {
        Path workspace = Objects.requireNonNull(targetWorkspace, "targetWorkspace").toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(workspace)
                || !Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Project workspace is missing or unsafe: " + workspace);
        }
        return workspace;
    }

    private static Path requireSafeSource(Path source, String sourceKey) throws IOException {
        if (!Files.isDirectory(source)) {
            throw new IOException("Source project is missing or unsafe: " + source);
        }
        PackDirectoryResolver.requireSafePackTree(source.toFile());
        Path resolvedSource = source.toRealPath();

        Path dimension = resolvedSource.resolve("dimensions").resolve(sourceKey + ".json").normalize();
        if (!dimension.startsWith(resolvedSource)
                || Files.isSymbolicLink(dimension)
                || !Files.isRegularFile(dimension, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Source project dimension is missing or unsafe: " + dimension);
        }
        return resolvedSource;
    }

    private static void requireAvailableTarget(Path target, Path workspace, String targetKey) throws IOException {
        if (!workspace.equals(target.getParent()) || !targetKey.equals(target.getFileName().toString())) {
            throw new IOException("Target project must be a direct child of the workspace: " + target);
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
    }

    private static void copyTree(Path source, Path stage, CopyHook copyHook) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                Path relative = source.relativize(path);
                if (relative.toString().isEmpty() || shouldSkip(relative)) {
                    continue;
                }
                if (Files.isSymbolicLink(path)) {
                    throw new IOException("Source project contains a symbolic link: " + path);
                }

                Path destination = stage.resolve(relative).normalize();
                if (!destination.startsWith(stage)) {
                    throw new IOException("Source project entry escapes staging: " + relative);
                }
                copyHook.beforeCopy(path, destination);
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination);
                } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination);
                } else {
                    throw new IOException("Source project contains an unsupported entry: " + path);
                }
            }
        }
    }

    private static boolean shouldSkip(Path relative) {
        return ".git".equals(relative.getName(0).toString())
                || relative.getFileName().toString().endsWith(".code-workspace");
    }

    private static void transformDimension(Path stage, String sourceKey, String targetKey) throws IOException {
        Path dimensionsRoot = stage.resolve("dimensions");
        Path oldDimension = dimensionsRoot.resolve(sourceKey + ".json");
        Path newDimension = dimensionsRoot.resolve(targetKey + ".json");
        if (!oldDimension.equals(newDimension)) {
            Files.move(oldDimension, newDimension);
        }

        try (Stream<Path> files = Files.walk(dimensionsRoot)) {
            for (Path dimensionFile : files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList()) {
                JSONObject json = new JSONObject(IO.readAll(dimensionFile.toFile()));
                boolean changed = false;
                if (dimensionFile.equals(newDimension) && json.has("name")) {
                    json.put("name", Form.capitalizeWords(targetKey.replace('-', ' ')));
                    changed = true;
                }
                if (rewriteDimensionStackReferences(json, sourceKey, targetKey)) {
                    changed = true;
                }
                if (changed) {
                    IO.writeAll(dimensionFile.toFile(), json.toString(4));
                }
            }
        }
    }

    private static boolean rewriteDimensionStackReferences(JSONObject json, String sourceKey, String targetKey) {
        JSONObject stack = json.optJSONObject("dimensionStack");
        JSONArray dimensions = stack == null ? null : stack.optJSONArray("dimensions");
        if (dimensions == null) {
            return false;
        }

        boolean changed = false;
        JSONArray rewritten = new JSONArray();
        for (int index = 0; index < dimensions.length(); index++) {
            Object value = dimensions.opt(index);
            if (sourceKey.equals(value)) {
                rewritten.put(targetKey);
                changed = true;
            } else {
                rewritten.put(value == null ? JSONObject.NULL : value);
            }
        }
        if (changed) {
            stack.put("dimensions", rewritten);
        }
        return changed;
    }

    private static void publish(Path stage, Path target) throws IOException {
        try {
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(stage, target);
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            IOException failure = null;
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    @FunctionalInterface
    interface CopyHook {
        void beforeCopy(Path source, Path target) throws IOException;
    }
}
