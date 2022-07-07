package com.volmit.iris.engine.dimension;

import art.arcane.source.NoisePlane;
import art.arcane.source.script.NoisePlaneConstructor;
import art.arcane.source.util.NoisePreset;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.engine.resolver.EngineResolvable;
import com.volmit.iris.engine.resolver.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import javax.script.ScriptException;
import java.io.IOException;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "generator", jsonTypes = {JsonToken.STRING, JsonToken.BEGIN_OBJECT})
public class IrisGenerator  extends EngineResolvable implements TypeAdapterFactory {
    public static final IrisGenerator NATURAL = IrisGenerator.builder().java("art.arcane.source.api.util.NoisePreset.NATURAL.create(seed)").build();
    public static final IrisGenerator WHITE = IrisGenerator.builder().java("Noise.white(seed)").build();
    public static final IrisGenerator FLAT = IrisGenerator.builder().java("Noise.flat(seed)").build();

    @Builder.Default
    private String java = "art.arcane.source.api.util.NoisePreset.NATURAL.create(seed)";

    @Builder.Default
    private IrisSeed seed = new IrisSeed();

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

            @SuppressWarnings("unchecked")
            public T read(JsonReader in) throws IOException {
                JsonToken token = in.peek();

                if(token == JsonToken.STRING) {
                    return (T) IrisGenerator.builder().java(in.nextString()).build();
                }

                return delegate.read(in);
            }
        };
    }
}
