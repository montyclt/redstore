package net.montyclt.redstore.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.montyclt.redstore.Redstore;
import net.montyclt.redstore.blockentity.FilterHopperBlockEntity;

public final class RedstoreBlockEntities {
	public static final BlockEntityType<FilterHopperBlockEntity> FILTER_HOPPER =
			register("filter_hopper", FilterHopperBlockEntity::new, RedstoreBlocks.FILTER_HOPPER);

	public static void initialize() {
	}

	private static <T extends BlockEntity> BlockEntityType<T> register(
			String name,
			FabricBlockEntityTypeBuilder.Factory<? extends T> factory,
			Block... blocks
	) {
		Identifier id = Redstore.id(name);
		return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, FabricBlockEntityTypeBuilder.<T>create(factory, blocks).build());
	}

	private RedstoreBlockEntities() {
	}
}
