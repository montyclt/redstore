package net.montyclt.redstore;

import net.minecraft.client.gui.screens.MenuScreens;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import net.fabricmc.api.ClientModInitializer;

import net.montyclt.redstore.client.render.FilterHopperRenderer;
import net.montyclt.redstore.client.screen.FilterHopperScreen;
import net.montyclt.redstore.registry.RedstoreBlockEntities;
import net.montyclt.redstore.registry.RedstoreMenus;

public class RedstoreClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(RedstoreMenus.FILTER_HOPPER, FilterHopperScreen::new);
		BlockEntityRenderers.register(RedstoreBlockEntities.FILTER_HOPPER, FilterHopperRenderer::new);
	}
}
