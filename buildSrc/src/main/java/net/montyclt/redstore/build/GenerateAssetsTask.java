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
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
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
 *   <li><b>logic gates</b> — the comparator's plate with its painted redstone line rubbed out and
 *       its quartz recoloured to the gate's metal, on the comparator's own models.</li>
 *   <li><b>chunk loader</b> — the enchanting table, with the diamond at its corners recoloured to
 *       amethyst and its cloth dyed to the ender pearl's teal.</li>
 * </ul>
 *
 * <p>One asset is not derived from anything: the funnel the empty filter slot draws. It is ours,
 * drawn here rather than committed as a PNG so that it stays reviewable in a diff like every other
 * edit in this file.
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

	/** Gold and shadow from the vanilla clock item. */
	private static final int DIAL_GOLD = 0xFFFAD64A;
	private static final int DIAL_DARK = 0xFF752802;

	/**
	 * The clock that stands on the plate is the mod's own object, so it needs its own texture —
	 * but not its own colours. Every one of these is lifted from {@code item/clock_00.png}, which
	 * is why a gold clock on a stone plate looks like it came with the game.
	 */
	private static final int CLOCK_RIM = 0xFFFAD64A;
	private static final int CLOCK_EDGE = 0xFFB26411;
	private static final int CLOCK_FACE = 0xFFFBF7B7;
	private static final int CLOCK_HANDS = 0xFF181616;

	private static final int PLATE_GREY = 0xFFA4A7A1;

	/**
	 * The grey an empty slot's icon is drawn in: darker than the slot's own {@code #8B8B8B}, so
	 * the shape reads as engraved into the slot rather than laid on top of it. Flat, with no
	 * second tone anywhere — that flatness is what says <i>placeholder</i> and not <i>item</i>.
	 *
	 * <p>Vanilla has two families here and they disagree. The thirty sprites under
	 * {@code container/slot/} are flat {@code #9C9C9C} (or one of #808080, #838383, #ABABAB,
	 * #B9B9B9), which is <em>lighter</em> than the slot. But the icons every player actually
	 * pictures — the four armour slots and the offhand — are not sprites at all: they are painted
	 * into {@code gui/container/inventory.png}, in {@code #555555}. This follows those, because
	 * they are the ones a player has seen ten thousand times.
	 */
	private static final int SLOT_ICON_GREY = 0xFF555555;

	// ---------------------------------------------------------------- glyphs

	/**
	 * Vanilla's idiom for "the same plate, a different job" is an inlay, and the comparator is the
	 * worked example: its plate carries a piece of quartz set into it, drawn in four warm tones in
	 * {@code block/comparator.png}, rows 6 to 10. A gate is the same plate with its own metal in
	 * that same setting, so the block is not a repeater wearing a badge we invented — it is the
	 * comparator's own shape, in iron, copper or gold.
	 *
	 * <p>Recolouring rather than drawing has a second payoff: a resource pack that redraws the
	 * comparator gets followed for free, at whatever resolution, as long as it keeps the palette.
	 * Faithful 64x does, to the colour.
	 */
	private static final int[] QUARTZ = {0xFFEBDED4, 0xFFDDCBBE, 0xFFD3C7B9, 0xFFC5B8A9};

	/**
	 * The enchanting table wears diamond at its corners, drawn in five pale teals. A chunk loader
	 * wears amethyst instead, and that swap is the block's whole colour change: the cloth keeps the
	 * red vanilla dyed it, and the obsidian stays obsidian, because the block is made of it.
	 *
	 * <p>Lightest for lightest, out of `block/amethyst_block`'s own palette.
	 */
	private static final int[] TABLE_GEMS = {0xFFFFFFFF, 0xFFC3FBF1, 0xFFA2F6E7, 0xFF4AEDD1, 0xFF2CCDB1};
	private static final int[] AMETHYST = {0xFFFECBE6, 0xFFC890F0, 0xFFA678F1, 0xFF8D6ACC, 0xFF7A5BB5};

	/**
	 * The enchanting table's tablecloth, in the red Mojang dyed it, and the colour ours is dyed
	 * instead: the teal of {@code item/ender_pearl}, because the cloth is dyed the colour of what it
	 * holds.
	 *
	 * <p>Only the hue changes. Each of the five reds keeps its own brightness, so the folds and the
	 * shadow under the pearl are still Mojang's — the same trade the gates' inlay makes with the
	 * comparator's quartz.
	 */
	private static final int[] TABLE_CLOTH = {
			// Vanilla's five.
			0xFF6B002F, 0xFF7F0728, 0xFFA22929, 0xFF741D32, 0xFF58162C,

			// Three more that Faithful adds. Its gems are vanilla's exactly, and so are five of its
			// eight reds, but a 64 × 64 cloth has room for shading vanilla's has not — and a tone
			// this list does not name is a tone that stays red. That is what a half-dyed cloth
			// looks like, and it is why this list is the union of both packs rather than vanilla's
			// alone.
			0xFF951C29, 0xFF86212F, 0xFF6E1C31};
	private static final int PEARL_TEAL = 0xFF349988;

	/** What each gate is made of, taken from that metal's own ingot texture. */
	private static final Map<String, Integer> GATE_METALS = new LinkedHashMap<>();

	static {
		GATE_METALS.put("and_gate", 0xFFD4D4D4);
		GATE_METALS.put("or_gate", 0xFFC15A36);
		GATE_METALS.put("xor_gate", 0xFFFAD64A);
	}

	/**
	 * The gate icon comes from the comparator's sprite, not the repeater's: the comparator already
	 * draws three torches, which is what a gate has. Its painted line is rubbed out, as on the
	 * block, and a chip of the metal goes where the block carries its panel.
	 */
	private static final int[][] ICON_LINE = {{5, 9}, {6, 9}, {5, 10}, {6, 10}, {7, 10}, {8, 10}, {7, 11}, {8, 11}};

	/**
	 * The icon needs its own answer: at this size the comparator's sprite draws three torches on a
	 * plate five pixels deep and <b>no quartz at all</b>, so there is nothing to recolour. What
	 * goes on the plate instead is the smallest shape that reads as a stone set into it rather
	 * than a bar lying on it — which is what the four-by-three block of metal it replaces looked
	 * like.
	 */
	private static final String[] ICON_STONE = {".L.", "LML", ".M."};
	private static final int ICON_STONE_X = 5;
	private static final int ICON_STONE_Y = 9;

	/** The clock's icon keeps both torches, because the block has both. */
	private static final String[] ICON_DIAL = {".##.", "#.+#", "#++#", ".##."};
	private static final int ICON_DIAL_X = 5;
	private static final int ICON_DIAL_Y = 8;

	/**
	 * The same dial as geometry, for an icon with the room for a round one. In units of the glyph
	 * above — its centre and its radius — so the two forms cannot drift apart.
	 */
	private static final double ICON_DIAL_CX = 7.0;
	private static final double ICON_DIAL_CY = 10.0;
	private static final double ICON_DIAL_R = 2.0;

	/**
	 * A funnel, for the filter slot. Vanilla has no icon for this and would not: nothing in the
	 * game filters. The funnel is the universal interface symbol for it, and it happens to be the
	 * silhouette of a hopper, so it is the mod's own drawing and still says what the block is.
	 *
	 * <p>It is an <b>outline</b>, because that is what vanilla's slot icons are: a shield, an
	 * ingot or a shovel there is a one-pixel contour, never a filled shape. At 30 opaque pixels
	 * this sits in vanilla's own range, which runs from 20 (llama armor) to 72 (banner pattern).
	 *
	 * <p>The spout is four pixels wide and not two, which is the width of a hopper's own spout in
	 * its model, and the reason is that a contour needs a hole. Two walls with nothing between
	 * them are not a pipe seen from outside, they are a solid bar — the shape stops reading as an
	 * outline exactly where it matters most.
	 *
	 * <p>It closes at the bottom, with the two walls stepping in by a pixel to meet. That single
	 * step is how pixel art of this size draws a curve — vanilla's own shield icon rounds its
	 * point the same way — and a closed contour is what keeps the eye reading a shape rather than
	 * two loose lines.
	 */
	private static final String[] GLYPH_FUNNEL = {
			"############",
			"#..........#",
			".#........#.",
			"..#......#..",
			"...#....#...",
			"....#..#....",
			"....#..#....",
			"....#..#....",
			"....#..#....",
			".....##....."};
	private static final int FUNNEL_X = 2;
	private static final int FUNNEL_Y = 3;

	/** How many quads vanilla hangs around a lit torch. */
	private static final int GLOW_QUADS = 6;

	/**
	 * Five pixels of clock: a gold rim, a pale dial and two hands. R is the rim, D the dial and H
	 * the hands, which is as much as five pixels across will hold and exactly what a clock is.
	 *
	 * <p>One dial, and one only: the mode is which way the whole clock faces, not what its hands
	 * are doing.
	 */
	private static final String[] GLYPH_CLOCK_DIAL = {
			".RRR.",
			"RDHDR",
			"RHHDR",
			"RDDDR",
			".RRR."};

	/**
	 * Where the clock stands, as the centre of its post. The output torch occupies x = 7..9, so
	 * this is its own width plus a pixel clear of it — a pixel that is there because at 4 the
	 * clock crowded the torch, and moving it out reads better from every angle but one.
	 */
	private static final double CLOCK_CX = 3.0;
	private static final double CLOCK_CZ = 3.0;

	/** Where each region starts, in a sheet five pixels tall. */
	private static final int CLOCK_DIAL_SIZE = 5;
	private static final int CLOCK_RIM_U = 5;
	private static final int CLOCK_POST_U = 10;

	private static final Map<Character, Integer> CLOCK_FACE_PALETTE = new LinkedHashMap<>();

	static {
		CLOCK_FACE_PALETTE.put('R', CLOCK_RIM);
		CLOCK_FACE_PALETTE.put('D', CLOCK_FACE);
		CLOCK_FACE_PALETTE.put('H', CLOCK_HANDS);
	}

	/** Every GUI sprite is 16 x 16, the size of a slot. */
	private static final int SPRITE_SIZE = 16;

	/** The folder the 64x pack is built into, which is also the id Fabric registers it under. */
	private static final String FAITHFUL_PACK = "faithful_64x";

	private static final int FAITHFUL_SCALE = 4;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@InputFile
	public abstract RegularFileProperty getMinecraftJar();

	/**
	 * An unpacked Faithful 64x resource pack, if the person building has one. Optional: without it
	 * the task simply produces no 64x pack, and the mod is built exactly as before.
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFaithfulPack();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@TaskAction
	public void generate() throws IOException {
		Path root = getOutputDirectory().get().getAsFile().toPath();

		// Everything under here was written by this task, so the run starts from nothing. Gradle
		// leaves a task's old output alone, and a file that used to be derived and is not any more
		// would otherwise sit in the directory and be packed into the jar for ever.
		wipe(root);

		try (ZipFile jar = new ZipFile(getMinecraftJar().get().getAsFile())) {
			derive(new JarSource(jar), new Target(root.resolve("assets/redstore"), 1, true));
			faithful(jar, root);
		}
	}

	/** Every asset of one output, at that output's own scale. */
	private void derive(Source source, Target target) throws IOException {
		Files.createDirectories(target.out());

		filterSlotIcon(target);
		filterHopper(source, target);
		redstoneClock(source, target);
		logicGates(source, target);
		chunkLoader(source, target);
	}

	/**
	 * The same edits again, on Faithful 64x, as a resource pack the player can turn on.
	 *
	 * <p>It is derived and never committed, for the same reason the vanilla art is — and here the
	 * licence says so outright. Faithful allows using and modifying their work in a mod, with
	 * credit and a link, but not "as a substitute for Minecraft's graphics when default textures
	 * otherwise wouldn't be allowed", which is exactly what shipping these files would be. So they
	 * are built on the player's machine from the player's own copy of the pack, or not at all.
	 *
	 * <p>Only textures. Models, block states and the rest are resolution-independent and already
	 * in the mod itself, so the pack overrides nothing but pixels.
	 */
	private void faithful(ZipFile jar, Path root) throws IOException {
		if (!getFaithfulPack().isPresent()) {
			return;
		}

		Path pack = getFaithfulPack().get().getAsFile().toPath();

		if (!Files.isDirectory(pack.resolve("assets/minecraft/textures"))) {
			throw new GradleException(pack + " does not look like an unpacked resource pack: "
					+ "assets/minecraft/textures is not in it.");
		}

		Path out = root.resolve("resourcepacks/" + FAITHFUL_PACK + "/assets/redstore");
		derive(new DirSource(pack), new Target(out, FAITHFUL_SCALE, false));
		packMetadata(jar, root.resolve("resourcepacks/" + FAITHFUL_PACK));
	}

	/**
	 * The pack's own `pack.mcmeta`, carrying the credit and the link Faithful's licence asks for.
	 *
	 * <p>Its format number is read from the game rather than written down, so it cannot go stale.
	 *
	 * <p>It is declared as a <b>range</b>, {@code min_format} to {@code max_format}, which is what
	 * vanilla's own packs do in 26.3 and what Faithful does. The older single {@code pack_format}
	 * is still parsed, but it pins the pack to one exact format: 26.3 serves resources at 97.1,
	 * a lone {@code 97} reads as 97.0, and the game marks the pack broken in the list while
	 * loading it anyway. A bare major at each end of a range covers every minor inside it.
	 */
	private void packMetadata(ZipFile jar, Path pack) throws IOException {
		JsonObject version = readJson(new JarSource(jar), "version.json");
		int format = version.getAsJsonObject("pack_version").get("resource_major").getAsInt();

		JsonObject meta = new JsonObject();
		JsonObject body = new JsonObject();
		body.addProperty("description", "Redstore's blocks, derived from Faithful 64x by HARYA_ "
				+ "and many others — https://faithfulpack.net");
		body.addProperty("min_format", format);
		body.addProperty("max_format", format);
		meta.add("pack", body);

		writeJson(pack, "pack.mcmeta", meta);
	}

	// ---------------------------------------------------------------- sources and targets

	/** Where the vanilla art is read from: the game's own jar, or an unpacked resource pack. */
	private interface Source {
		InputStream open(String path) throws IOException;
	}

	private record JarSource(ZipFile jar) implements Source {
		@Override
		public InputStream open(String path) throws IOException {
			ZipEntry entry = this.jar.getEntry(path);

			if (entry == null) {
				throw new GradleException(path + " is not in the Minecraft jar. "
						+ "Either the version changed or vanilla renamed it.");
			}

			return this.jar.getInputStream(entry);
		}
	}

	private record DirSource(Path root) implements Source {
		@Override
		public InputStream open(String path) throws IOException {
			Path file = this.root.resolve(path);

			if (!Files.isRegularFile(file)) {
				throw new GradleException(file + " is missing from the resource pack. "
						+ "Either the pack is incomplete or it renamed the file.");
			}

			return Files.newInputStream(file);
		}
	}

	/**
	 * One output. {@code scale} is how many pixels of this art make one pixel of vanilla's, so
	 * every coordinate written for 16 x 16 multiplies by it; {@code models} is false for a
	 * resource pack, which overrides textures and nothing else.
	 */
	private record Target(Path out, int scale, boolean models) {}

	// ---------------------------------------------------------------- blocks

	/**
	 * The placeholder the empty filter slot draws, in the same flat grey as vanilla's own.
	 *
	 * <p>Nothing is read from the jar here. It is drawn on an empty sprite, which is why the glyph
	 * is the whole of it.
	 */
	private void filterSlotIcon(Target target) throws IOException {
		int scale = target.scale();
		int size = SPRITE_SIZE * scale;
		BufferedImage icon = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		if (scale == 1) {
			stamp(icon, GLYPH_FUNNEL, FUNNEL_X, FUNNEL_Y, SLOT_ICON_GREY, 0, null, scale);
		} else {
			funnel(icon, size, scale / 2);
		}

		writePng(target.out(), "textures/gui/sprites/container/slot/filter.png", icon);
	}

	/**
	 * The same funnel, drawn instead of stamped, for a sprite that has the pixels to spare.
	 *
	 * <p>Scaling the 16 x 16 glyph would give a four-pixel stroke at 64 x 64, and that is not what
	 * a pack of that resolution does with a placeholder: Faithful's own slot icons are redrawn at
	 * a <b>two-pixel</b> stroke, with the diagonals stepping a pixel at a time instead of four. So
	 * this one is too — same shape, same proportions, finer line.
	 *
	 * <p>The shape is geometry rather than ASCII art because at this size it is geometry: a rim, a
	 * pair of 45° walls, a spout and a rounded bottom, all of them measured in units of the small
	 * design so the two cannot drift apart. The 16 x 16 version stays hand-placed, because at that
	 * size every pixel is a decision.
	 */
	private static void funnel(BufferedImage icon, int size, int stroke) {
		int unit = size / SPRITE_SIZE;
		int top = 3 * unit;
		int bottom = size - top;

		// The rim, then the walls closing in at 45° until they are the spout's width apart.
		fillRect(icon, 2 * unit, top, 12 * unit, stroke, SLOT_ICON_GREY);

		int left = 2 * unit;
		int right = size - 2 * unit - stroke;
		int y = top + stroke;

		for (; left < 6 * unit; left++, right--, y++) {
			fillRect(icon, left, y, stroke, 1, SLOT_ICON_GREY);
			fillRect(icon, right, y, stroke, 1, SLOT_ICON_GREY);
		}

		// The spout, and then the two walls stepping in to meet and close it.
		int curve = stroke + 1;
		int straight = bottom - y - curve - stroke;

		fillRect(icon, left, y, stroke, straight, SLOT_ICON_GREY);
		fillRect(icon, right, y, stroke, straight, SLOT_ICON_GREY);
		y += straight;

		for (int step = 0; step < curve; step++, left++, right--, y++) {
			fillRect(icon, left, y, stroke, 1, SLOT_ICON_GREY);
			fillRect(icon, right, y, stroke, 1, SLOT_ICON_GREY);
		}

		fillRect(icon, left, y, right - left + stroke, stroke, SLOT_ICON_GREY);
	}

	private static void wipe(Path root) throws IOException {
		if (!Files.isDirectory(root)) {
			return;
		}

		try (var walk = Files.walk(root)) {
			for (Path path : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
				Files.delete(path);
			}
		}
	}

	private static void fillRect(BufferedImage image, int x, int y, int width, int height, int colour) {
		for (int dy = 0; dy < height; dy++) {
			for (int dx = 0; dx < width; dx++) {
				image.setRGB(x + dx, y + dy, colour);
			}
		}
	}

	/**
	 * The little clock's own texture: three regions of one 16 x 16 sheet, each mapped face for
	 * face at the block's own density, so nothing is stretched.
	 *
	 * <pre>
	 *   (0,0)  5 x 5  the dial
	 *   (5,0)  5 x 5  the rim — what the clock looks like edge on
	 *   (10,0) 2 x 2  the post it stands on
	 * </pre>
	 *
	 * <p>Vanilla's own clock item was tried here first and does not survive being embedded: it is
	 * an inventory icon, drawn round with soft edges against nothing, and mounted on a block those
	 * edges read as a torn sticker. This is drawn instead — at 16 x 16 as pixels, above that as
	 * geometry, the same way every other mark in this task is.
	 */
	private void clockFace(Target target) throws IOException {
		int scale = target.scale();
		int size = SPRITE_SIZE * scale;
		BufferedImage sheet = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);

		// Edge on, a clock is a gold rim with a shadow round it.
		int rim = CLOCK_RIM_U * scale;
		int side = CLOCK_DIAL_SIZE * scale;
		fillRect(sheet, rim, 0, side, side, CLOCK_RIM);
		outline(sheet, rim, 0, side, side, scale, CLOCK_EDGE);

		// The post, in two shades so it has a lit side.
		fillRect(sheet, CLOCK_POST_U * scale, 0, 2 * scale, 2 * scale, CLOCK_EDGE);
		fillRect(sheet, CLOCK_POST_U * scale, 0, scale, 2 * scale, CLOCK_RIM);

		if (scale == 1) {
			stamp(sheet, GLYPH_CLOCK_DIAL, 0, 0, CLOCK_RIM, 0, CLOCK_FACE_PALETTE, 1);
		} else {
			dial(sheet, 0, scale);
		}

		writePng(target.out(), "textures/block/redstone_clock_face.png", sheet);
	}

	/**
	 * One dial, drawn round, with its two hands.
	 *
	 * <p>It fills the same footprint the five-pixel glyph does — the whole of it but the four
	 * corners — and that is not a detail. The disc's silhouette is two crossed boxes, so the model
	 * shows the middle three rows of this region and the middle three columns of it; anything left
	 * transparent inside those bands is a hole you can see the world through. A circle inscribed in
	 * the region leaves exactly such gaps at the bands' corners, which is what a rounder texture
	 * than the model bought the first time.
	 *
	 * <p>So the rim is the footprint, filled, and the face is a circle cut out of it. The rim comes
	 * out a shade thicker at the corners, which is what the model's own outline does too.
	 */
	private static void dial(BufferedImage sheet, int x, int scale) {
		int side = CLOCK_DIAL_SIZE * scale;
		double centre = side / 2.0;

		// Half a small pixel of rim and of hand, not a whole one. This is the whole reason for
		// drawing the dial rather than scaling the glyph: at four times the resolution a line can
		// be finer than the design it came from, which is what Faithful does with its own art.
		double line = scale / 2.0;

		fillRect(sheet, x, scale, side, side - 2 * scale, CLOCK_RIM);
		fillRect(sheet, x + scale, 0, side - 2 * scale, side, CLOCK_RIM);
		fillDisc(sheet, x + centre, centre, centre - line, CLOCK_FACE);

		// Up, and out to the left: the long hand and the short one.
		hand(sheet, x + centre, centre, centre * 0.7, -90, line, CLOCK_HANDS);
		hand(sheet, x + centre, centre, centre * 0.45, 180, line, CLOCK_HANDS);
	}

	/** A filled circle, for the dial the ring and the hands go on. */
	private static void fillDisc(BufferedImage image, double cx, double cy, double radius, int colour) {
		for (int y = (int) (cy - radius); y <= cy + radius; y++) {
			for (int x = (int) (cx - radius); x <= cx + radius; x++) {
				double dx = x + 0.5 - cx;
				double dy = y + 0.5 - cy;

				if (dx * dx + dy * dy <= radius * radius
						&& x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()) {
					image.setRGB(x, y, colour);
				}
			}
		}
	}

	/** A rectangle's border, drawn inside it. */
	private static void outline(BufferedImage image, int x, int y, int width, int height,
			int stroke, int colour) {
		fillRect(image, x, y, width, stroke, colour);
		fillRect(image, x, y + height - stroke, width, stroke, colour);
		fillRect(image, x, y, stroke, height, colour);
		fillRect(image, x + width - stroke, y, stroke, height, colour);
	}

	private void filterHopper(Source source, Target target) throws IOException {
		int s = target.scale();
		Path out = target.out();

		BufferedImage top = readPng(source, "assets/minecraft/textures/block/hopper_top.png");
		// The outer two-pixel ring of the top face becomes the frame.
		int edge = top.getWidth() - 1;
		recolour(top, BLOCK_WOOD, (x, y) -> Math.min(Math.min(x, y), Math.min(edge - x, edge - y)) < 2 * s);

		BufferedImage side = readPng(source, "assets/minecraft/textures/block/hopper_outside.png");
		// The collar spans y=11..16 in the model and its faces declare no UV, so they default to
		// v = 16 - y: rows 0..4 of this texture. Rows 0-1 are the top of it.
		recolour(side, BLOCK_WOOD, (x, y) -> y < 2 * s);

		BufferedImage icon = readPng(source, "assets/minecraft/textures/item/hopper.png");
		// The icon's rim, following its isometric outline: the top edge, then the corners down.
		// The ramp does the rest of the work — a pixel outside the hopper's greys is left alone —
		// so this only has to say how far down the rim reaches and how far in it wraps.
		recolour(icon, ITEM_WOOD, (x, y) -> y >= 2 * s && (y < 3 * s
				|| (y < 5 * s && (x < 4 * s || x >= 12 * s))));

		writePng(out, "textures/block/filter_hopper_top.png", top);
		writePng(out, "textures/block/filter_hopper_outside.png", side);
		writePng(out, "textures/item/filter_hopper.png", icon);

		if (!target.models()) {
			return;
		}

		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "redstore:block/filter_hopper_outside");
		textures.addProperty("top", "redstore:block/filter_hopper_top");
		textures.addProperty("side", "redstore:block/filter_hopper_outside");
		// The funnel's inner face has no rim to recolour, so it stays vanilla.
		textures.addProperty("inside", "minecraft:block/hopper_inside");

		for (String[] pair : new String[][]{{"hopper", "filter_hopper"}, {"hopper_side", "filter_hopper_side"}}) {
			JsonObject model = readJson(source, "assets/minecraft/models/block/" + pair[0] + ".json");
			model.add("textures", textures.deepCopy());
			writeJson(out, "models/block/" + pair[1] + ".json", model);
		}
	}

	private void redstoneClock(Source source, Target target) throws IOException {
		int s = target.scale();
		Path out = target.out();

		BufferedImage icon = readPng(source, "assets/minecraft/textures/item/repeater.png");

		if (s == 1) {
			stamp(icon, ICON_DIAL, ICON_DIAL_X, ICON_DIAL_Y, DIAL_GOLD, DIAL_DARK, null, s);
		} else {
			fineDial(icon, ICON_DIAL_CX * s, ICON_DIAL_CY * s, ICON_DIAL_R * s, s / 2.0,
					DIAL_GOLD, DIAL_DARK);
		}

		writePng(out, "textures/item/redstone_clock.png", icon);

		clockFace(target);

		if (!target.models()) {
			return;
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

				JsonObject model = readJson(source,
						"assets/minecraft/models/block/repeater_" + delay + "tick" + suffix + ".json");

				JsonArray elements = model.getAsJsonArray("elements");
				clockElements(!mode.isEmpty()).forEach(elements::add);

				String top = "minecraft:block/repeater" + (lit ? "_on" : "");
				JsonObject textures = new JsonObject();
				textures.addProperty("particle", top);
				textures.addProperty("slab", "minecraft:block/smooth_stone");
				textures.addProperty("top", top);
				textures.addProperty("clock", "redstore:block/redstone_clock_face");

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

	private void logicGates(Source source, Target target) throws IOException {
		int s = target.scale();
		Path out = target.out();

		for (Map.Entry<String, Integer> entry : GATE_METALS.entrySet()) {
			String gate = entry.getKey();
			int colour = entry.getValue();

			Map<Integer, Integer> inlay = inlay(colour);

			Map<Character, Integer> metal = new LinkedHashMap<>();
			metal.put('L', shade(colour, 1.1));
			metal.put('M', colour);

			BufferedImage plate = readPng(source, "assets/minecraft/textures/block/comparator.png");
			recolour(plate, inlay, (x, y) -> true);
			writePng(out, "textures/block/" + gate + "_top.png", plate);

			// One texture serves every model of this gate: nothing on the plate ever changes.
			// What changes when a signal arrives is which torches are lit, and a torch is
			// geometry, not paint.
			if (target.models()) {
				gateModels(source, out, gate, "redstore:block/" + gate + "_top");
			}

			BufferedImage icon = readPng(source, "assets/minecraft/textures/item/comparator.png");

			for (int[] pixel : ICON_LINE) {
				fill(icon, pixel[0] * s, pixel[1] * s, s, PLATE_GREY);
			}

			stamp(icon, ICON_STONE, ICON_STONE_X, ICON_STONE_Y, colour, 0, metal, s);
			writePng(out, "textures/item/" + gate + ".png", icon);
		}
	}

	/**
	 * The chunk loader's pedestal: the enchanting table, with its diamond corners recoloured to
	 * amethyst and its cloth dyed to the ender pearl's teal.
	 *
	 * <p>Nothing here is drawn — both edits are colour for colour, on vanilla's own texture and
	 * vanilla's own model.
	 *
	 * <p>The pearl is not here at all. It faces the camera, which a model baked into a chunk mesh
	 * cannot do, so {@code ChunkLoaderRenderer} draws it — the same division the enchanting table
	 * makes with its book, whose model is not in the table's model either.
	 */
	private void chunkLoader(Source source, Target target) throws IOException {
		Path out = target.out();

		Map<Integer, Integer> palette = new LinkedHashMap<>();

		for (int tone = 0; tone < TABLE_GEMS.length; tone++) {
			palette.put(TABLE_GEMS[tone], AMETHYST[tone]);
		}

		palette.putAll(dye(TABLE_CLOTH, PEARL_TEAL));

		for (String face : new String[]{"top", "side"}) {
			BufferedImage texture = readPng(source,
					"assets/minecraft/textures/block/enchanting_table_" + face + ".png");
			recolour(texture, palette, (x, y) -> true);
			writePng(out, "textures/block/chunk_loader_" + face + ".png", texture);
		}

		if (!target.models()) {
			return;
		}

		// The pedestal, and only the pedestal. The pearl is not in the model: it faces the camera,
		// which a baked model cannot do, so a block entity renderer draws it — exactly as the
		// enchanting table's book is not in the table's model either. See
		// spec/blocks/chunk-loader.md section 8.
		JsonObject model = readJson(source, "assets/minecraft/models/block/enchanting_table.json");

		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "minecraft:block/obsidian");
		textures.addProperty("bottom", "minecraft:block/enchanting_table_bottom");
		textures.addProperty("top", "redstore:block/chunk_loader_top");
		textures.addProperty("side", "redstore:block/chunk_loader_side");
		model.add("textures", textures);

		writeJson(out, "models/block/chunk_loader.json", model);
	}

	/**
	 * The clock: a five-pixel disc on a post, standing beside the output torch.
	 *
	 * <p>The disc is two boxes, a wide one crossed with a tall one, which is how pixel art draws a
	 * circle and how a model gets a round silhouette out of a renderer that only has boxes. It has
	 * its own depth — two pixels of it — rather than being a face on a case: a case is a box, and
	 * a box around a round thing is the part you notice.
	 *
	 * <p>It stands where the output torch stands, in the same two pixels of depth, one place to
	 * its left, on a post so that it is held up rather than lying against the stone. Its dial ends
	 * up level with the torch's head, which is what makes the two read as a pair of instruments
	 * rather than as a thing and some scenery.
	 *
	 * <p><b>The mode is which way the whole clock faces.</b> In square mode it looks along the
	 * wire, the way the signal leaves; in pulse mode the whole thing is turned a quarter and looks
	 * across it. From above — which is how a plate is read — the difference is the silhouette: a
	 * bar lying across the plate, or one lying along it. Nothing is added and nothing is taken
	 * away, so one object says both things.
	 */
	private static List<JsonObject> clockElements(boolean turned) {
		JsonObject post = new JsonObject();
		post.add("from", corner(CLOCK_CX - 1, 2, CLOCK_CZ - 1));
		post.add("to", corner(CLOCK_CX + 1, 3, CLOCK_CZ + 1));
		post.add("faces", postFaces());

		if (!turned) {
			return List.of(post,
					disc(CLOCK_CX - 2.5, 4, CLOCK_CZ - 1, CLOCK_CX + 2.5, 7, CLOCK_CZ + 1, false, 5, 3),
					disc(CLOCK_CX - 1.5, 3, CLOCK_CZ - 1, CLOCK_CX + 1.5, 8, CLOCK_CZ + 1, false, 3, 5));
		}

		// The same two boxes, turned a quarter about the post: the half-widths swap between the
		// axes, so the disc now looks along the block's other one and the dial moves to the faces
		// that axis presents.
		return List.of(post,
				disc(CLOCK_CX - 1, 4, CLOCK_CZ - 2.5, CLOCK_CX + 1, 7, CLOCK_CZ + 2.5, true, 5, 3),
				disc(CLOCK_CX - 1, 3, CLOCK_CZ - 1.5, CLOCK_CX + 1, 8, CLOCK_CZ + 1.5, true, 3, 5));
	}

	/**
	 * One box of the disc.
	 *
	 * <p>{@code across} and {@code high} are the box's face in texels, which is also how much of
	 * the dial it shows: the wide box carries the dial's middle three rows, the tall one its middle
	 * three columns, and between them the whole circle is covered exactly once.
	 */
	private static JsonObject disc(double x1, double y1, double z1, double x2, double y2, double z2,
			boolean turned, int across, int high) {
		int inset = (CLOCK_DIAL_SIZE - across) / 2;
		int u1 = inset;
		int u2 = CLOCK_DIAL_SIZE - inset;
		int v1 = (CLOCK_DIAL_SIZE - high) / 2;
		int v2 = CLOCK_DIAL_SIZE - v1;

		JsonObject faces = new JsonObject();
		JsonObject front = face("#clock", u1, v1, u2, v2);
		JsonObject back = face("#clock", u2, v1, u1, v2);
		JsonObject side = face("#clock", CLOCK_RIM_U, 0, CLOCK_RIM_U + 2, high);
		JsonObject flat = face("#clock", CLOCK_RIM_U, 0, CLOCK_RIM_U + across, 2);

		faces.add(turned ? "west" : "north", front);
		faces.add(turned ? "east" : "south", back);
		faces.add(turned ? "north" : "west", side);
		faces.add(turned ? "south" : "east", side.deepCopy());
		faces.add("up", flat);
		faces.add("down", flat.deepCopy());

		JsonObject element = new JsonObject();
		element.add("from", corner(x1, y1, z1));
		element.add("to", corner(x2, y2, z2));
		element.add("faces", faces);

		return element;
	}

	private static JsonObject postFaces() {
		JsonObject faces = new JsonObject();

		for (String side : new String[]{"north", "south", "west", "east"}) {
			faces.add(side, face("#clock", CLOCK_POST_U, 0, CLOCK_POST_U + 2, 1));
		}

		return faces;
	}

	/** Vanilla writes whole coordinates without a decimal point, so keep it that way. */
	private static JsonArray corner(double x, double y, double z) {
		JsonArray corner = new JsonArray();

		for (double value : new double[]{x, y, z}) {
			corner.add(value == Math.rint(value) ? (Number) (int) value : (Number) value);
		}

		return corner;
	}

	private static JsonObject face(String texture, double u1, double v1, double u2, double v2) {
		JsonArray uv = new JsonArray();

		for (double value : new double[]{u1, v1, u2, v2}) {
			uv.add(value == Math.rint(value) ? (Number) (int) value : (Number) value);
		}

		JsonObject face = new JsonObject();
		face.add("uv", uv);
		face.addProperty("texture", texture);

		return face;
	}

	/**
	 * The sixteen models of one gate face, composed out of vanilla's own comparator models.
	 *
	 * <p>Three torches light independently — the two flanks follow their inputs, the front follows
	 * the output — so a model is the plate plus a choice of lit or unlit for each. Both halves come
	 * from files vanilla already ships: {@code comparator.json} has the three unlit torches and
	 * {@code comparator_on_subtract.json} the three lit ones, each dragging the six glow quads a
	 * lit torch needs. Nothing here moves a coordinate.
	 *
	 * <p>Elements are taken by index, because glow quads cannot be told apart by geometry — two of
	 * them occupy the same box — and the index is then checked against the corner it should have.
	 * A reordering in a future vanilla model fails the build instead of quietly putting a torch in
	 * the wrong place.
	 */
	private void gateModels(Source source, Path out, String name, String top) throws IOException {
		JsonArray unlit = readJson(source, "assets/minecraft/models/block/comparator.json")
				.getAsJsonArray("elements");
		JsonArray lit = readJson(source, "assets/minecraft/models/block/comparator_on_subtract.json")
				.getAsJsonArray("elements");

		// comparator.json:              0 slab, 1 left flank, 2 right flank, 3 front
		// comparator_on_subtract.json:  0 slab, 1 front, 2 left flank, 3 right flank,
		//                               4-9 left glow, 10-15 right glow, 16-21 front glow
		// Left first, and left is +x: a model with no rotation is the gate facing south, whose
		// left flank — FACING.getCounterClockWise() — is east. The block state file rotates it.
		JsonElement slab = at(unlit, 0, 0, 0, 0);
		JsonElement[] off = {at(unlit, 2, 10, 2, 11), at(unlit, 1, 4, 2, 11), at(unlit, 3, 7, 2, 2)};
		JsonElement[] on = {at(lit, 3, 10, 2, 11), at(lit, 2, 4, 2, 11), at(lit, 1, 7, 2, 2)};
		int[] glow = {10, 4, 16};

		for (int mask = 0; mask < 8; mask++) {
			JsonArray elements = new JsonArray();
			elements.add(slab);

			for (int torch = 0; torch < 3; torch++) {
				if ((mask & (1 << torch)) == 0) {
					elements.add(off[torch]);
					continue;
				}

				elements.add(on[torch]);

				for (int quad = 0; quad < GLOW_QUADS; quad++) {
					elements.add(lit.get(glow[torch] + quad));
				}
			}

			JsonObject textures = new JsonObject();
			textures.addProperty("particle", top);
			textures.addProperty("slab", "minecraft:block/smooth_stone");
			textures.addProperty("top", top);
			textures.addProperty("unlit", "minecraft:block/redstone_torch_off");
			textures.addProperty("lit", "minecraft:block/redstone_torch");

			JsonObject model = new JsonObject();
			model.addProperty("ambientocclusion", false);
			model.add("textures", textures);
			model.add("elements", elements);

			writeJson(out, "models/block/" + name + gateSuffix(mask) + ".json", model);
		}
	}

	/** The element at that index, if its near corner is where it should be. */
	private static JsonElement at(JsonArray elements, int index, int x, int y, int z) {
		JsonArray from = elements.get(index).getAsJsonObject().getAsJsonArray("from");

		if (from.get(0).getAsInt() != x || from.get(1).getAsInt() != y || from.get(2).getAsInt() != z) {
			throw new GradleException("vanilla's comparator model has been reordered: element "
					+ index + " starts at " + from + ", not [" + x + ", " + y + ", " + z + "]");
		}

		return elements.get(index);
	}

	/** Bit 0 is the left flank, bit 1 the right, bit 2 the output — the block state's own order. */
	private static String gateSuffix(int mask) {
		return ((mask & 1) != 0 ? "_left" : "")
				+ ((mask & 2) != 0 ? "_right" : "")
				+ ((mask & 4) != 0 ? "_on" : "");
	}

	/**
	 * What to replace each of the quartz's four tones with: the metal's own colour, at the
	 * brightness the quartz has there.
	 *
	 * <p>Picking four tones out of the ingot by hand looks right in a table and wrong on the
	 * block. Quartz is drawn across 38 levels of brightness; iron's ingot spans 74, copper's 88
	 * and gold's 116, so the darkest tone — the one that draws the inlay's outline — falls far
	 * below the body and the outline stops being depth and becomes a drawn line. The comparator's
	 * own outline is barely visible, and that is the whole effect.
	 *
	 * <p>So the shading is the quartz's, kept exactly: each tone is the metal scaled by how bright
	 * that quartz tone is against their average. The hue is the ingot's, the contrast is vanilla's.
	 */
	private static Map<Integer, Integer> inlay(int metal) {
		double average = 0;

		for (int tone : QUARTZ) {
			average += luminance(tone);
		}

		average /= QUARTZ.length;

		Map<Integer, Integer> tones = new LinkedHashMap<>();

		for (int tone : QUARTZ) {
			tones.put(tone, shade(metal, luminance(tone) / average));
		}

		return tones;
	}

	/** Rec. 601 luminance, which is what the eye reads a tone's depth by. */
	private static double luminance(int colour) {
		return 0.299 * ((colour >> 16) & 0xFF)
				+ 0.587 * ((colour >> 8) & 0xFF)
				+ 0.114 * (colour & 0xFF);
	}

	/** The same colour, brighter or darker: every channel scaled, so the hue does not move. */
	/**
	 * One colour at somebody else's brightnesses: a dye, not a repaint.
	 *
	 * <p>The brightest of the given tones comes out as the colour itself and the rest keep their
	 * distance from it, so whatever the tones described — folds in cloth, the curve of a bead — is
	 * still described afterwards. {@link #inlay} does the same thing against the average rather
	 * than the brightest, because an inlay has to sit *inside* a plate and the plate's own
	 * brightness is the thing it must not escape.
	 */
	private static Map<Integer, Integer> dye(int[] tones, int colour) {
		double brightest = 0;

		for (int tone : tones) {
			brightest = Math.max(brightest, luminance(tone));
		}

		Map<Integer, Integer> dyed = new LinkedHashMap<>();

		for (int tone : tones) {
			dyed.put(tone, shade(colour, luminance(tone) / brightest));
		}

		return dyed;
	}

	private static int shade(int colour, double factor) {
		int r = channel(((colour >> 16) & 0xFF) * factor);
		int g = channel(((colour >> 8) & 0xFF) * factor);
		int b = channel((colour & 0xFF) * factor);

		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	private static int channel(double value) {
		return Math.min(255, Math.max(0, (int) Math.round(value)));
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

	/**
	 * Engrave an ASCII-art glyph onto a texture. '.' is always left alone.
	 *
	 * <p>The glyph is written once, for a 16 x 16 texture, and drawn at {@code scale} pixels per
	 * glyph pixel. That is dimensionally right rather than merely convenient: a block occupies the
	 * same space in the world whatever its texture's resolution, so a mark four pixels wide on a
	 * 64 x 64 plate is exactly as big as a one-pixel mark on a 16 x 16 one. What a scaled glyph
	 * does not gain is detail — the mod's own marks stay as coarse as they were drawn, next to the
	 * finer art around them.
	 */
	private static void stamp(BufferedImage image, String[] glyph, int x0, int y0,
			int colour, int accent, Map<Character, Integer> palette, int scale) {
		for (int dy = 0; dy < glyph.length; dy++) {
			String line = glyph[dy];

			for (int dx = 0; dx < line.length(); dx++) {
				char c = line.charAt(dx);
				Integer ink = null;

				if (c == '#') {
					ink = colour;
				} else if (c == '+' && accent != 0) {
					ink = accent;
				} else if (palette != null && palette.containsKey(c)) {
					ink = palette.get(c);
				}

				if (ink != null) {
					fill(image, (x0 + dx) * scale, (y0 + dy) * scale, scale, ink);
				}
			}
		}
	}

	/**
	 * The marks the mod draws itself, at a resolution fine enough to draw them properly.
	 *
	 * <p>A glyph scaled up keeps its shape and gains nothing: at 64 x 64 a ring authored on a
	 * 4 x 3 grid is a ring of four-pixel blocks, which next to Faithful's own curves reads as the
	 * only thing on the block that did not get redrawn. These do the same shapes in the space the
	 * bigger texture actually has — circles that are round, and engravings with an edge.
	 *
	 * <p>Everything is measured in units of the 16 x 16 design, so the two forms cannot drift: the
	 * fine ring is centred where the small one is centred, and covers the same pixels of the block.
	 */
	private static void fineRing(BufferedImage image, double cx, double cy, double radius,
			double stroke, int colour) {
		int from = (int) Math.floor(cx - radius - stroke);
		int to = (int) Math.ceil(cx + radius + stroke);

		for (int y = (int) Math.floor(cy - radius - stroke); y <= (int) Math.ceil(cy + radius + stroke); y++) {
			for (int x = from; x <= to; x++) {
				if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
					continue;
				}

				double dx = x + 0.5 - cx;
				double dy = y + 0.5 - cy;

				if (Math.abs(Math.sqrt(dx * dx + dy * dy) - radius) <= stroke / 2.0) {
					image.setRGB(x, y, colour);
				}
			}
		}
	}

	/** A clock face: a ring, and two hands at the angles a clock's are drawn at. */
	private static void fineDial(BufferedImage image, double cx, double cy, double radius,
			double stroke, int gold, int dark) {
		fineRing(image, cx, cy, radius, stroke, gold);

		// Up for the long hand, and out to the right for the short one, as on the vanilla clock.
		hand(image, cx, cy, radius * 0.72, -90, stroke, dark);
		hand(image, cx, cy, radius * 0.48, -20, stroke, dark);
	}

	/**
	 * A hand, drawn from the middle outwards.
	 *
	 * <p>The stroke is centred on the line rather than hung off it: a hand two pixels thick drawn
	 * from the centre of a dial sits a pixel to the right and a pixel below where it should, and on
	 * a five-pixel face that is the difference between a clock and a smudge.
	 */
	private static void hand(BufferedImage image, double cx, double cy, double length,
			double degrees, double stroke, int colour) {
		double radians = Math.toRadians(degrees);
		int thickness = Math.max(1, (int) Math.round(stroke));
		double offset = thickness / 2.0;

		for (double along = 0; along <= length; along += 0.25) {
			double x = cx + Math.cos(radians) * along - offset;
			double y = cy + Math.sin(radians) * along - offset;

			for (int dy = 0; dy < thickness; dy++) {
				for (int dx = 0; dx < thickness; dx++) {
					int px = (int) Math.round(x) + dx;
					int py = (int) Math.round(y) + dy;

					if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
						image.setRGB(px, py, colour);
					}
				}
			}
		}
	}

	/** One pixel of a 16 x 16 design, which at this scale is a square of them. */	/** One pixel of a 16 x 16 design, which at this scale is a square of them. */
	private static void fill(BufferedImage image, int x, int y, int scale, int colour) {
		for (int dy = 0; dy < scale; dy++) {
			for (int dx = 0; dx < scale; dx++) {
				image.setRGB(x + dx, y + dy, colour);
			}
		}
	}

	// ---------------------------------------------------------------- io

	private static BufferedImage readPng(Source source, String path) throws IOException {
		try (InputStream in = source.open(path)) {
			BufferedImage png = ImageIO.read(in);
			// Palette images come back indexed; copy into ARGB so every pixel is writable.
			BufferedImage copy = new BufferedImage(png.getWidth(), png.getHeight(),
					BufferedImage.TYPE_INT_ARGB);
			copy.getGraphics().drawImage(png, 0, 0, null);

			return copy;
		}
	}

	private static JsonObject readJson(Source source, String path) throws IOException {
		try (InputStream in = source.open(path)) {
			return GSON.fromJson(new java.io.InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		}
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
