package com.gbdhapa.fabric.mixin.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(AbstractSignEditScreen.class)
public abstract class AbstractSignEditScreenMixin extends Screen {

    @Shadow @Final private String[] messages;
    @Shadow private int line;
    @Shadow private TextFieldHelper signField;

    @Unique private List<EnchantmentInfo> suggestions = new ArrayList<>();
    @Unique private int selectedSuggestionIndex = 0;
    @Unique private boolean suggestionsVisible = false;

    @Unique
    private static class EnchantmentInfo {
        final String path;
        final int maxLevel;

        EnchantmentInfo(String path, int maxLevel) {
            this.path = path;
            this.maxLevel = maxLevel;
        }
    }

    @Unique
    private static final List<EnchantmentInfo> ALL_ENCHANTMENTS = List.of(
        new EnchantmentInfo("aqua_affinity", 1),
        new EnchantmentInfo("bane_of_arthropods", 5),
        new EnchantmentInfo("blast_protection", 4),
        new EnchantmentInfo("breach", 4),
        new EnchantmentInfo("channeling", 1),
        new EnchantmentInfo("curse_of_binding", 1),
        new EnchantmentInfo("curse_of_vanishing", 1),
        new EnchantmentInfo("depth_strider", 3),
        new EnchantmentInfo("density", 5),
        new EnchantmentInfo("efficiency", 5),
        new EnchantmentInfo("feather_falling", 4),
        new EnchantmentInfo("fire_aspect", 2),
        new EnchantmentInfo("fire_protection", 4),
        new EnchantmentInfo("flame", 1),
        new EnchantmentInfo("fortune", 3),
        new EnchantmentInfo("frost_walker", 2),
        new EnchantmentInfo("impaling", 5),
        new EnchantmentInfo("infinity", 1),
        new EnchantmentInfo("knockback", 2),
        new EnchantmentInfo("looting", 3),
        new EnchantmentInfo("loyalty", 3),
        new EnchantmentInfo("luck_of_the_sea", 3),
        new EnchantmentInfo("lure", 3),
        new EnchantmentInfo("mending", 1),
        new EnchantmentInfo("multishot", 1),
        new EnchantmentInfo("piercing", 4),
        new EnchantmentInfo("power", 5),
        new EnchantmentInfo("projectile_protection", 4),
        new EnchantmentInfo("protection", 4),
        new EnchantmentInfo("punch", 2),
        new EnchantmentInfo("quick_charge", 3),
        new EnchantmentInfo("respiration", 3),
        new EnchantmentInfo("riptide", 3),
        new EnchantmentInfo("sharpness", 5),
        new EnchantmentInfo("silk_touch", 1),
        new EnchantmentInfo("smite", 5),
        new EnchantmentInfo("sweeping_edge", 3),
        new EnchantmentInfo("thorns", 3),
        new EnchantmentInfo("unbreaking", 3)
    );

    protected AbstractSignEditScreenMixin(Component title) {
        super(title);
    }

    @Unique
    private String getRoman(int level) {
        switch (level) {
            case 1: return "I";
            case 2: return "II";
            case 3: return "III";
            case 4: return "IV";
            case 5: return "V";
            case 6: return "VI";
            case 7: return "VII";
            case 8: return "VIII";
            case 9: return "IX";
            case 10: return "X";
            default: return String.valueOf(level);
        }
    }

    @Unique
    private List<EnchantmentInfo> getEnchantments() {
        List<EnchantmentInfo> list = new ArrayList<>();
        try {
            var client = net.minecraft.client.Minecraft.getInstance();
            if (client.level != null) {
                var registry = client.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                for (var key : registry.listElementIds().toList()) {
                    String path = key.identifier().getPath();
                    if (path.equals("soul_speed") || path.equals("swift_sneak") || path.equals("wind_burst")) {
                        continue;
                    }
                    int maxLevel = 1;
                    var holderOpt = registry.get(key);
                    if (holderOpt.isPresent()) {
                        maxLevel = holderOpt.get().value().getMaxLevel();
                    }
                    list.add(new EnchantmentInfo(path, maxLevel));
                }
            }
        } catch (Exception e) {
            // fallback
        }
        if (list.isEmpty()) {
            return ALL_ENCHANTMENTS;
        }
        return list;
    }

