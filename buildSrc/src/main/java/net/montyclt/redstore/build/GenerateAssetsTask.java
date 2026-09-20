package net.montyclt.redstore.build;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * Derives the mod's textures and block models from the vanilla Minecraft jar.
 *
 * <p>Several of Redstore's assets are not original art: they are vanilla files with a small,
 * precisely defined edit. Keeping that edit as code has two consequences, and both are the point.
 * The edit stays reviewable in a diff and can be replayed whenever Mojang retouches an original;
 * and the repository never contains a modified Mojang texture, because every one of them is
 * produced at build time from the player's own copy of the game.
 *
 * <p>What is derived:
 *
 * <ul>
 *   <li><b>filter hopper</b> — a hopper with the rim of its mouth recoloured to the item frame's
 *       wood, plus its two block models with the texture references swapped.</li>
 * </ul>
 *
 * <p>See spec/conventions.md section 10.
 */
public abstract class GenerateAssetsTask extends DefaultTask {
	// ---------------------------------------------------------------- palettes

	/**
	 * The hopper's grey shading ramp mapped onto the item frame's wood ramp, pairing the two by
	 * lightness. Only the hue changes, so vanilla's pixel-art shading survives untouched; a
	 * multiplicative tint would have flattened it.
	 */
	private static final Map<Integer, Integer> BLOCK_WOOD = ramp(
			0xFF676161, 0xFFAC5D31,
			0xFF595858, 0xFFA45531,
			0xFF4F4F4F, 0xFF944C29,
			0xFF494848, 0xFF834829,
			0xFF3F3E42, 0xFF7B4429,
			0xFF343438, 0xFF734029,
			0xFF2D2D32, 0xFF603623);

	/** The inventory icon is drawn with its own, slightly different greys. */
	private static final Map<Integer, Integer> ITEM_WOOD = ramp(
			0xFF626162, 0xFFAC5D31,
			0xFF525552, 0xFFA45531,
			0xFF4A4C4A, 0xFF944C29,
			0xFF414441, 0xFF834829,
			0xFF3E3E3E, 0xFF7B4429,
			0xFF383838, 0xFF734029,
			0xFF303030, 0xFF603623);

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@InputFile
	public abstract RegularFileProperty getMinecraftJar();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@TaskAction
	public void generate() throws IOException {
		Path out = getOutputDirectory().get().getAsFile().toPath().resolve("assets/redstore");
		Files.createDirectories(out);

		try (ZipFile jar = new ZipFile(getMinecraftJar().get().getAsFile())) {
			filterHopper(jar, out);
		}
	}

	// ---------------------------------------------------------------- blocks

	private void filterHopper(ZipFile jar, Path out) throws IOException {
		BufferedImage top = readPng(jar, "assets/minecraft/textures/block/hopper_top.png");
		// The outer two-pixel ring of the top face becomes the frame.
		recolour(top, BLOCK_WOOD, (x, y) -> Math.min(Math.min(x, y), Math.min(15 - x, 15 - y)) <= 1);

		BufferedImage side = readPng(jar, "assets/minecraft/textures/block/hopper_outside.png");
		// The collar spans y=11..16 in the model and its faces declare no UV, so they default to
		// v = 16 - y: rows 0..4 of this texture. Rows 0-1 are the top of it.
		recolour(side, BLOCK_WOOD, (x, y) -> y <= 1);

		BufferedImage icon = readPng(jar, "assets/minecraft/textures/item/hopper.png");
		// The icon's rim, following its isometric outline: the top edge, then the corners down.
		recolour(icon, ITEM_WOOD, (x, y) -> y == 2
				|| (y == 3 && (x == 2 || x == 3 || x == 12 || x == 13))
				|| (y == 4 && (x == 1 || x == 2 || x == 13 || x == 14)));

		writePng(out, "textures/block/filter_hopper_top.png", top);
		writePng(out, "textures/block/filter_hopper_outside.png", side);
		writePng(out, "textures/item/filter_hopper.png", icon);

		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "redstore:block/filter_hopper_outside");
		textures.addProperty("top", "redstore:block/filter_hopper_top");
		textures.addProperty("side", "redstore:block/filter_hopper_outside");
		// The funnel's inner face has no rim to recolour, so it stays vanilla.
		textures.addProperty("inside", "minecraft:block/hopper_inside");

