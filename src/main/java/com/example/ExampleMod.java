package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.LecternBlock;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.EnchantedBookItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class ExampleMod implements ModInitializer {
    public static final String MOD_ID = "elt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final HashMap<UUID, Long> cooldownMap = new HashMap<>();
    private static final long COOLDOWN_TIME = 1000; // in milliseconds
    private static final int VILLAGER_SEARCH_RADIUS = 128; //in blocks
    private static final String enchantmentRegex = "enchantment.minecraft.";
    private static final int MAX_REROLL_COUNT = 10000;
    private static final int MAX_PROFESSION_LEVEL = 5;
    private static final int durationTicks = 5; //in ticks

    @Override
    public void onInitialize() {
        LOGGER.info("Mod initialized!");

        registerEvent();
    }

    private void registerEvent() {
        // Register the event to listen for right-click interactions
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            BlockPos clickedPos = hitResult.getBlockPos();
            Block blockClicked = world.getBlockState(clickedPos).getBlock();
            Text[] signTexts = getSignTexts(world, blockClicked, clickedPos);
            List<EnchFilter> filters = getEnchFilters(signTexts);
            if (!filters.isEmpty()) {
                VillagerEntity villager = getVillagerForLectern(player, world, clickedPos, filters);
                FilterResult result = filterTrade(player, world, villager, filters);
                if (result != null) {
                    if (world instanceof ServerWorld) {
                        spawnParticles((ServerWorld) world, result, villager, clickedPos);
                    }
                    return ActionResult.PASS;
                }
            }
            return ActionResult.PASS; // Continue normal behavior for other blocks
        });
    }

    private List<EnchFilter> getEnchFilters(Text[] signTexts) {
        List<EnchFilter> filters = new ArrayList<>();
        if (signTexts != null) {
            for (Text line : signTexts) {
                if (line.getString() != null) {
                    String[] filterText = line.getString().trim().split("\\s+");
                    if (filterText.length > 1) {
                        try {
                            if (StringUtils.isNumeric(filterText[1])) {
                                int enchLevel = Integer.parseInt(filterText[1]);
                                int maxPrice = 0;
                                if (filterText.length > 2 && StringUtils.isNumeric(filterText[2])) {
                                    maxPrice = Integer.parseInt(filterText[2]);
                                }
                                if (enchLevel > 0) {
                                    filters.add(new EnchFilter(filterText[0], enchLevel, maxPrice));
                                }
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid or overflowing numbers
                        }
                    } else if (filterText.length == 1 && !filterText[0].isEmpty()) {
                        filters.add(new EnchFilter(filterText[0], 0, 0));
                    }
                }
            }
        }
        return filters;
    }

    private Text[] getSignTexts(World world, Block blockClicked, BlockPos clickedPos) {

        // Check if the block clicked is a lectern
        if (blockClicked == Blocks.LECTERN) {
            // Get the lectern's facing direction
            Direction facingDirection = world.getBlockState(clickedPos).get(LecternBlock.FACING);

            // Get the position in front of the lectern (based on its facing direction)
            BlockPos signPos = clickedPos.offset(facingDirection);
            // Get the BlockEntity (SignBlockEntity) of the WallSign
            if (world.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
                // Retrieve the text written on the sign
                return signEntity.getText(true).getMessages(false); // Get the text on the first line (index 0)
            }

        }
        return null;
    }

    private VillagerEntity getVillagerForLectern(PlayerEntity player, World world, BlockPos clickedPos, List<EnchFilter> filters) {
        List<VillagerEntity> nearbyEntities = world.getEntitiesByClass(VillagerEntity.class, player.getBoundingBox().expand(VILLAGER_SEARCH_RADIUS), (entity) -> true);
        for (VillagerEntity villager : nearbyEntities) {
            // Check if the villager is a librarian
            if (villager.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN) {
                Optional<GlobalPos> jobSitePosOptional = villager.getBrain().getOptionalMemory(MemoryModuleType.JOB_SITE);
                // Convert GlobalPos to BlockPos and compare with clicked lectern
                if (jobSitePosOptional != null && jobSitePosOptional.isPresent()) {
                    BlockPos jobSitePos = jobSitePosOptional.get().getPos(); // Extract BlockPos from GlobalPos
                    if (jobSitePos.equals(clickedPos)) {
                        if (villager.getExperience() == 0) {
                            return villager;
                        }
                    }
                }
            }
        }
        return null;
    }

    private FilterResult filterTrade(PlayerEntity player, World world, VillagerEntity villager, List<EnchFilter> filters) {
        if (world instanceof ServerWorld) {
            UUID playerUUID = player.getUuid();
            long currentTime = System.currentTimeMillis();
            // Check if the player is still on cooldown
            if (cooldownMap.containsKey(playerUUID)) {
                long lastClickTime = cooldownMap.get(playerUUID);
                long difference = currentTime - lastClickTime;
                if (difference < COOLDOWN_TIME) {
                    return FilterResult.PASS; // Prevents further execution
                }
            }
            if (villager != null) {
                int recycleCount = 0;
                while (recycleCount <= 10000) {
                    // REMOVE librarian's profession (reset their job site)
                    villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.NONE));

                    // Wait a tick to let Minecraft update (optional, if needed)
                    villager.refreshPositionAndAngles(villager.getX(), villager.getY(), villager.getZ(), villager.getYaw(), villager.getPitch());

                    // REASSIGN librarian profession (force trade refresh)
                    villager.setVillagerData(villager.getVillagerData().withProfession(VillagerProfession.LIBRARIAN));
                    recycleCount++;
                    for (TradeOffer trade : villager.getOffers()) {
                        ItemStack sellItem = trade.getSellItem();
                        if (sellItem.getItem() instanceof EnchantedBookItem) {
                            // Get enchantments on the book
                            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.get(sellItem).entrySet()) {
                                String translationKey = entry.getKey().getTranslationKey();
                                String idAsString = translationKey.substring(translationKey.lastIndexOf('.') + 1);
                                int intValue = entry.getValue();
                                for (EnchFilter filter : filters) {
                                    int valueToSearch = filter.enchLevel;
                                    if (filter.enchLevel == 0) {
                                        valueToSearch = entry.getKey().getMaxLevel();
                                    }
                                    if (StringUtils.isNotBlank(filter.enchName) && idAsString.toLowerCase().startsWith(filter.enchName.toLowerCase()) && intValue == valueToSearch) {
                                        if (filter.maxPrice > 0 && trade.getOriginalFirstBuyItem().getCount() > filter.maxPrice) {
                                            continue;
                                        }
                                        cooldownMap.put(playerUUID, currentTime);
                                        return FilterResult.SUCCESS;
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
        return FilterResult.FAILED;
    }

    public record EnchFilter(String enchName, int enchLevel, int maxPrice) {
    }

    enum FilterResult {
        PASS,
        SUCCESS,
        FAILED
    }


    private void spawnParticles(ServerWorld world, FilterResult filterResult, VillagerEntity villager, BlockPos clickedPos) {
        if (filterResult == FilterResult.SUCCESS) {
            world.playSound(villager, villager.getBlockPos(),
                    SoundEvents.ENTITY_VILLAGER_YES,
                    SoundCategory.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        villager.getX() + 0.5,
                        villager.getY() + 1,
                        villager.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01);
                world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                        clickedPos.getX() + 0.5,
                        clickedPos.getY() + 1,
                        clickedPos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01);
//                world.sendParticles(
//                        ParticleTypes.HAPPY_VILLAGER,
//                        villager.getX() + 0.5,
//                        villager.getY() + 1,
//                        villager.getZ() + 0.5,
//                        8, 0.3, 0.3, 0.3, 0.01
//                );
//                world.sendParticles(
//                        ParticleTypes.HAPPY_VILLAGER,
//                        clickedPos.getX() + 0.5,
//                        clickedPos.getY() + 1,
//                        clickedPos.getZ() + 0.5,
//                        8, 0.3, 0.3, 0.3, 0.01
//                );
            }
        }
        if (filterResult == FilterResult.FAILED) {
            world.playSound(villager, villager.getBlockPos(),
                    SoundEvents.ENTITY_VILLAGER_NO,
                    SoundCategory.NEUTRAL, 1f, 1f);

//            world.playSound(null, villager,
//                    SoundEvents.VILLAGER_NO,
//                    SoundSource.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                        villager.getX() + 0.5,
                        villager.getY() + 1,
                        villager.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01);
                world.spawnParticles(ParticleTypes.ANGRY_VILLAGER,
                        clickedPos.getX() + 0.5,
                        clickedPos.getY() + 1,
                        clickedPos.getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.01);

//                world.sendParticles(
//                        ParticleTypes.ANGRY_VILLAGER,
//                        villager.getX() + 0.5,
//                        villager.getY() + 1,
//                        villager.getZ() + 0.5,
//                        8, 0.3, 0.3, 0.3, 0.01
//                );
//                world.sendParticles(
//                        ParticleTypes.ANGRY_VILLAGER,
//                        clickedPos.getX() + 0.5,
//                        clickedPos.getY() + 1,
//                        clickedPos.getZ() + 0.5,
//                        8, 0.3, 0.3, 0.3, 0.01
//                );
            }

        }
        // ABC test

    }
}
