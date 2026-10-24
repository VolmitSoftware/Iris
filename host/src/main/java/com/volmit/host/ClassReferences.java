package com.volmit.host;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.MethodTypeEntry;
import java.lang.classfile.constantpool.NameAndTypeEntry;
import java.lang.classfile.constantpool.PoolEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.Collections;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/** The classes one class file links against, as binary names: every class in its constant pool
 * (supertypes, instantiated, cast, caught and called-on types) and every type in the descriptors of
 * its fields, methods and the members it uses. Arrays count as their element type; primitives do
 * not count.
 *
 * <p>Annotations and generic signatures are left out. The JVM never resolves them while linking,
 * and reflection skips annotations whose type is missing.
 */
final class ClassReferences {
    private static final String METHOD_DESCRIPTOR_START = "(";

    private ClassReferences() {}

    /** Throws {@link IllegalArgumentException} when the bytes are not a valid class file. */
    static SortedSet<String> read(byte[] classFile) {
        var model = ClassFile.of().parse(classFile);
        var references = new TreeSet<String>();
        for (var entry : model.constantPool()) addPoolEntry(entry, references);
        model.fields().forEach(field -> addType(field.fieldTypeSymbol(), references));
        model.methods().forEach(method -> addMethodType(method.methodTypeSymbol(), references));
        return Collections.unmodifiableSortedSet(references);
    }

    private static void addPoolEntry(PoolEntry entry, Set<String> references) {
        switch (entry) {
            case ClassEntry type -> addType(type.asSymbol(), references);
            case NameAndTypeEntry member -> addDescriptor(member.type().stringValue(), references);
            case MethodTypeEntry methodType -> addMethodType(methodType.asSymbol(), references);
            default -> {}
        }
    }

    private static void addDescriptor(String descriptor, Set<String> references) {
        if (descriptor.startsWith(METHOD_DESCRIPTOR_START)) {
            addMethodType(MethodTypeDesc.ofDescriptor(descriptor), references);
        } else {
            addType(ClassDesc.ofDescriptor(descriptor), references);
        }
    }

    private static void addMethodType(MethodTypeDesc type, Set<String> references) {
        addType(type.returnType(), references);
        type.parameterList().forEach(parameter -> addType(parameter, references));
    }

    private static void addType(ClassDesc type, Set<String> references) {
        var element = type;
        while (element.isArray()) element = element.componentType();
        if (element.isPrimitive()) return;
        references.add(binaryName(element));
    }

    private static String binaryName(ClassDesc type) {
        var descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1).replace('/', '.');
    }
}
