//package com.volmit.iris.core.link;
//
//import com.jojodmo.customitems.api.CustomItemsAPI;
//import com.jojodmo.customitems.item.custom.CustomItem;
//import com.jojodmo.customitems.item.custom.block.CustomMushroomBlock;
//import com.jojodmo.customitems.version.SafeMaterial;
//import com.volmit.iris.util.collection.KList;
//import com.volmit.iris.util.reflect.WrappedField;
//import com.volmit.iris.util.reflect.WrappedReturningMethod;
//import org.bukkit.block.BlockFace;
//import org.bukkit.block.data.BlockData;
//import org.bukkit.block.data.MultipleFacing;
//import org.bukkit.inventory.ItemStack;
//
//import java.util.Map;
//import java.util.MissingResourceException;
//
//public class CustomItemsDataProvider extends ExternalDataProvider {
//
//    private static final String FIELD_FACES = "faces";
//    private static final String METHOD_GET_MATERIAL = "getMaterial";
//
//    private WrappedField<CustomMushroomBlock, Map<Integer, boolean[]>> mushroomFaces;
//    private WrappedReturningMethod<CustomMushroomBlock, SafeMaterial> mushroomMaterial;
//
//    public CustomItemsDataProvider() {
//        super("CustomItems");
//    }
//
//    @Override
//    public void init() {
//        this.mushroomFaces = new WrappedField<>(CustomMushroomBlock.class, FIELD_FACES);
//        this.mushroomMaterial = new WrappedReturningMethod<>(CustomMushroomBlock.class, METHOD_GET_MATERIAL);
//    }
//
//    @Override
//    public BlockData getBlockData(Identifier blockId) throws MissingResourceException {
//        CustomItem item = CustomItem.get(blockId.key());
//        if(item == null) {
//            throw new MissingResourceException("Failed to find BlockData!", blockId.namespace(), blockId.key());
//        } else if(item.getBlockTexture().isSpawner()) {
//            throw new MissingResourceException("Iris does not yet support SpawnerBlocks from CustomItems.", blockId.namespace(), blockId.key());
//        } else if(item.getBlockTexture() != null && item.getBlockTexture().isValid()) {
//            throw new MissingResourceException("Tried to fetch BlockData for a CustomItem that is not placeable!", blockId.namespace(), blockId.key());
//        }
//        return getMushroomData(item);
//    }
//
//    @Override
//    public ItemStack getItemStack(Identifier itemId) throws MissingResourceException {
//        ItemStack stack = CustomItemsAPI.getCustomItem(itemId.key());
//        if(stack == null) {
//            throw new MissingResourceException("Failed to find ItemData!", itemId.namespace(), itemId.key());
//        }
//        return stack;
//    }
//
//    @Override
//    public Identifier[] getBlockTypes() {
//        KList<Identifier> names = new KList<>();
//        for (String name : CustomItemsAPI.listBlockCustomItemIDs()) {
//            try {
//                Identifier key = new Identifier("cui", name);
//                if (getItemStack(key) != null)
//                    names.add(key);
//            } catch (MissingResourceException ignored) { }
//        }
//
//        return names.toArray(new Identifier[0]);
//    }
//
//    @Override
//    public Identifier[] getItemTypes() {
//        KList<Identifier> names = new KList<>();
//        for (String name : CustomItemsAPI.listCustomItemIDs()) {
//            try {
//                Identifier key = new Identifier("cui", name);
//                if (getItemStack(key) != null)
//                    names.add(key);
//            } catch (MissingResourceException ignored) { }
//        }
//
//        return names.toArray(new Identifier[0]);
//    }
//
//    @Override
//    public boolean isValidProvider(Identifier key, boolean isItem) {
//        return key.namespace().equalsIgnoreCase("cui");
//    }
//
//    private BlockData getMushroomData(CustomItem item) {
//        MultipleFacing data = (MultipleFacing)mushroomMaterial.invoke(item.getBlockTexture().getMushroomId()).parseMaterial().createBlockData();
//        boolean[] values = mushroomFaces.get().get(item.getBlockTexture().getMushroomId());
//        data.setFace(BlockFace.DOWN, values[0]);
//        data.setFace(BlockFace.EAST, values[1]);
//        data.setFace(BlockFace.NORTH, values[2]);
//        data.setFace(BlockFace.SOUTH, values[3]);
//        data.setFace(BlockFace.UP, values[4]);
//        data.setFace(BlockFace.WEST, values[5]);
//        return data;
//    }
//}
