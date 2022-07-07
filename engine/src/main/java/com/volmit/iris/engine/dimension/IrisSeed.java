package com.volmit.iris.engine.dimension;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.engine.Engine;
import com.volmit.iris.engine.resolver.EngineResolvable;
import com.volmit.iris.engine.resolver.Resolvable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.IOException;

@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "seed", jsonTypes = {JsonToken.NUMBER, JsonToken.STRING, JsonToken.BEGIN_OBJECT})
public class IrisSeed extends EngineResolvable implements TypeAdapterFactory {
    @Builder.Default
    private IrisSeedSetMode mode = IrisSeedSetMode.LOCAL_OFFSET;

    @Builder.Default
    @TokenConstructor(JsonToken.NUMBER)
    private long offset = 0;

    @TokenConstructor(JsonToken.STRING)
    private String hashOffset;

    public long getOffset() {
        if(hashOffset != null && hashOffset.isNotEmpty()) {
            return hashOffset.hashCode() + offset;
        }

        return offset;
    }

    public double getSeed(Engine engine, long localSeed) {
        return switch(mode)
            {
                case WORLD -> engine.getSeedManager().getWorldSeed();
                case LOCAL -> localSeed;
                case LOCAL_OFFSET -> localSeed + getOffset();
                case RAW -> getOffset();
                case WORLD_OFFSET -> engine.getSeedManager().getWorldSeed() + getOffset();
                case RANDOM -> (Math.random() * Long.MAX_VALUE) + (Math.random() * Long.MAX_VALUE);
            };
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
                    return (T) IrisSeed.builder().hashOffset(in.nextString()).build();
                }

                if(token == JsonToken.NUMBER) {
                    return (T) IrisSeed.builder().offset(Double.doubleToLongBits(in.nextDouble())).build();
                }

                return delegate.read(in);
            }
        };
    }
}
