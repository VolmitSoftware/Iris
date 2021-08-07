package com.volmit.iris.engine.object.location;

import com.volmit.iris.engine.object.annotations.ArrayType;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.engine.object.annotations.RegistryListResource;
import com.volmit.iris.engine.object.annotations.Required;
import com.volmit.iris.engine.object.objects.IrisObject;
import com.volmit.iris.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.StructureType;

@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = false)
@Desc("Override location with object")
public class IrisLocation {

    @Required
    @Desc("The structure type to override")
    private StructureType type;

    @Required
    @Desc("The object(s) to override it with")
    @RegistryListResource(IrisObject.class)
    @ArrayType(min = 1, type = String.class)
    private KList<String> objects = new KList<>();
}
