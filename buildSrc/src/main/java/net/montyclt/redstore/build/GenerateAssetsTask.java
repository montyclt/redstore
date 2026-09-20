package net.montyclt.redstore.build;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
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
 *   <li><b>redstone clock</b> — the repeater plate with a mode badge and a clock dial engraved on
 *       it, and the repeater's own delay models with nothing changed but their textures.</li>
 *   <li><b>logic gates</b> — the same plate with the painted redstone line rubbed out, a panel of
 *       the gate's metal set into it, and the repeater's torch copied to three positions.</li>
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

	/** Vanilla's own unlit and lit redstone reds, sampled from block/repeater. */
	private static final int ENGRAVED_OFF = 0xFF580101;
	private static final int ENGRAVED_ON = 0xFFD70304;

	/** Gold and shadow from the vanilla clock item. */
	private static final int DIAL_GOLD = 0xFFFAD64A;
	private static final int DIAL_DARK = 0xFF752802;

	private static final int PLATE_GREY = 0xFFA4A7A1;

	// ---------------------------------------------------------------- glyphs

	/**
	 * Glyphs are ASCII art so the edit lives in the diff rather than in a binary. '#' takes the
	 * main colour, '+' the accent, and 'L', 'M' and 'D' a palette's light, mid and dark tones.
	 *
	 * <p>The clock's two glyphs are mode badges, not a picture of the signal: a solid 2x2 square
	 * for the square wave, a 2x1 bar for the pulse. Drawing the real waveform was tried twice and
	 * abandoned — a glyph cannot know the delay setting, so it drew the same cycles at 1 tick and
	 * at 4, a timing diagram that lied about the timing. A badge claims nothing it cannot keep.
	 */
	private static final String[] GLYPH_SQUARE = {"##", "##"};
	private static final String[] GLYPH_PULSE = {"##", ".."};

	/** A clock face, so the block says what it is and not only what it does. */
	private static final String[] GLYPH_DIAL = {".###.", "#.+.#", "#.++#", "#...#", ".###."};

	private static final int GLYPH_X = 11;
	private static final int GLYPH_Y = 9;
	private static final int DIAL_X = 1;
	private static final int DIAL_Y = 8;

	/**
	 * Vanilla's idiom for "the same plate, a different job" is an inlay, not a symbol: the
	 * comparator is the repeater's plate with quartz set into it. Each gate follows that with the
	 * metal its recipe calls for. The dark border is what makes it work — iron is barely brighter
	 * than the stone it sits on, so the panel reads by its outline and its flatness, not its hue.
	 */
	private static final String[] GATE_INLAY = {
			"DDDDDD", "DLLLLD", "DLLLLD", "DMMMMD", "DMMMMD", "DDDDDD"};
	private static final int GATE_INLAY_X = 5;
	private static final int GATE_INLAY_Y = 7;

	/**
	 * Inversion is a bubble between the output torch and the panel, carved in the metal's dark
	 * tone rather than in redstone red: a gate's recipe has no redstone dust in it, and nothing on
	 * its face may imply an ingredient that is not there.
	 */
	private static final String[] GATE_BUBBLE = {".##.", "#..#", ".##."};
	private static final int GATE_BUBBLE_X = 6;
	private static final int GATE_BUBBLE_Y = 4;

	/**
	 * Three torches, like the comparator: one at each input flank and one at the output. Offsets
	 * from the repeater's own torch, which sits at x=7..9, z=2..4. They stop two pixels short of
	 * the plate's edge so they do not cover the shading that reads as its side.
	 */
	private static final int[][] GATE_TORCH_OFFSETS = {{-4, 5}, {4, 5}, {0, 0}};

	/** light, mid, dark, sampled from each ingot's own texture. */
	private static final Map<String, int[]> GATE_METALS = new LinkedHashMap<>();

	static {
		GATE_METALS.put("and_gate", new int[]{0xFFD8D8D8, 0xFFA8A8A8, 0xFF5E5E5E});
	}

	/**
	 * The gate icon comes from the comparator's sprite, not the repeater's: the comparator already
	 * draws three torches, which is what a gate has. Its painted line is rubbed out, as on the
	 * block, and a chip of the metal goes where the block carries its panel.
	 */
	private static final int[][] ICON_LINE = {{5, 9}, {6, 9}, {5, 10}, {6, 10}, {7, 10}, {8, 10}, {7, 11}, {8, 11}};
	private static final String[] ICON_CHIP = {"DDDD", "DLLD", "DMMD"};
	private static final int ICON_CHIP_X = 5;
	private static final int ICON_CHIP_Y = 9;

	/** The clock's icon keeps both torches, because the block has both. */
	private static final String[] ICON_DIAL = {".##.", "#.+#", "#++#", ".##."};
	private static final int ICON_DIAL_X = 5;
	private static final int ICON_DIAL_Y = 8;

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
			redstoneClock(jar, out);
			logicGates(jar, out);
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

	private void redstoneClock(ZipFile jar, Path out) throws IOException {
		BufferedImage icon = readPng(jar, "assets/minecraft/textures/item/repeater.png");
		stamp(icon, ICON_DIAL, ICON_DIAL_X, ICON_DIAL_Y, DIAL_GOLD, DIAL_DARK, null);
		writePng(out, "textures/item/redstone_clock.png", icon);

		for (String mode : new String[]{"", "_pulse"}) {
			String[] glyph = mode.isEmpty() ? GLYPH_SQUARE : GLYPH_PULSE;

			for (String lit : new String[]{"", "_on"}) {
				String base = lit.isEmpty() ? "repeater" : "repeater_on";
				int colour = lit.isEmpty() ? ENGRAVED_OFF : ENGRAVED_ON;

				BufferedImage plate = readPng(jar, "assets/minecraft/textures/block/" + base + ".png");
				stamp(plate, glyph, GLYPH_X, GLYPH_Y, colour, 0, null);
				stamp(plate, GLYPH_DIAL, DIAL_X, DIAL_Y, DIAL_GOLD, DIAL_DARK, null);
				writePng(out, "textures/block/redstone_clock_top" + mode + lit + ".png", plate);
			}
		}

		// The repeater's model already encodes the delay as the position of a sliding torch, and a
		// clock wants exactly that plus a second, fixed torch — which is the repeater, unchanged.
		// A locked clock is always off, so only the unlit locked models are generated; the block
		// state file points both powered values at them.
		String[][] variants = {{"", ""}, {"", "_on"}, {"_pulse", ""}, {"_pulse", "_on"},
				{"", "_locked"}, {"_pulse", "_locked"}};

		for (int delay = 1; delay <= 4; delay++) {
			for (String[] variant : variants) {
				String mode = variant[0];
				String suffix = variant[1];
				boolean locked = suffix.equals("_locked");
				boolean lit = suffix.equals("_on");

				JsonObject model = readJson(jar,
						"assets/minecraft/models/block/repeater_" + delay + "tick" + suffix + ".json");

				String top = "redstore:block/redstone_clock_top" + mode + (lit ? "_on" : "");
				JsonObject textures = new JsonObject();
				textures.addProperty("particle", top);
				textures.addProperty("slab", "minecraft:block/smooth_stone");
				textures.addProperty("top", top);

				// A locked model keeps the fixed torch and adds the bedrock bar, so it needs both
				// texture keys. Setting only the bar left #unlit dangling and the game logged a
				// missing texture reference for all eight locked models.
				if (locked) {
					textures.addProperty("lock", "minecraft:block/bedrock");
				}

				if (lit) {
					textures.addProperty("lit", "minecraft:block/redstone_torch");
				} else {
					textures.addProperty("unlit", "minecraft:block/redstone_torch_off");
				}

				model.add("textures", textures);
				writeJson(out, "models/block/redstone_clock_" + delay + "tick" + mode + suffix + ".json", model);
			}
		}
	}

	private void logicGates(ZipFile jar, Path out) throws IOException {
		for (Map.Entry<String, int[]> entry : GATE_METALS.entrySet()) {
			String gate = entry.getKey();
			int light = entry.getValue()[0];
			int mid = entry.getValue()[1];
			int dark = entry.getValue()[2];

			Map<Character, Integer> metal = new LinkedHashMap<>();
			metal.put('L', light);
			metal.put('M', mid);
			metal.put('D', dark);

			for (String suffix : new String[]{"", "_inverted"}) {
				BufferedImage plate = readPng(jar, "assets/minecraft/textures/block/repeater.png");
				eraseSignalLine(plate);

				if (!suffix.isEmpty()) {
					stamp(plate, GATE_BUBBLE, GATE_BUBBLE_X, GATE_BUBBLE_Y, dark, 0, null);
				}

				stamp(plate, GATE_INLAY, GATE_INLAY_X, GATE_INLAY_Y, light, 0, metal);
				writePng(out, "textures/block/" + gate + "_top" + suffix + ".png", plate);

				// One texture serves both models: what changes when a gate fires is its torches.
				String top = "redstore:block/" + gate + "_top" + suffix;

				for (String lit : new String[]{"", "_on"}) {
					JsonObject source = readJson(jar,
							"assets/minecraft/models/block/repeater_1tick" + lit + ".json");
					JsonArray elements = source.getAsJsonArray("elements");

					JsonArray kept = new JsonArray();
					kept.add(elements.get(0));
					gateTorches(elements, !lit.isEmpty()).forEach(kept::add);
					source.add("elements", kept);

					JsonObject textures = new JsonObject();
					textures.addProperty("particle", top);
					textures.addProperty("slab", "minecraft:block/smooth_stone");
					textures.addProperty("top", top);
					textures.addProperty(lit.isEmpty() ? "unlit" : "lit",
							lit.isEmpty() ? "minecraft:block/redstone_torch_off" : "minecraft:block/redstone_torch");
					source.add("textures", textures);

					writeJson(out, "models/block/" + gate + suffix + lit + ".json", source);
				}
			}

			BufferedImage icon = readPng(jar, "assets/minecraft/textures/item/comparator.png");

			for (int[] pixel : ICON_LINE) {
				icon.setRGB(pixel[0], pixel[1], PLATE_GREY);
			}

			stamp(icon, ICON_CHIP, ICON_CHIP_X, ICON_CHIP_Y, light, 0, metal);
			writePng(out, "textures/item/" + gate + ".png", icon);
		}
	}

	/**
	 * Three torches, from the repeater's fixed torch copied to each position.
	 *
	 * <p>In a lit model a torch drags six glow quads behind it, all positioned relative to it, so
	 * the whole group moves together. Element order is identical in all of vanilla's repeater
	 * models, which is what makes this safe to do positionally: 0 is the slab, 1 the sliding
	 * torch, 2 the fixed torch, and in the lit models 3-8 are the fixed torch's glow quads.
	 * Matching on geometry instead does not work — at two ticks the two torches' glow quads occupy
	 * the same box.
	 */
	private static List<JsonElement> gateTorches(JsonArray elements, boolean lit) {
		int count = lit ? 7 : 1;
		List<JsonElement> moved = new java.util.ArrayList<>();

		for (int[] offset : GATE_TORCH_OFFSETS) {
			for (int i = 2; i < 2 + count; i++) {
				JsonObject element = elements.get(i).getAsJsonObject().deepCopy();
				shift(element.getAsJsonArray("from"), offset);
				shift(element.getAsJsonArray("to"), offset);
				moved.add(element);
			}
		}

		return moved;
	}

	private static void shift(JsonArray corner, int[] offset) {
		corner.set(0, number(corner.get(0).getAsDouble() + offset[0]));
		corner.set(2, number(corner.get(2).getAsDouble() + offset[1]));
	}

	/** Vanilla writes whole coordinates without a decimal point, so keep it that way. */
	private static JsonElement number(double value) {
		if (value == Math.rint(value)) {
			return GSON.toJsonTree((int) value);
		}

		return GSON.toJsonTree(value);
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

	/**
	 * Rub out the redstone line the repeater texture paints along its torch track.
	 *
	 * <p>A gate has no sliding torch and nothing travels that path, so the line would be
	 * decoration that lies. Erased pixels take the colour three columns to their left, which on
	 * this texture is always plain plate, so vanilla's own shading noise carries over instead of a
	 * flat patch.
	 */
	private static void eraseSignalLine(BufferedImage image) {
		for (int y = 0; y < 16; y++) {
			for (int x = 3; x < 16; x++) {
				int pixel = image.getRGB(x, y);
				int r = (pixel >> 16) & 0xFF;
				int g = (pixel >> 8) & 0xFF;
				int b = pixel & 0xFF;

				if (r > g + 20 || (r == 120 && g == 120 && b == 120)) {
					image.setRGB(x, y, image.getRGB(x - 3, y));
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
