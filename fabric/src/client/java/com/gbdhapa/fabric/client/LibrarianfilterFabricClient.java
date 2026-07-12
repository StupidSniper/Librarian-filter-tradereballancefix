package com.gbdhapa.fabric.client;

import com.gbdhapa.config.TradeConfig;
import com.gbdhapa.fabric.client.gui.FabricTradeConfigScreen;
import com.gbdhapa.network.OpenConfigScreenPayload;
import com.gbdhapa.network.TradeConfigSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class LibrarianfilterFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientInit.init();
        
        ClientPlayNetworking.registerGlobalReceiver(TradeConfigSyncPayload.ID, (payload, context) -> {
            TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
            TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
            TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
            TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
        });

        ClientPlayNetworking.registerGlobalReceiver(OpenConfigScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreenAndShow(new FabricTradeConfigScreen(payload.enableReroll(), payload.enableEachLevelReroll(), payload.disableTradeRebalance(), payload.enableSignSuggestions()));
            });
        });

        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (handler.enabledFeatures().contains(net.minecraft.world.flag.FeatureFlags.TRADE_REBALANCE)) {
                client.execute(() -> {
                    if (client.player != null) {
                        var msg = net.minecraft.network.chat.Component.literal("§e[Librarian Filter] Warning: Villager Trade Rebalance experimental feature is enabled in this world! Villager book trades are biome-dependent. ")
                            .append(net.minecraft.network.chat.Component.literal("[Click here to disable]")
                                .withStyle(style -> style
                                    .withColor(net.minecraft.ChatFormatting.RED)
                                    .withUnderlined(true)
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/reroll config toggle disableTradeRebalance"))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, net.minecraft.network.chat.Component.literal("Click to disable Trade Rebalance")))
                                )
                            );
                        client.player.sendSystemMessage(msg);
                    }
                });
            }
        });
    }
}
