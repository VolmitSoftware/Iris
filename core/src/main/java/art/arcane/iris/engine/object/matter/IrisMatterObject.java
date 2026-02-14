package art.arcane.iris.engine.object.matter;

import art.arcane.iris.core.loader.IrisRegistrant;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.iris.util.matter.IrisMatter;
import art.arcane.iris.util.matter.Matter;
import art.arcane.iris.util.plugin.VolmitSender;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;
import java.io.IOException;

@Data
@EqualsAndHashCode(callSuper = false)
public class IrisMatterObject extends IrisRegistrant {
    private final Matter matter;

    public IrisMatterObject() {
        this(1, 1, 1);
    }

    public IrisMatterObject(int w, int h, int d) {
        this(new IrisMatter(w, h, d));
    }

    public IrisMatterObject(Matter matter) {
        this.matter = matter;
    }

    public static IrisMatterObject from(IrisObject object) {
        return new IrisMatterObject(Matter.from(object));
    }

    public static IrisMatterObject from(File j) throws IOException, ClassNotFoundException {
        return new IrisMatterObject(Matter.read(j));
    }

    @Override
    public String getFolderName() {
        return "matter";
    }

    @Override
    public String getTypeName() {
        return "Matter";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
