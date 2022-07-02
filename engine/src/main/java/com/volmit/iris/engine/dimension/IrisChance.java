package com.volmit.iris.engine.dimension;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.engine.editor.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.IOException;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "chance", jsonTypes = {JsonToken.STRING, JsonToken.BEGIN_OBJECT})
public class IrisChance extends IrisResolvable implements TypeAdapterFactory {
    @Builder.Default
    @TokenConstructor(JsonToken.NUMBER)
    private double threshold = 0.5;

    @TokenConstructor(JsonToken.STRING)
    @Builder.Default
    private IrisGenerator generator = IrisGenerator.WHITE;

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        if(!type.getRawType().equals(getClass())) {
            return null;
        }

        return new TypeAdapter<>() {
            public void write(JsonWriter out, T value) {writeSafeJson(delegate, out, value);}

            @SuppressWarnings("unchecked")
            public T read(JsonReader in) throws IOException {
                JsonToken token = in.peek();

                if(token == JsonToken.STRING) {
                    return (T) IrisChance.half(gson.fromJson(in.nextString(), IrisGenerator.class));
                }

                return delegate.read(in);
            }
        };
    }

    public static IrisChance half(IrisGenerator generator) {
        return IrisChance.builder()
            .threshold(0.5)
            .generator(generator)
            .build();
    }

    public static IrisChance white(double chance) {
        return IrisChance.builder()
            .threshold(chance)
            .generator(IrisGenerator.WHITE)
            .build();
    }
}