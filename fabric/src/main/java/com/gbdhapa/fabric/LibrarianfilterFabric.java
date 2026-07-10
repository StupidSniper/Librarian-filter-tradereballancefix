package com.gbdhapa.fabric;

import com.gbdhapa.RerollLogic;
import com.gbdhapa.config.TradeConfig;
import com.gbdhapa.network.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

public class LibrarianfilterFabric implements ModInitializer {
    public static final String MOD_ID = "librarian-filter";

    @Override
    public void onInitialize() {
        TradeConfig.load();

        // Register Payloads
        PayloadTypeRegistry.clientboundPlay().register(TradeConfigSyncPayload.ID, TradeConfigSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(OpenConfigScreenPayload.ID, OpenConfigScreenPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(TradeConfigUpdatePayload.ID, TradeConfigUpdatePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ConfigRequestPayload.ID, ConfigRequestPayload.CODEC);

        // Register Receivers
        ServerPlayNetworking.registerGlobalReceiver(TradeConfigUpdatePayload.ID, (payload, context) -> {
            var server = context.player().level().getServer();
            if (server.getPlayerList().isOp(new NameAndId(context.player().getGameProfile()))) {
                TradeConfig.INSTANCE.enableReroll = payload.enableReroll();
                TradeConfig.INSTANCE.enableEachLevelReroll = payload.enableEachLevelReroll();
                TradeConfig.INSTANCE.disableTradeRebalance = payload.disableTradeRebalance();
                TradeConfig.INSTANCE.enableSignSuggestions = payload.enableSignSuggestions();
                TradeConfig.save();

                TradeConfig.applyTradeRebalanceOverride(server);

                TradeConfigSyncPayload syncPayload = new TradeConfigSyncPayload(payload.enableReroll(), payload.enableEachLevelReroll(), payload.disableTradeRebalance(), payload.enableSignSuggestions());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(player, syncPayload);
                }
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ConfigRequestPayload.ID, (payload, context) -> {
            var server = context.player().level().getServer();
            if (server.getPlayerList().isOp(new NameAndId(context.player().getGameProfile()))) {
                ServerPlayNetworking.send(context.player(), new OpenConfigScreenPayload(
                        TradeConfig.INSTANCE.enableReroll,
                        TradeConfig.INSTANCE.enableEachLevelReroll,
                        TradeConfig.INSTANCE.disableTradeRebalance,
                        TradeConfig.INSTANCE.enableSignSuggestions
                ));
            }
        });

        // Register Events
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            return RerollLogic.handleBlockUse(player, world, hitResult.getBlockPos());
        });

        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TradeConfig.applyTradeRebalanceOverride(server);
        });

        // Register Commands
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("reroll")
                    .requires(source -> {
                        try {
                            return source.getServer().getPlayerList().isOp(new NameAndId(source.getPlayerOrException().getGameProfile()));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .then(Commands.literal("config")
                            .executes(context -> {
                                ServerPlayer player = context.getSource().getPlayerOrException();
                                ServerPlayNetworking.send(player, new OpenConfigScreenPayload(
                                        TradeConfig.INSTANCE.enableReroll,
                                        TradeConfig.INSTANCE.enableEachLevelReroll,
                                        TradeConfig.INSTANCE.disableTradeRebalance,
                                        TradeConfig.INSTANCE.enableSignSuggestions
                                ));
                                return 1;
                            })
                    )
                    .then(Commands.literal("find")
                            .then(Commands.argument("query", com.mojang.brigadier.arguments.StringArgumentType.word())
                                    .suggests((context, builder) -> {
                                        net.minecraft.commands.CommandSourceStack source = context.getSource();
                                        try {
                                            var registry = source.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                                            java.util.List<String> paths = registry.listElementIds().toList().stream()
                                                    .map(key -> key.identifier().getPath())
                                                    .filter(path -> !path.equals("soul_speed") && !path.equals("swift_sneak") && !path.equals("wind_burst"))
                                                    .toList();
                                            return net.minecraft.commands.SharedSuggestionProvider.suggest(paths, builder);
                                        } catch (Exception e) {
                                            java.util.List<String> paths = com.gbdhapa.EnchantmentDescriptions.DESCRIPTIONS.keySet().stream()
                                                    .filter(path -> !path.equals("soul_speed") && !path.equals("swift_sneak") && !path.equals("wind_burst"))
                                                    .toList();
                                            return net.minecraft.commands.SharedSuggestionProvider.suggest(paths, builder);
                                        }
                                    })
                                    .executes(context -> {
                                        String query = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "query");
                                        try {
                                            return RerollLogic.executeFind(context.getSource(), query);
                                        } catch (Exception e) {
                                            return 0;
                                        }
                                    })
                            )
                    )
            );
        });
    }
}
