package com.gbdhapa.forge.client;

import com.gbdhapa.network.ConfigRequestPayload;
import com.gbdhapa.network.OpenConfigScreenPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.tick.PlayerTickEvent;
import net.minecraftforge.network.event.RegisterPayloadHandlersEvent;

public class LibrarianfilterForgeClient {
    public static KeyMapping OPEN_CONFIG_KEY;

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(LibrarianfilterForgeClient::registerKeyMappings);
        modEventBus.addListener(LibrarianfilterForgeClient::registerClientPayloads);
        MinecraftForge.EVENT_BUS.addListener(LibrarianfilterForgeClient::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(LibrarianfilterForgeClient::onClientLogin);
    }

    private static void onClientLogin(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        if (event.getPlayer() != null && event.getPlayer().connection.getConnection().isMemoryConnection() || event.getPlayer() != null && event.getPlayer().level().enabledFeatures().contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE)) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§e[Librarian Filter] Warning: Villager Trade Rebalance experimental feature is enabled in this world! Villager book trades are biome-dependent."
                    ));
                }
            });
        }
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        OPEN_CONFIG_KEY = new KeyMapping(
                "key.librarian-filter.open_trade_config",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_O,
                new Category(Identifier.fromNamespaceAndPath("minecraft", "gameplay"))
        );
        event.register(OPEN_CONFIG_KEY);
    }

    public static void registerClientPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1.0.1").playToClient(OpenConfigScreenPayload.ID, OpenConfigScreenPayload.CODEC, (payload, context) -> {
            context.enqueueWork(() -> {
                Minecraft.getInstance().setScreenAndShow(new ForgeTradeConfigScreen(payload.enableReroll(), payload.enableEachLevelReroll(), payload.disableTradeRebalance(), payload.enableSignSuggestions()));
            });
        });
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) {
            if (OPEN_CONFIG_KEY != null) {
                while (OPEN_CONFIG_KEY.consumeClick()) {
                    ClientPacketDistributor.sendToServer(new ConfigRequestPayload());
                }
            }
        }
    }
}

