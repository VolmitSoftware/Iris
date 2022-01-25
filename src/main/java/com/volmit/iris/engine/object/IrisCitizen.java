package com.volmit.iris.engine.object;

import com.volmit.iris.core.link.CitizensLink;
import com.volmit.iris.core.loader.IrisRegistrant;
import com.volmit.iris.engine.object.annotations.Desc;
import com.volmit.iris.util.json.JSONObject;
import com.volmit.iris.util.plugin.VolmitSender;

public class IrisCitizen extends IrisRegistrant {



    @Override
    public String getFolderName() {
        return "citizens";
    }

    @Override
    public String getTypeName() {
        return "Citizen";
    }

    @Override
    public void scanForErrors(JSONObject p, VolmitSender sender) {

    }
}