		for (String[] pair : new String[][]{{"hopper", "filter_hopper"}, {"hopper_side", "filter_hopper_side"}}) {
			JsonObject model = readJson(jar, "assets/minecraft/models/block/" + pair[0] + ".json");
			model.add("textures", textures.deepCopy());
			writeJson(out, "models/block/" + pair[1] + ".json", model);
		}
	}

	// ---------------------------------------------------------------- pixels

	private interface Mask {
		boolean test(int x, int y);
	}

	/** Apply a ramp to every pixel the mask selects, leaving transparent pixels alone. */
	private static void recolour(BufferedImage image, Map<Integer, Integer> ramp, Mask mask) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int pixel = image.getRGB(x, y);

				if ((pixel >>> 24) == 0 || !mask.test(x, y)) {
					continue;
				}

				Integer replacement = ramp.get(pixel);

				if (replacement != null) {
					image.setRGB(x, y, replacement);
				}
			}
		}
	}

	/** Engrave an ASCII-art glyph onto a texture. '.' is always left alone. */
	private static void stamp(BufferedImage image, String[] glyph, int x0, int y0,
			int colour, int accent, Map<Character, Integer> palette) {
		for (int dy = 0; dy < glyph.length; dy++) {
			String line = glyph[dy];

			for (int dx = 0; dx < line.length(); dx++) {
				char c = line.charAt(dx);

				if (c == '#') {
					image.setRGB(x0 + dx, y0 + dy, colour);
				} else if (c == '+' && accent != 0) {
					image.setRGB(x0 + dx, y0 + dy, accent);
				} else if (palette != null && palette.containsKey(c)) {
					image.setRGB(x0 + dx, y0 + dy, palette.get(c));
				}
			}
		}
	}

	// ---------------------------------------------------------------- io

	private static BufferedImage readPng(ZipFile jar, String path) throws IOException {
		try (InputStream in = open(jar, path)) {
			BufferedImage source = ImageIO.read(in);
			// Palette images come back indexed; copy into ARGB so every pixel is writable.
			BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(),
					BufferedImage.TYPE_INT_ARGB);
			copy.getGraphics().drawImage(source, 0, 0, null);

			return copy;
		}
	}

	private static JsonObject readJson(ZipFile jar, String path) throws IOException {
		try (InputStream in = open(jar, path)) {
			return GSON.fromJson(new java.io.InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		}
	}

	private static InputStream open(ZipFile jar, String path) throws IOException {
		ZipEntry entry = jar.getEntry(path);

		if (entry == null) {
			throw new GradleException(path + " is not in the Minecraft jar. "
					+ "Either the version changed or vanilla renamed it.");
		}

		return jar.getInputStream(entry);
	}

	private static void writePng(Path root, String name, BufferedImage image) throws IOException {
		Path file = root.resolve(name);
		Files.createDirectories(file.getParent());
		ImageIO.write(image, "PNG", file.toFile());
	}

	private static void writeJson(Path root, String name, JsonObject json) throws IOException {
		Path file = root.resolve(name);
		Files.createDirectories(file.getParent());

		try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			GSON.toJson(json, writer);
			writer.write("\n");
		}
	}

	private static Map<Integer, Integer> ramp(int... pairs) {
		Map<Integer, Integer> map = new LinkedHashMap<>();

		for (int i = 0; i < pairs.length; i += 2) {
			map.put(pairs[i], pairs[i + 1]);
		}

		return map;
	}
}
