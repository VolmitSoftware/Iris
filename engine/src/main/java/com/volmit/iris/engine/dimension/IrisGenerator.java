package com.volmit.iris.engine.dimension;

import art.arcane.source.api.NoisePlane;
import art.arcane.source.api.script.NoisePlaneConstructor;
import art.arcane.source.api.util.NoisePreset;
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
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.script.ScriptException;
import java.io.IOException;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Accessors(fluent = true, chain = true)
public class IrisGenerator implements Resolvable, TypeAdapterFactory {
    @Builder.Default
    private String java = "art.arcane.source.api.util.NoisePreset.NATURAL.create(seed)";

    @Builder.Default
    private IrisSeedSet seed = new IrisSeedSet();

    public NoisePlane getNoisePlane(long seed)
    {
        try {
            return NoisePlaneConstructor.execute(seed, java);
        } catch(ScriptException e) {
            e.printStackTrace();
            return NoisePreset.NATURAL.create(seed);
        }
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        if(!type.getRawType().equals(getClass())) {
            return null;
        }

        return new TypeAdapter<>() {
            public void write(JsonWriter out, T value) {writeSafeJson(delegate, out, value);}

            public T read(JsonReader in) throws IOException {
                JsonToken token = in.peek();

                if(token == JsonToken.STRING)
                {
                    return (T) IrisGenerator.builder().java(in.nextString()).build();
                }

                return delegate.read(in);
            }
        };
    }
}
