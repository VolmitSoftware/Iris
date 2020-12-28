package net.querz.mca;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides main and utility functions to read and write .mca files and
 * to convert block, chunk and region coordinates.
 * */
public final class MCAUtil {

	private MCAUtil() {}

	/**
	 * @see MCAUtil#read(File)
	 * @param file The file to read the data from.
	 * @return An in-memory representation of the MCA file with decompressed chunk data.
	 * @throws IOException if something during deserialization goes wrong.
	 * */
	public static MCAFile read(String file) throws IOException {
		return read(new File(file), LoadFlags.ALL_DATA);
	}

	/**
	 * Reads an MCA file and loads all of its chunks.
	 * @param file The file to read the data from.
	 * @return An in-memory representation of the MCA file with decompressed chunk data.
	 * @throws IOException if something during deserialization goes wrong.
	 * */
	public static MCAFile read(File file) throws IOException {
		return read(file, LoadFlags.ALL_DATA);
	}

	/**
	 * @see MCAUtil#read(File)
	 * @param file The file to read the data from.
	 * @return An in-memory representation of the MCA file with decompressed chunk data.
	 * @param loadFlags A logical or of {@link LoadFlags} constants indicating what data should be loaded
	 * @throws IOException if something during deserialization goes wrong.
	 * */
	public static MCAFile read(String file, long loadFlags) throws IOException {
		return read(new File(file), loadFlags);
	}

