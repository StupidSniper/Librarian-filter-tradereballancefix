package com.gbdhapa.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class TradeConfig {
    private static final File CONFIG_FILE = new File("config/librarian-filter.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static TradeConfig INSTANCE = new TradeConfig();

    public boolean enableReroll = true;
    public boolean enableEachLevelReroll = false;
    public boolean disableTradeRebalance = false;
    public boolean enableSignSuggestions = true;

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, TradeConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        CONFIG_FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void applyTradeRebalanceOverride(net.minecraft.server.MinecraftServer server) {
        if (server != null) {
            try {
                var packRepo = server.getPackRepository();
                java.util.List<String> activePacks = new java.util.ArrayList<>(packRepo.getSelectedIds());
                
                boolean hasRebalance = activePacks.contains("trade_rebalance") || activePacks.contains("minecraft:trade_rebalance");
                boolean shouldDisable = INSTANCE.disableTradeRebalance;
                
                boolean changed = false;
                if (shouldDisable && hasRebalance) {
                    activePacks.remove("trade_rebalance");
                    activePacks.remove("minecraft:trade_rebalance");
                    changed = true;
                } else if (!shouldDisable && !hasRebalance) {
                    if (packRepo.isAvailable("trade_rebalance")) {
                        activePacks.add("trade_rebalance");
                        changed = true;
                    } else if (packRepo.isAvailable("minecraft:trade_rebalance")) {
                        activePacks.add("minecraft:trade_rebalance");
                        changed = true;
                    }
                }

                if (changed) {
                    server.reloadResources(activePacks);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
