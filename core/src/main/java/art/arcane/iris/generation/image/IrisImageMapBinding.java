package art.arcane.iris.generation.image;

import art.arcane.iris.pack.schema.annotation.ArrayType;
import art.arcane.volmlib.util.documentation.Description;
import art.arcane.iris.pack.schema.annotation.RegistryListResource;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Description("Binds a reusable image map to one dimension generation role")
@Data
public class IrisImageMapBinding {
    @Description("Unique name used by Studio previews and custom map lookups")
    private String key = "";

    @RegistryListResource(IrisImageMap.class)
    @Description("Image-map resource key under image-maps/")
    private String map = "";

    @Description("Generation input controlled by this binding")
    private IrisImageMapApplication application = IrisImageMapApplication.CUSTOM;

    @ArrayType(type = IrisImageMapMask.class)
    @Description("Named mask maps composed in declaration order")
    private KList<IrisImageMapMask> masks = new KList<>();
}
