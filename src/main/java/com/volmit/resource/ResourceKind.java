package com.volmit.resource;

import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.regex.Pattern;

/** A kind of resource read from the pack: every JSON file below {@code <pack>/<folder>/}, each read
 * into a new instance of {@code type}. A file's key is its path below the folder without {@code .json}, such as
 * {@code castle/keep}, and its id is {@code <pack namespace>:<key>}, the form {@link PackResources} uses for
 * every pack resource. A resource is found by its kind and its id, {@link PackResources#find}{@code (kind, id)};
 * the host tells kinds apart by {@code type}, so each kind has a class of its own. The host's own kinds are the
 * constants of {@link PackResources}.
 *
 * <p>Modules register kinds with {@link com.volmit.FeatureRegistry#registerResourceKind}. The id names the kind
 * in errors and studio and follows the module id like every other id the module owns: {@code iris:structure}
 * owns {@code iris:structure/pool}. The folder is one folder name directly below the pack root, chosen by the
 * module; one folder holds one kind, and the host's own folders belong to the host.
 *
 * <p>The host creates each resource with the type's no-argument constructor and reads the file's JSON into it,
 * the way it reads settings, so an omitted field keeps its default. Resources are values: the host reads each
 * once per pack load and hands every caller the same instance, so nobody may change one after it is read.
 */
public record ResourceKind<T>(ResourceId id, String folder, Class<T> type) {
    private static final Pattern FOLDER = Pattern.compile("[a-z0-9_.-]+");

    public ResourceKind {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(type, "type");
        requireFolderName(id, folder);
        requireConcreteClass(id, type);
    }

    public static <T> ResourceKind<T> of(String id, String folder, Class<T> type) {
        return new ResourceKind<>(ResourceId.parse(id), folder, type);
    }

    /** Whether {@code name} may name a kind's folder: one folder name of lowercase letters, digits, '-', '_' and '.'. */
    public static boolean isFolderName(String name) {
        return FOLDER.matcher(name).matches() && !name.equals(".") && !name.equals("..");
    }

    private static void requireFolderName(ResourceId id, String folder) {
        if (isFolderName(folder)) return;
        throw new IllegalArgumentException("Folder '" + folder + "' of resource kind " + id + " is not a folder name;"
                + " a kind reads one folder directly below the pack root, named with lowercase letters, digits, '-', '_' and '.'");
    }

    private static void requireConcreteClass(ResourceId id, Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.isInterface() || Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException("Resource kind " + id + " reads into " + type.getName()
                    + ", which the host cannot create; use a concrete class with a no-argument constructor");
        }
    }
}
