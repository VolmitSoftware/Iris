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
import lombok.experimental.Accessors;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper=false)
@Accessors(fluent = true, chain = true)
@Resolvable.Entity(id = "surface-layer")
public class IrisSurface extends IrisResolvable {
    @Singular
    @Type(IrisSurfaceLayer.class)
    private List<IrisSurfaceLayer> layers = new ArrayList<>();

    @Singular
    @Type(IrisDecorator.class)
    private List<IrisDecorator> decorators = new ArrayList<>();
}
