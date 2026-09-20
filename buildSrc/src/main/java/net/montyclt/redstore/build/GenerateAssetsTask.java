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
 * <p>This is the machinery on its own: reading a file out of the jar, recolouring or engraving it,
 * and writing the result. Each block adds its own derivation to it.
 *
 * <p>See spec/conventions.md section 10.
 */
public abstract class GenerateAssetsTask extends DefaultTask {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	@InputFile
	public abstract RegularFileProperty getMinecraftJar();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@TaskAction
	public void generate() throws IOException {
		Path out = getOutputDirectory().get().getAsFile().toPath().resolve("assets/redstore");
		Files.createDirectories(out);
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
