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

package art.arcane.iris.core.modules.manifest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.classfile.constantpool.Utf8Entry;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The classes a module jar holds and the classes its code names, as internal names such as
 * {@code art/arcane/iris/modules/carve/CarveSettings}. A pack loads all its jars in one class loader, so a jar that names a
 * class of another jar was compiled against that jar's version and may not link against a different one.
 *
 * @param defined    the classes in the jar
 * @param referenced every class the jar's classes name: in code, signatures and descriptors. It may list more than the
 *                   code uses, never less
 */
record JarClasses(Set<String> defined, Set<String> referenced) {
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final Pattern CLASS_IN_DESCRIPTOR = Pattern.compile("L([^;<>()\\[.\\s]+);");

    JarClasses {
        defined = Collections.unmodifiableSet(defined);
        referenced = Collections.unmodifiableSet(referenced);
    }

    static JarClasses read(byte[] jarBytes) throws IOException {
        Set<String> defined = new HashSet<>();
        Set<String> referenced = new HashSet<>();
        try (JarInputStream jar = new JarInputStream(new ByteArrayInputStream(jarBytes))) {
            for (JarEntry entry = jar.getNextJarEntry(); entry != null; entry = jar.getNextJarEntry()) {
                if (entry.getName().endsWith(CLASS_FILE_SUFFIX)) {
                    collect(ClassFile.of().parse(jar.readAllBytes()), defined, referenced);
                }
            }
        }
        return new JarClasses(defined, referenced);
    }

    boolean names(JarClasses other) {
        return !Collections.disjoint(referenced, other.defined);
    }

    /** Both jars' classes together, for a jar judged by what either of its two versions holds. */
    JarClasses mergedWith(JarClasses other) {
        Set<String> allDefined = new HashSet<>(defined);
        allDefined.addAll(other.defined);
        Set<String> allReferenced = new HashSet<>(referenced);
        allReferenced.addAll(other.referenced);
        return new JarClasses(allDefined, allReferenced);
    }

    private static void collect(ClassModel type, Set<String> defined, Set<String> referenced) {
        defined.add(type.thisClass().asInternalName());
        for (PoolEntry entry : type.constantPool()) {
            if (entry instanceof ClassEntry named) {
                addClassesIn(named.asInternalName(), referenced);
            } else if (entry instanceof Utf8Entry text) {
                addClassesIn(text.stringValue(), referenced);
            }
        }
    }

    /** A plain class name names itself; an array type or a descriptor names every {@code L...;} class in it. */
    private static void addClassesIn(String nameOrDescriptor, Set<String> referenced) {
        Matcher descriptor = CLASS_IN_DESCRIPTOR.matcher(nameOrDescriptor);
        boolean describesClasses = false;
        while (descriptor.find()) {
            referenced.add(descriptor.group(1));
            describesClasses = true;
        }
        if (!describesClasses) {
            referenced.add(nameOrDescriptor);
        }
    }
}
