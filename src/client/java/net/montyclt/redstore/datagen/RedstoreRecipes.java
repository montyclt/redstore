package net.montyclt.redstore.datagen;

import java.util.concurrent.CompletableFuture;

import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;

import net.montyclt.redstore.registry.RedstoreBlocks;

/**
 * Every recipe, and with each one the advancement that unlocks it.
 *
 * <p>That pairing is the reason this provider exists. The advancements were written by hand once,
 * forgotten, and the gates were craftable but invisible in the recipe book until someone noticed;
 * here they cannot be forgotten, because the builder emits them.
 *
 * <p>Each recipe is a parts list of what the block shows — see
 * spec/blocks/abstract-redstone-plate.md section 10 — so the shapes below are not arbitrary and
 * changing one means changing a texture too.
 */
public class RedstoreRecipes extends FabricRecipeProvider {
	public RedstoreRecipes(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries,
			BootstrapContext<Recipe<?>> recipes, BootstrapContext<Advancement> advancements) {
		return new RecipeProvider(recipes, advancements) {
			@Override
			public void buildRecipes() {
				gate(RedstoreBlocks.AND_GATE, Items.IRON_INGOT);
				gate(RedstoreBlocks.OR_GATE, Items.COPPER_INGOT);
				gate(RedstoreBlocks.XOR_GATE, Items.GOLD_INGOT);

				// The repeater's recipe with a clock under it, which is what the block is.
				this.shaped(RecipeCategory.REDSTONE, RedstoreBlocks.REDSTONE_CLOCK)
						.pattern("TRT")
						.pattern(" K ")
						.pattern("SSS")
						.define('T', Items.REDSTONE_TORCH)
						.define('R', Items.REDSTONE)
						.define('K', Items.CLOCK)
						.define('S', Blocks.SMOOTH_STONE)
						.unlockedBy("has_clock", this.has(Items.CLOCK))
						.save(this.output);

				// The enchanting table's recipe, part for part: a pearl where the book goes,
				// amethyst where the diamonds go. See spec/blocks/chunk-loader.md section 7.
				this.shaped(RecipeCategory.REDSTONE, RedstoreBlocks.CHUNK_LOADER)
						.pattern(" P ")
						.pattern("AOA")
						.pattern("OOO")
						.define('P', Items.ENDER_PEARL)
						.define('A', Items.AMETHYST_SHARD)
						.define('O', Blocks.OBSIDIAN)
						.unlockedBy(getHasName(Items.ENDER_PEARL), this.has(Items.ENDER_PEARL))
						.save(this.output);

				// A hopper and the item frame that goes on its mouth: the contraption, in two
				// items, in any order.
				this.shapeless(RecipeCategory.REDSTONE, RedstoreBlocks.FILTER_HOPPER)
						.requires(Items.HOPPER)
						.requires(Items.ITEM_FRAME)
						.group("redstore_filter_hopper")
						.unlockedBy("has_hopper", this.has(Items.HOPPER))
						.save(this.output);
			}

			/** The comparator's recipe with the gate's own metal where the quartz goes. */
			private void gate(ItemLike gate, ItemLike metal) {
				this.shaped(RecipeCategory.REDSTONE, gate)
						.pattern(" T ")
						.pattern("TMT")
						.pattern("SSS")
						.define('T', Items.REDSTONE_TORCH)
						.define('M', metal)
						.define('S', Blocks.SMOOTH_STONE)
						.unlockedBy(getHasName(metal), this.has(metal))
						.save(this.output);
			}
		};
	}

	@Override
	public String getName() {
		return "Redstore recipes";
	}
}
