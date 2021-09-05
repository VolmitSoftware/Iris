package com.volmit.iris.core.commands;

import com.volmit.iris.Iris;
import com.volmit.iris.core.edit.JigsawEditor;
import com.volmit.iris.core.loader.IrisData;
import com.volmit.iris.engine.jigsaw.PlannedStructure;
import com.volmit.iris.engine.object.IrisJigsawPiece;
import com.volmit.iris.engine.object.IrisJigsawStructure;
import com.volmit.iris.engine.object.IrisObject;
import com.volmit.iris.engine.object.IrisPosition;
import com.volmit.iris.util.decree.DecreeExecutor;
import com.volmit.iris.util.decree.DecreeOrigin;
import com.volmit.iris.util.decree.annotations.Decree;
import com.volmit.iris.util.decree.annotations.Param;
import com.volmit.iris.util.decree.specialhandlers.ObjectHandler;
import com.volmit.iris.util.format.C;
import com.volmit.iris.util.format.Form;
import com.volmit.iris.util.math.RNG;
import com.volmit.iris.util.scheduling.PrecisionStopwatch;

import java.io.File;

@Decree(name = "jigsaw", origin = DecreeOrigin.PLAYER, studio = true, description = "Iris jigsaw commands")
public class CommandJigsaw implements DecreeExecutor {
    @Decree(description = "Edit a jigsaw piece")
    public void edit(
            @Param(description = "The jigsaw piece to edit")
                    IrisJigsawPiece piece
    ) {
        File dest = piece.getLoadFile();
        new JigsawEditor(player(), piece, IrisData.loadAnyObject(piece.getObject()), dest);
    }

    @Decree(description = "Place a jigsaw structure")
    public void place(
            @Param(description = "The jigsaw structure to place")
                    IrisJigsawStructure structure
    ) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        PlannedStructure ps = new PlannedStructure(structure, new IrisPosition(player().getLocation()), new RNG());
        sender().sendMessage(C.GREEN + "Generated " + ps.getPieces().size() + " pieces in " + Form.duration(p.getMilliseconds(), 2));
        ps.place(world());
    }

    @Decree(description = "Create a jigsaw piece")
    public void create(
            @Param(description = "The name of the jigsaw piece")
                    String piece,
            @Param(description = "The project to add the jigsaw piece to")
                    String project,
            @Param(description = "The object to use for this piece", customHandler = ObjectHandler.class)
                    String object
    ) {
        IrisObject o = IrisData.loadAnyObject(object);

        if (object == null) {
            sender().sendMessage(C.RED + "Failed to find existing object");
            return;
        }

        File dest = Iris.instance.getDataFile("packs", project, "jigsaw-pieces", piece + ".json");
        new JigsawEditor(player(), null, o, dest);
        sender().sendMessage(C.GRAY + "* Right Click blocks to make them connectors");
        sender().sendMessage(C.GRAY + "* Right Click connectors to orient them");
        sender().sendMessage(C.GRAY + "* Shift + Right Click connectors to remove them");
        sender().sendMessage(C.GREEN + "Remember to use /iris jigsaw save");
    }

    @Decree(description = "Exit the current jigsaw editor")
    public void exit() {
        JigsawEditor editor = JigsawEditor.editors.get(player());

        if (editor == null) {
            sender().sendMessage(C.GOLD + "You don't have any pieces open to exit!");
            return;
        }

        editor.exit();
        sender().sendMessage(C.GREEN + "Exited Jigsaw Editor");
    }

    @Decree(description = "Save & Exit the current jigsaw editor")
    public void save() {
        JigsawEditor editor = JigsawEditor.editors.get(player());

        if (editor == null) {
            sender().sendMessage(C.GOLD + "You don't have any pieces open to save!");
            return;
        }

        editor.close();
        sender().sendMessage(C.GREEN + "Saved & Exited Jigsaw Editor");
    }
}
