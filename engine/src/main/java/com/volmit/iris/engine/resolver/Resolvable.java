package com.volmit.iris.engine.resolver;

import art.arcane.amulet.format.Form;
import art.arcane.cram.PakResource;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.platform.PlatformNamespaced;
import com.volmit.iris.platform.PlatformNamespacedMutable;
import lombok.AllArgsConstructor;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public interface Resolvable extends PlatformNamespaced, PlatformNamespacedMutable, PakResource {
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
        Class<? extends Resolvable> value();
    }

    @Target({ElementType.FIELD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @interface PlatformType {
        Class<? extends PlatformNamespaced> value();
    }
}