    @Unique
    private void updateSuggestions() {
        if (messages == null || line < 0 || line >= messages.length) {
            suggestions.clear();
            suggestionsVisible = false;
            return;
        }
        String currentText = messages[line];
        if (currentText == null || currentText.trim().isEmpty()) {
            suggestions.clear();
            suggestionsVisible = false;
            return;
        }

        String query = currentText.toLowerCase().trim();
        List<EnchantmentInfo> list = getEnchantments();
        List<EnchantmentInfo> matching = new ArrayList<>();
        for (EnchantmentInfo ench : list) {
            if (ench.path.toLowerCase().startsWith(query)) {
                matching.add(ench);
            }
        }

        // Sort: alphabetical
        matching.sort((a, b) -> a.path.compareTo(b.path));

        suggestions = matching.size() > 5 ? matching.subList(0, 5) : matching;
        if (suggestions.isEmpty()) {
            suggestionsVisible = false;
        } else {
            suggestionsVisible = true;
            if (selectedSuggestionIndex >= suggestions.size()) {
                selectedSuggestionIndex = 0;
            }
        }
    }

    @Unique
    private void applySuggestion(EnchantmentInfo suggestion) {
        if (signField != null) {
            signField.selectAll();
            signField.insertText(suggestion.path);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (suggestionsVisible && !suggestions.isEmpty()) {
            int keyCode = event.key();
            if (keyCode == 264) { // GLFW_KEY_DOWN
                selectedSuggestionIndex = (selectedSuggestionIndex + 1) % suggestions.size();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            } else if (keyCode == 265) { // GLFW_KEY_UP
                selectedSuggestionIndex = (selectedSuggestionIndex - 1 + suggestions.size()) % suggestions.size();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            } else if (keyCode == 258 || keyCode == 257 || keyCode == 335) { // TAB, ENTER, KP_ENTER
                EnchantmentInfo selected = suggestions.get(selectedSuggestionIndex);
                applySuggestion(selected);
                suggestionsVisible = false;
                suggestions.clear();
                cir.setReturnValue(true);
                cir.cancel();
                return;
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("TAIL"))
    private void postKeyPressed(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        updateSuggestions();
    }

    @Inject(method = "charTyped", at = @At("TAIL"))
    private void postCharTyped(net.minecraft.client.input.CharacterEvent event, CallbackInfoReturnable<Boolean> cir) {
        updateSuggestions();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (suggestionsVisible && !suggestions.isEmpty()) {
            int boxX = 15;
            int boxY = 30;
            int boxWidth = 150;
            int boxHeight = 16 + (suggestions.size() * 12) + 12;

            // Background
            guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xD00A0A0F);
            // Accent line (blue-purple)
            guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF4A4AE2);

            // Title
            guiGraphics.text(this.font, "Suggestions", boxX + 6, boxY + 5, 0xFFD700, false);

            int currentY = boxY + 16;
            for (int i = 0; i < suggestions.size(); i++) {
                EnchantmentInfo s = suggestions.get(i);
                String roman = getRoman(s.maxLevel);
                String text = "[" + roman + "] " + s.path;
                boolean isSelected = (i == selectedSuggestionIndex);
                if (isSelected) {
                    guiGraphics.fill(boxX + 2, currentY - 1, boxX + boxWidth - 2, currentY + 11, 0x404A4AE2);
                    guiGraphics.text(this.font, "> " + text, boxX + 6, currentY, 0xFFFFFFFF, false);
                } else {
                    guiGraphics.text(this.font, "  " + text, boxX + 6, currentY, 0x99FFFFFF, false);
                }
                currentY += 12;
            }
            
            // Bottom tips
            guiGraphics.text(this.font, "Tab/Enter to apply", boxX + 6, currentY + 2, 0x55FFFFFF, false);
        }
    }
}
