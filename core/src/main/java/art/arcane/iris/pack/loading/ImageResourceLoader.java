/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.pack.loading;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.configuration.IrisSettings;
import art.arcane.iris.generation.image.IrisImageMapCompiler;
import art.arcane.iris.generation.image.IrisImage;
import art.arcane.volmlib.util.collection.KSet;
import art.arcane.volmlib.util.data.KCache;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

public class ImageResourceLoader extends ResourceLoader<IrisImage> {
    public ImageResourceLoader(File root, IrisData idm, String folderName, String resourceTypeName, Options options) {
        super(root, idm, folderName, resourceTypeName, IrisImage.class, options);
        int cacheSize = options.registerPreservation()
                ? IrisSettings.get().getPerformance().getObjectLoaderCacheSize()
                : options.cacheSize();
        loadCache = new KCache<>(this::loadRaw, cacheSize);
    }

    public boolean supportsSchemas() {
        return false;
    }

    public long getSize() {
        return loadCache.getSize();
    }

    public long getTotalStorage() {
        return getSize();
    }

    protected IrisImage loadFile(File j, String name) {
        try {
            PrecisionStopwatch p = PrecisionStopwatch.start();
            try (ImageInputStream input = ImageIO.createImageInputStream(j)) {
                if (input == null) {
                    IrisLogging.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath()
                            + " (unsupported or corrupt image)");
                    return null;
                }
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    IrisLogging.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath()
                            + " (unsupported or corrupt image)");
                    return null;
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!"png".equals(format)) {
                        IrisLogging.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath()
                                + " (expected PNG, got " + format + ")");
                        return null;
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (!supportedDimensions(width, height)) {
                        IrisLogging.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath()
                                + " (dimensions " + width + "x" + height + " exceed the supported image-map limits)");
                        return null;
                    }
                    BufferedImage image = reader.read(0);
                    if (image == null) {
                        IrisLogging.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath()
                                + " (unsupported or corrupt image)");
                        return null;
                    }
                    IrisImage loaded = new IrisImage(image, format);
                    loaded.setLoadFile(j);
                    loaded.setLoader(manager);
                    loaded.setLoadKey(name);
                    logLoad(j, loaded);
                    tlt.addAndGet(p.getMilliseconds());
                    return loaded;
                } finally {
                    reader.dispose();
                }
            }
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            IrisLogging.warn("Couldn't read " + resourceTypeName + " file: " + j.getPath() + ": " + e.getMessage());
            return null;
        }
    }

    static boolean supportedDimensions(int width, int height) {
        if (width < IrisImageMapCompiler.MINIMUM_DIMENSION
                || width > IrisImageMapCompiler.MAXIMUM_DIMENSION
                || height < IrisImageMapCompiler.MINIMUM_DIMENSION
                || height > IrisImageMapCompiler.MAXIMUM_DIMENSION) {
            return false;
        }
        return (long) width * height <= IrisImageMapCompiler.MAXIMUM_PIXELS;
    }

    void getPNGFiles(File directory, String prefix, Set<String> m, HashSet<String> visitedDirectories) {
        if (directory == null || !directory.exists()) {
            return;
        }

        if (directory.isDirectory()) {
            String canonicalDirectory = toCanonicalPath(directory);
            if (canonicalDirectory != null && !visitedDirectories.add(canonicalDirectory)) {
                return;
            }
        }

        File[] listedFiles = directory.listFiles();
        if (listedFiles == null) {
            return;
        }

        for (File file : listedFiles) {
            if (file.isFile() && file.getName().endsWith(".png")) {
                m.add(prefix + file.getName().replace(".png", ""));
            } else if (file.isDirectory()) {
                getPNGFiles(file, prefix + file.getName() + "/", m, visitedDirectories);
            }
        }
    }


    public String[] getPossibleKeys() {
        if (possibleKeys != null) {
            return possibleKeys;
        }

        IrisLogging.debug("Building " + resourceTypeName + " Possibility Lists");
        KSet<String> m = new KSet<>();
        HashSet<String> visitedDirectories = new HashSet<>();

        for (File i : getFolders()) {
            getPNGFiles(i, "", m, visitedDirectories);
        }

        possibleKeys = m.toArray(new String[0]);
        return possibleKeys;
    }

    private String toCanonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return null;
        }
    }

    public File findFile(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        if (name.equals("null")) {
            IrisLogging.warn("Refusing " + resourceTypeName + " lookup for literal string \"null\" (called by " + callerHint() + ")");
            return null;
        }

        File file = resolveFile(name, ".png");

        if (file != null) {
            return file;
        }

        IrisLogging.warn("Couldn't find " + resourceTypeName + ": " + name + " (called by " + callerHint() + ")");

        return null;
    }

    public IrisImage load(String name) {
        return load(name, true);
    }

    private IrisImage loadRaw(String name) {
        File file = resolveFile(name, ".png");

        if (file != null) {
            return loadFile(file, name);
        }

        IrisLogging.warn("Couldn't find " + resourceTypeName + ": " + name + " (called by " + callerHint() + ")");

        return null;
    }

    public IrisImage load(String name, boolean warn) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        if (name.equals("null") && warn) {
            IrisLogging.warn("Refusing " + resourceTypeName + " load for literal string \"null\" (called by " + callerHint() + ")");
            return null;
        }
        return loadCache.get(name);
    }
}
