package com.volmit.iris.engine.resolver;

import art.arcane.amulet.format.Form;
import art.arcane.cram.PakResource;
import com.google.gson.*;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.engine.EngineData;
import com.volmit.iris.platform.PlatformBiome;
import com.volmit.iris.platform.block.PlatformBlock;
import com.volmit.iris.platform.PlatformNamespaced;
import com.volmit.iris.platform.PlatformNamespacedMutable;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

public interface Resolvable extends PlatformNamespaced, PlatformNamespacedMutable, PakResource {
    String SCHEMA = "http://json-schema.org/draft-07/schema#";

    default String getSchemaId() {
        return "https://art.arcane/iris/schemas/"+entity().getId()+".json";
    }

    default String getSchemaRefId() {
        return entity().getId()+".json";
    }

    default JsonObject generateSchema(EngineData data) {
        JsonObject object = new JsonObject();
        Entity.ResolverEntityData entity = entity();
        object.add("$schema", new JsonPrimitive(SCHEMA));
        object.add("$id", new JsonPrimitive(getSchemaId()));
        object.add("type", new JsonPrimitive("object"));
        object.add("description", new JsonPrimitive("No Description for " + entity.getName()));

        JsonObject properties = new JsonObject();
        JsonObject definitions = new JsonObject();

        for(Field i : getClass().getDeclaredFields()) {
            i.access();

            if (Modifier.isStatic(i.getModifiers()) || Modifier.isTransient(i.getModifiers())) {
                continue;
            }

            properties.add(i.getName(), generateSchemaProperty(data, i.getName(), i.getType(), i.getDeclaredAnnotation(Type.class), i.getDeclaredAnnotation(PlatformType.class), definitions));
        }

        object.add("properties", properties);
        object.add("definitions", definitions);
        return object;
    }

    default String getSchemaDefinition(String name) {
        return "D" + name.hashCode();
    }

