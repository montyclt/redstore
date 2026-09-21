package net.montyclt.redstore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import net.montyclt.redstore.chunkloading.ChunkLoaderManager;
import net.montyclt.redstore.registry.RedstoreBlockEntities;
import net.montyclt.redstore.registry.RedstoreBlocks;
import net.montyclt.redstore.registry.RedstoreMenus;

public class Redstore implements ModInitializer {
	public static final String MOD_ID = "redstore";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		RedstoreBlocks.initialize();
		RedstoreBlockEntities.initialize();
		RedstoreMenus.initialize();
		ChunkLoaderManager.initialize();
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