	/**
	 * Reads an MCA file and loads all of its chunks.
	 * @param file The file to read the data from.
	 * @return An in-memory representation of the MCA file with decompressed chunk data
	 * @param loadFlags A logical or of {@link LoadFlags} constants indicating what data should be loaded
	 * @throws IOException if something during deserialization goes wrong.
	 * */
	public static MCAFile read(File file, long loadFlags) throws IOException {
		MCAFile mcaFile = newMCAFile(file);
		try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
			mcaFile.deserialize(raf, loadFlags);
			return mcaFile;
		}
	}

	/**
	 * Calls {@link MCAUtil#write(MCAFile, File, boolean)} without changing the timestamps.
	 * @see MCAUtil#write(MCAFile, File, boolean)
	 * @param file The file to write to.
	 * @param mcaFile The data of the MCA file to write.
	 * @return The amount of chunks written to the file.
	 * @throws IOException If something goes wrong during serialization.
	 * */
	public static int write(MCAFile mcaFile, String file) throws IOException {
		return write(mcaFile, new File(file), false);
	}

	/**
	 * Calls {@link MCAUtil#write(MCAFile, File, boolean)} without changing the timestamps.
	 * @see MCAUtil#write(MCAFile, File, boolean)
	 * @param file The file to write to.
	 * @param mcaFile The data of the MCA file to write.
	 * @return The amount of chunks written to the file.
	 * @throws IOException If something goes wrong during serialization.
	 * */
	public static int write(MCAFile mcaFile, File file) throws IOException {
		return write(mcaFile, file, false);
	}

	/**
	 * @see MCAUtil#write(MCAFile, File, boolean)
	 * @param file The file to write to.
	 * @param mcaFile The data of the MCA file to write.
	 * @param changeLastUpdate Whether to adjust the timestamps of when the file was saved.
	 * @return The amount of chunks written to the file.
	 * @throws IOException If something goes wrong during serialization.
	 * */
	public static int write(MCAFile mcaFile, String file, boolean changeLastUpdate) throws IOException {
		return write(mcaFile, new File(file), changeLastUpdate);
	}

	/**
	 * Writes an {@code MCAFile} object to disk. It optionally adjusts the timestamps
	 * when the file was last saved to the current date and time or leaves them at
	 * the value set by either loading an already existing MCA file or setting them manually.<br>
	 * If the file already exists, it is completely overwritten by the new file (no modification).
	 * @param file The file to write to.
	 * @param mcaFile The data of the MCA file to write.
	 * @param changeLastUpdate Whether to adjust the timestamps of when the file was saved.
	 * @return The amount of chunks written to the file.
	 * @throws IOException If something goes wrong during serialization.
	 * */
	public static int write(MCAFile mcaFile, File file, boolean changeLastUpdate) throws IOException {
		File to = file;
		if (file.exists()) {
			to = File.createTempFile(to.getName(), null);
		}
		int chunks;
		try (RandomAccessFile raf = new RandomAccessFile(to, "rw")) {
			chunks = mcaFile.serialize(raf, changeLastUpdate);
		}

		if (chunks > 0 && to != file) {
			Files.move(to.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		return chunks;
	}

	/**
	 * Turns the chunks coordinates into region coordinates and calls
	 * {@link MCAUtil#createNameFromRegionLocation(int, int)}
	 * @param chunkX The x-value of the location of the chunk.
	 * @param chunkZ The z-value of the location of the chunk.
	 * @return A mca filename in the format "r.{regionX}.{regionZ}.mca"
	 * */
	public static String createNameFromChunkLocation(int chunkX, int chunkZ) {
		return createNameFromRegionLocation( chunkToRegion(chunkX), chunkToRegion(chunkZ));
	}

	/**
	 * Turns the block coordinates into region coordinates and calls
	 * {@link MCAUtil#createNameFromRegionLocation(int, int)}
	 * @param blockX The x-value of the location of the block.
	 * @param blockZ The z-value of the location of the block.
	 * @return A mca filename in the format "r.{regionX}.{regionZ}.mca"
	 * */
	public static String createNameFromBlockLocation(int blockX, int blockZ) {
		return createNameFromRegionLocation(blockToRegion(blockX), blockToRegion(blockZ));
	}

	/**
	 * Creates a filename string from provided chunk coordinates.
	 * @param regionX The x-value of the location of the region.
	 * @param regionZ The z-value of the location of the region.
	 * @return A mca filename in the format "r.{regionX}.{regionZ}.mca"
	 * */
	public static String createNameFromRegionLocation(int regionX, int regionZ) {
		return "r." + regionX + "." + regionZ + ".mca";
	}

	/**
	 * Turns a block coordinate value into a chunk coordinate value.
	 * @param block The block coordinate value.
	 * @return The chunk coordinate value.
	 * */
	public static int blockToChunk(int block) {
		return block >> 4;
	}

	/**
	 * Turns a block coordinate value into a region coordinate value.
	 * @param block The block coordinate value.
	 * @return The region coordinate value.
	 * */
	public static int blockToRegion(int block) {
		return block >> 9;
	}

	/**
	 * Turns a chunk coordinate value into a region coordinate value.
	 * @param chunk The chunk coordinate value.
	 * @return The region coordinate value.
	 * */
	public static int chunkToRegion(int chunk) {
		return chunk >> 5;
	}

	/**
	 * Turns a region coordinate value into a chunk coordinate value.
	 * @param region The region coordinate value.
	 * @return The chunk coordinate value.
	 * */
	public static int regionToChunk(int region) {
		return region << 5;
	}

	/**
	 * Turns a region coordinate value into a block coordinate value.
	 * @param region The region coordinate value.
	 * @return The block coordinate value.
	 * */
	public static int regionToBlock(int region) {
		return region << 9;
	}

	/**
	 * Turns a chunk coordinate value into a block coordinate value.
	 * @param chunk The chunk coordinate value.
	 * @return The block coordinate value.
	 * */
	public static int chunkToBlock(int chunk) {
		return chunk << 4;
	}

	private static final Pattern mcaFilePattern = Pattern.compile("^.*r\\.(?<regionX>-?\\d+)\\.(?<regionZ>-?\\d+)\\.mca$");

	public static MCAFile newMCAFile(File file) {
		Matcher m = mcaFilePattern.matcher(file.getName());
		if (m.find()) {
			return new MCAFile(Integer.parseInt(m.group("regionX")), Integer.parseInt(m.group("regionZ")));
		}
		throw new IllegalArgumentException("invalid mca file name: " + file.getName());
	}
}
