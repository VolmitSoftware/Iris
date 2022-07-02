package com.volmit.iris.engine.dimension;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.volmit.iris.engine.editor.Resolvable;
import com.volmit.iris.platform.PlatformBlock;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Singular;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.builder.ToStringExclude;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "palette")
public class IrisPalette extends IrisResolvable implements TypeAdapterFactory {
    @Singular
    @PlatformType(PlatformBlock.class)
    @TokenConstructor(JsonToken.STRING)
    private List<String> blocks = new ArrayList<>();

    @Builder.Default
    private IrisGenerator generator = IrisGenerator.WHITE;

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);

        if(!type.getRawType().equals(getClass())) {
            return null;
        }

        return new TypeAdapter<>() {
            public void write(JsonWriter out, T value) {
                writeSafeJson(delegate, out, value);
            }

            @SuppressWarnings("unchecked")
            public T read(JsonReader in) throws IOException {
                JsonToken token = in.peek();

                if(token == JsonToken.STRING) {
                    return (T) IrisPalette.flat(in.nextString());
                }

                return delegate.read(in);
            }
        };
    }

    public static IrisPalette flat(String block){
        return IrisPalette.builder()
            .block(block)
            .generator(IrisGenerator.FLAT)
            .build();
    }
}
