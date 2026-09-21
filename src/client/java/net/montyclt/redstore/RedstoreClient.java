package net.montyclt.redstore;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.network.chat.Component;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.pack.PackActivationType;
import net.fabricmc.loader.api.FabricLoader;

import net.montyclt.redstore.client.render.ChunkLoaderRenderer;
import net.montyclt.redstore.client.render.FilterHopperRenderer;
import net.montyclt.redstore.client.screen.FilterHopperScreen;
import net.montyclt.redstore.registry.RedstoreBlockEntities;
import net.montyclt.redstore.registry.RedstoreMenus;

public class RedstoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(RedstoreMenus.FILTER_HOPPER, FilterHopperScreen::new);
		BlockEntityRenderers.register(RedstoreBlockEntities.FILTER_HOPPER, FilterHopperRenderer::new);
		BlockEntityRenderers.register(RedstoreBlockEntities.CHUNK_LOADER, ChunkLoaderRenderer::new);
		registerFaithfulPack();
	}

	/**
	 * Offers the 64x pack in the resource pack screen, if this copy of the mod was built with one.
	 *
	 * <p>It is only there when the person who built the mod had Faithful 64x on their machine, so
	 * the call is allowed to find nothing and say so by returning false. The player turns it on
	 * themselves: switching a resource pack on because another one is enabled would mean guessing
	 * at pack names, which change between versions and between Faithful's own resolutions.
	 */
	private static void registerFaithfulPack() {
		FabricLoader.getInstance().getModContainer(Redstore.MOD_ID).ifPresent(mod ->
				ResourceLoader.registerBuiltinPack(
						Redstore.id("faithful_64x"),
						mod,
						Component.literal("Redstore for Faithful 64x"),
						PackActivationType.NORMAL));
	}
}
