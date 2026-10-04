package art.arcane.iris.engine.object.image;

import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.RegistryListResource;
import art.arcane.volmlib.util.collection.KList;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Accessors(chain = true)
@NoArgsConstructor
@Desc("Binds a reusable image map to one dimension generation role")
@Data
public class IrisImageMapBinding {
    @Desc("Unique name used by Studio previews and custom map lookups")
    private String key = "";

    @RegistryListResource(IrisImageMap.class)
    @Desc("Image-map resource key under image-maps/")
    private String map = "";

    @Desc("Generation input controlled by this binding")
    private IrisImageMapApplication application = IrisImageMapApplication.CUSTOM;

    @ArrayType(type = IrisImageMapMask.class)
    @Desc("Named mask maps composed in declaration order")
    private KList<IrisImageMapMask> masks = new KList<>();
}