    default JsonObject generateSchemaProperty(EngineData data, String name, Class<?> type, Type listType, PlatformType platformType, JsonObject definitions) {
        JsonObject main = new JsonObject();
        JsonArray anyOf = new JsonArray();
        JsonObject object = new JsonObject();
        anyOf.add(object);
        object.add("description", new JsonPrimitive("No Description for field " + name));

        if(type.isAssignableFrom(Resolvable.class) || Resolvable.class.isAssignableFrom(type)) {
            Resolvable r = data.getInstance(type);

            if(r != null) {
                String def = getSchemaDefinition(r.getClass().simpleName());
                object.add("$ref", new JsonPrimitive(r.getSchemaRefId()));
                JsonObject registry = new JsonObject();
                registry.add("description", new JsonPrimitive("No Description for field " + name));
                String defName = getSchemaDefinition("resolve" + type.getCanonicalName());
                registry.add("type", new JsonPrimitive("string"));
                registry.add("$ref", new JsonPrimitive("#/definitions/" + defName));

                if(!definitions.has(defName)) {
                    JsonObject enumDef = new JsonObject();
                    JsonArray enums = new JsonArray();
                    data.getAllKeys(type).forEach(i -> enums.add(i.toString()));
                    enumDef.add("enum", enums);
                    definitions.add(defName, enumDef);
                }

                anyOf.add(registry);
            }
        }

        else if(type.isAssignableFrom(List.class) || List.class.isAssignableFrom(type)) {
            if(listType != null) {
                JsonObject internal = generateSchemaProperty(data, "list", listType.value(), null, platformType, definitions);
                object.add("items", internal);
            }
        }

        else if(type.isEnum()) {
            String defName = getSchemaDefinition(type.getCanonicalName());
            object.add("type", new JsonPrimitive("string"));
            object.add("$ref", new JsonPrimitive("#/definitions/" + defName));

            if(!definitions.has(defName)) {
                try {
                    JsonObject enumDef = new JsonObject();
                    JsonArray enums = new JsonArray();
                    Arrays.stream((Enum<?>[])type.getDeclaredMethod("values").invoke(null)).forEach(i -> enums.add(i.name()));
                    enumDef.add("enum", enums);
                    definitions.add(defName, enumDef);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        else if(type.equals(String.class)) {
            object.add("type", new JsonPrimitive("string"));

            if(platformType != null) {
                String defName = getSchemaDefinition(platformType.value().getCanonicalName());
                object.add("$ref", new JsonPrimitive("#/definitions/" + defName));

                if(!definitions.has(defName)) {
                    JsonObject enumDef = new JsonObject();
                    JsonArray enums = buildSchemaPlatformEnum(data, platformType.value());
                    enumDef.add("enum", enums);
                    definitions.add(defName, enumDef);
                }
            }
        }

        else if(type.equals(int.class) || type.equals(Integer.class)
                || type.equals(long.class) || type.equals(Long.class)
                || type.equals(byte.class) || type.equals(Byte.class)
                || type.equals(short.class) || type.equals(Short.class))
        {
            object.add("type", new JsonPrimitive("integer"));

            if(type.equals(int.class) || type.equals(Integer.class)) {
                object.add("minimum", new JsonPrimitive(Integer.MIN_VALUE));
                object.add("maximum", new JsonPrimitive(Integer.MAX_VALUE));
            }

            else if(type.equals(long.class) || type.equals(Long.class)) {
                object.add("minimum", new JsonPrimitive(Long.MIN_VALUE));
                object.add("maximum", new JsonPrimitive(Long.MAX_VALUE));
            }

            else if(type.equals(short.class) || type.equals(Short.class)) {
                object.add("minimum", new JsonPrimitive(Short.MIN_VALUE));
                object.add("maximum", new JsonPrimitive(Short.MAX_VALUE));
            }

            else {
                object.add("minimum", new JsonPrimitive(Byte.MIN_VALUE));
                object.add("maximum", new JsonPrimitive(Byte.MAX_VALUE));
            }
        }

        if(anyOf.size() == 1)
        {
            return object;
        }

        main.add("anyOf", anyOf);
        return main;
    }

    default JsonArray buildSchemaPlatformEnum(EngineData data, Class<? extends PlatformNamespaced> value) {
        JsonArray a = new JsonArray();

        if(value.equals(PlatformBlock.class)) {
            data.getEngine().getPlatform().getBlocks().map(i -> i.getKey().toString()).forEach(a::add);
        }

        else if(value.equals(PlatformBiome.class)) {
            data.getEngine().getPlatform().getBiomes().map(i -> i.getKey().toString()).forEach(a::add);
        }

        return a;
    }

    default void apply(GsonBuilder builder) {
        if(this instanceof TypeAdapterFactory f) {
            builder.registerTypeAdapterFactory(f);
        }
    }

    default Entity.ResolverEntityData entity() {
        return new Entity.ResolverEntityData(getClass().getDeclaredAnnotation(Resolvable.Entity.class));
    }

    default <T> void writeSafeJson(TypeAdapter<T> delegate, JsonWriter out, T value) {
        try {
            delegate.write(out, value);
        } catch (IOException e) {
            try {
                delegate.write(out, null);
            } catch(IOException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Entity {
        String id();
        String name() default "";
        String namePlural() default "";
        JsonToken[] jsonTypes() default JsonToken.BEGIN_OBJECT;

        @AllArgsConstructor
        @lombok.Data
        class ResolverEntityData
        {
            private final Entity annotation;

            public String getId() {
                return annotation.id();
            }

            public String getName() {
                return annotation.name().isEmpty() ? Form.capitalizeWords(getId().replaceAll("\\Q-\\E", " ")) : annotation.name();
            }

            public String getNamePlural() {
                return annotation.namePlural().isEmpty() ? getName() + "s" : annotation.namePlural();
            }
        }
    }

    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface TokenConstructor {
        JsonToken[] value();
    }

    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface Type {
        Class<?> value();
    }

    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface PlatformType {
        Class<? extends PlatformNamespaced> value();
    }
}
