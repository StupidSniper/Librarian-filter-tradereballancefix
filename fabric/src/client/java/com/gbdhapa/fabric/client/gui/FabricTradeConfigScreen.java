package com.gbdhapa.fabric.client.gui;

import com.gbdhapa.network.TradeConfigUpdatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class FabricTradeConfigScreen extends Screen {
    private boolean enableReroll;
    private boolean enableEachLevelReroll;
    private boolean disableTradeRebalance;

    public FabricTradeConfigScreen(boolean enableReroll, boolean enableEachLevelReroll, boolean disableTradeRebalance) {
        super(Component.literal("Trade Reroll Configuration"));
        this.enableReroll = enableReroll;
        this.enableEachLevelReroll = enableEachLevelReroll;
        this.disableTradeRebalance = disableTradeRebalance;
    }

    @Override
    protected void init() {
        super.init();
        
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startX = (this.width - buttonWidth) / 2;
        int startY = this.height / 4;

        this.addRenderableWidget(Button.builder(Component.literal("Enable Reroll: " + (enableReroll ? "ON" : "OFF")), button -> {
            this.enableReroll = !this.enableReroll;
            button.setMessage(Component.literal("Enable Reroll: " + (enableReroll ? "ON" : "OFF")));
        }).bounds(startX, startY, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Enable Each Level Reroll: " + (enableEachLevelReroll ? "ON" : "OFF")), button -> {
            this.enableEachLevelReroll = !this.enableEachLevelReroll;
            button.setMessage(Component.literal("Enable Each Level Reroll: " + (enableEachLevelReroll ? "ON" : "OFF")));
        }).bounds(startX, startY + 30, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Disable Trade Rebalance: " + (disableTradeRebalance ? "ON" : "OFF")), button -> {
            this.disableTradeRebalance = !this.disableTradeRebalance;
            button.setMessage(Component.literal("Disable Trade Rebalance: " + (disableTradeRebalance ? "ON" : "OFF")));
        }).bounds(startX, startY + 60, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            ClientPlayNetworking.send(new TradeConfigUpdatePayload(this.enableReroll, this.enableEachLevelReroll, this.disableTradeRebalance));
            this.onClose();
        }).bounds(startX, startY + 100, buttonWidth, buttonHeight).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.centeredText(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }
}
