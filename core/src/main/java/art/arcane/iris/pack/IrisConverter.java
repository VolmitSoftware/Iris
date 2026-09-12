package art.arcane.iris.pack;

import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.structure.object.IrisObject;
import art.arcane.volmlib.util.data.Varint;
import art.arcane.iris.localization.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.nbt.io.NBTUtil;
import art.arcane.volmlib.util.nbt.io.NamedTag;
import art.arcane.volmlib.util.nbt.tag.ByteArrayTag;
import art.arcane.volmlib.util.nbt.tag.CompoundTag;
import art.arcane.volmlib.util.nbt.tag.IntTag;
import art.arcane.volmlib.util.nbt.tag.ShortTag;
import art.arcane.volmlib.util.nbt.tag.Tag;
import art.arcane.iris.platform.bukkit.plugin.VolmitSender;
import art.arcane.iris.world.task.J;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import art.arcane.iris.localization.BukkitRuntimeMessages;
import art.arcane.iris.localization.IrisLanguage;
import art.arcane.iris.localization.RuntimeUiMessages;
import art.arcane.volmlib.util.localization.MessageArgument;
public class IrisConverter {
    public static void convertSchematics(VolmitSender sender) {
        File folder = IrisPlatforms.get().dataFolder("convert");

        FilenameFilter filter = (dir, name) -> name.endsWith(".schem");
        File[] fileList = folder.listFiles(filter);
        if (fileList == null) {
            sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_CONVERTER_NO_SCHEMATIC_FILES_CONVERT_FOUND, MessageArgument.untrusted("path", String.valueOf(folder.getAbsolutePath()))));
            return;
        }

        AtomicInteger counter = new AtomicInteger(0);
        var stopwatch = PrecisionStopwatch.start();
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.submit(() -> {
            for (File schem : fileList) {
                try {
                    PrecisionStopwatch p = PrecisionStopwatch.start();
                    IrisObject object;
                    boolean largeObject = false;
                    NamedTag tag;
                    try {
                        tag = NBTUtil.read(schem);
                    } catch (IOException e) {
                        IrisLogging.info(C.RED + "Failed to read: " + schem.getName());
                        throw new RuntimeException(e);
                    }
                    CompoundTag compound = (CompoundTag) tag.getTag();
                    int version = resolveVersion(compound);
                    if (!(version == 2 || version == 3))
                        throw new RuntimeException(C.RED + "Unsupported schematic version: " + version);

                    compound = version == 3 ? (CompoundTag) compound.get("Schematic") : compound;
                    int objW = ((ShortTag) compound.get("Width")).getValue();
                    int objH = ((ShortTag) compound.get("Height")).getValue();
                    int objD = ((ShortTag) compound.get("Length")).getValue();
                    int i = -1;
                    int mv = objW * objH * objD;
                    AtomicInteger v = new AtomicInteger(0);
                    boolean reportProgress = mv > 2_000_000 && sender.isPlayer();
                    // try/finally over the whole decode: a throw must never leak the
                    // self-rescheduling progress task or the HUD lane.
                    try {
                    if (mv > 2_000_000) {
                        largeObject = true;
                        IrisLogging.info(C.GRAY + "Converting.. " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        IrisLogging.info(C.GRAY + "- It may take a while");
                        if (reportProgress) {
                            i = J.ar(() -> {
                                double conversionProgress = (double) v.get() / mv;
                                sender.sendProgress(conversionProgress, IrisLanguage.text(RuntimeUiMessages.CONVERTING));
                                BukkitPlatform.showProgressLane(sender.player(), "iris:job", IrisLanguage.text(RuntimeUiMessages.CONVERTING) + " " + Form.pc(conversionProgress, 0), conversionProgress, 4000L);
                            }, 0);
                        }
                    }

                    compound = version == 3 ? (CompoundTag) compound.get("Blocks") : compound;
                    CompoundTag paletteTag = (CompoundTag) compound.get("Palette");
                    Map<Integer, BlockData> blockmap = new HashMap<>(paletteTag.size(), 0.9f);
                    for (Map.Entry<String, Tag<?>> entry : paletteTag.getValue().entrySet()) {
                        String blockName = entry.getKey();
                        BlockData bd = Bukkit.createBlockData(blockName);
                        Tag<?> blockTag = entry.getValue();
                        int blockId = ((IntTag) blockTag).getValue();
                        blockmap.put(blockId, bd);
                    }

                    boolean isBytes = version == 3 ? compound.getByteArrayTag("Data").length() < 128 : ((IntTag) compound.get("PaletteMax")).getValue() < 128;
                    ByteArrayTag byteArray = version == 3 ? (ByteArrayTag) compound.get("Data") : (ByteArrayTag) compound.get("BlockData");
                    byte[] originalBlockArray = byteArray.getValue();
                    var din = new DataInputStream(new ByteArrayInputStream(originalBlockArray));
                    object = new IrisObject(objW, objH, objD);
                    for (int h = 0; h < objH; h++) {
                        for (int d = 0; d < objD; d++) {
                            for (int w = 0; w < objW; w++) {
                                int blockIndex = isBytes ? din.read() & 0xFF : Varint.readUnsignedVarInt(din);
                                BlockData bd = blockmap.get(blockIndex);
                                if (!bd.getMaterial().isAir()) {
                                    object.setUnsigned(w, h, d, art.arcane.iris.platform.bukkit.BukkitBlockState.of(bd));
                                }
                                v.getAndAdd(1);
                            }
                        }
                    }

                    } finally {
                        if (i != -1) J.car(i);
                        if (reportProgress) {
                            sender.sendAction(" ");
                            BukkitPlatform.hudLanes().hide(sender.player(), "iris:job");
                        }
                    }
                    try {
                        object.shrinkwrap();
                        object.write(new File(folder, schem.getName().replace(".schem", ".iob")));
                        counter.incrementAndGet();
                        if (sender.isPlayer()) {
                            if (largeObject) {
                                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_CONVERTER_CONVERTED, MessageArgument.untrusted("name", String.valueOf(schem.getName())), MessageArgument.untrusted("value", String.valueOf(schem.getName().replace(".schem", ".iob"))), MessageArgument.untrusted("value2", String.valueOf(Form.duration(p.getMillis())))));
                            } else {
                                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_CONVERTER_CONVERTED_2, MessageArgument.untrusted("name", String.valueOf(schem.getName())), MessageArgument.untrusted("value", String.valueOf(schem.getName().replace(".schem", ".iob")))));
                            }
                        }
                        if (largeObject) {
                            IrisLogging.info(C.GRAY + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob") + " in " + Form.duration(p.getMillis()));
                        } else {
                            IrisLogging.info(C.GRAY + "Converted " + schem.getName() + " -> " + schem.getName().replace(".schem", ".iob"));
                        }
                        FileUtils.delete(schem);
                    } catch (IOException e) {
                        sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_CONVERTER_FAILED_SAVE, MessageArgument.untrusted("name", String.valueOf(schem.getName()))));
                        throw new IOException(e);
                    }


                } catch (Exception e) {
                    sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_CONVERTER_FAILED_CONVERT, MessageArgument.untrusted("name", String.valueOf(schem.getName()))));
                    IrisLogging.reportError("Failed to convert schematic " + schem.getName() + ".", e);
                }
            }
            stopwatch.end();
            if (counter.get() != 0) {
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_CONVERTER_CONVERTED_3, MessageArgument.untrusted("get", String.valueOf(counter.get())), MessageArgument.untrusted("value", String.valueOf(Form.duration(stopwatch.getMillis())))));
            }
            if (counter.get() < fileList.length) {
                sender.sendMessage(IrisLanguage.text(BukkitRuntimeMessages.IRIS_CONVERTER_SOME_SCHEMATICS_FAILED_CONVERT_CHECK_CONSOLE_DETAILS));
            }
        });
        // Single-use pool: retire the worker once the queued conversion finishes instead of
        // leaking a non-daemon thread per invocation.
        executorService.shutdown();
    }

    private static int resolveVersion(CompoundTag compound) throws Exception {
        try {

            IntTag root = compound.getIntTag("Version");
            if (root != null) {
                return root.getValue();
            }
            CompoundTag schematic = (CompoundTag) compound.get("Schematic");
            return schematic.getIntTag("Version").getValue();
        } catch (NullPointerException e) {
            throw new Exception("Cannot resolve schematic version", e);
        }
    }
}
