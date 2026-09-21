package net.montyclt.redstore.datagen;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

/**
 * Writes the mod's data files into {@code src/main/generated}, with {@code ./gradlew runDatagen}.
 *
 * <p>What it owns: recipes and the unlock advancements that come with them, loot tables, tags, and
 * block state files. What it does not: the language files, which people translate and which no
 * generator should stand between them and — see spec/conventions.md section 11.
 *
 * <p>It lives in the client source set because block state generation is client-side code in 26.3,
 * and the datagen run is configured from the client run for the same reason.
 */
public class RedstoreDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator generator) {
		FabricDataGenerator.Pack pack = generator.createPack();

		pack.addProvider(RedstoreRecipes::new);
		pack.addProvider(RedstoreLootTables::new);
		pack.addProvider(RedstoreBlockTags::new);
		pack.addProvider(RedstoreModels::new);
	}
}
