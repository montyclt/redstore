package net.montyclt.redstore.registry;

import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;

import net.montyclt.redstore.Redstore;

/** Identifiers for every block that also has an item. */
public final class RedstoreBlockIds {
	public static final BlockItemId FILTER_HOPPER = create("filter_hopper");

	private static BlockItemId create(String name) {
		Identifier id = Redstore.id(name);
		return BlockItemId.create(id, id);
	}

	private RedstoreBlockIds() {
	}
}
