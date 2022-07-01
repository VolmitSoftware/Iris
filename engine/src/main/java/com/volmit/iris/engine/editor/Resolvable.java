package com.volmit.iris.engine.editor;

import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public interface Resolvable {
    default void apply(GsonBuilder builder) {
        if(this instanceof TypeAdapterFactory f) {
            builder.registerTypeAdapterFactory(f);
        }
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
}
