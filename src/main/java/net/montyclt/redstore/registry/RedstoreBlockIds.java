package net.montyclt.redstore.registry;

import net.minecraft.references.BlockItemId;
import net.minecraft.resources.Identifier;

import net.montyclt.redstore.Redstore;

/** Identifiers for every block that also has an item. */
public final class RedstoreBlockIds {
	public static final BlockItemId AND_GATE = create("and_gate");
	public static final BlockItemId OR_GATE = create("or_gate");
	public static final BlockItemId XOR_GATE = create("xor_gate");
	public static final BlockItemId REDSTONE_CLOCK = create("redstone_clock");
	public static final BlockItemId FILTER_HOPPER = create("filter_hopper");
	public static final BlockItemId CHUNK_LOADER = create("chunk_loader");

	private static BlockItemId create(String name) {
		Identifier id = Redstore.id(name);
		return BlockItemId.create(id, id);
	}

	private RedstoreBlockIds() {
	}
}
