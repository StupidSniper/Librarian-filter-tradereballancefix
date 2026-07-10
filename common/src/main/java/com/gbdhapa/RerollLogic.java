package com.gbdhapa;

import com.gbdhapa.config.TradeConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RerollLogic {
    private static final HashMap<UUID, Map<BlockPos, Long>> cooldownMap = new HashMap<>();
    private static final long COOLDOWN_TIME = 1000;
    private static final int VILLAGER_SEARCH_RADIUS = 128;
    private static final int MAX_REROLL_COUNT = 10000;
    private static final int durationTicks = 5;

    public static InteractionResult handleBlockUse(Player player, Level world, BlockPos clickedPos) {
        if (!TradeConfig.INSTANCE.enableReroll) {
            return InteractionResult.PASS;
        }
        UUID playerUUID = player.getUUID();
        long currentTime = System.currentTimeMillis();
        if (isRerollCooldown(playerUUID, clickedPos, currentTime)) {
            return InteractionResult.PASS;
        }
        Block blockClicked = world.getBlockState(clickedPos).getBlock();
        List<String> signTexts = getSignTexts(world, blockClicked, clickedPos);
        if (signTexts == null || signTexts.isEmpty()) {
            return InteractionResult.PASS;
        }
        List<TradeFilter> filters = getEnchFilters(signTexts);
        if (!filters.isEmpty() && world instanceof ServerLevel) {
            Villager villager = getVillagerForWorkstation(player, (ServerLevel) world, clickedPos);
            if (villager != null) {
                FilterResult filterResult = filterTrade(villager, filters);
                villager.refreshBrain((ServerLevel) world);
                spawnParticles((ServerLevel) world, filterResult, villager, clickedPos);
                cooldownMap.put(playerUUID, Map.of(clickedPos, currentTime));
            }
        }
        return InteractionResult.PASS;
    }

    private static Boolean isRerollCooldown(UUID playerUUID, BlockPos clickedPos, long currentTime) {
        if (cooldownMap.containsKey(playerUUID)) {
            Long lastClickTime = cooldownMap.get(playerUUID).get(clickedPos);
            if (lastClickTime != null) {
                long difference = currentTime - lastClickTime;
                if (difference < COOLDOWN_TIME) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<TradeFilter> getEnchFilters(List<String> signTexts) {
        List<TradeFilter> filters = new ArrayList<>();
        if (signTexts != null) {
            for (String line : signTexts) {
                if (line != null && !line.isEmpty()) {
                    String[] filterText = line.trim().split(" ");
                    if (filterText.length > 1) {
                        if (StringUtils.isNumeric(filterText[1])) {
                            int enchLevel = Integer.parseInt(filterText[1]);
                            if (enchLevel > 0) {
                                if (filterText.length > 2 && StringUtils.isNumeric(filterText[2])) {
                                    int price = Integer.parseInt(filterText[2]);
                                    filters.add(new TradeFilter(filterText[0], enchLevel, price));
                                } else {
                                    filters.add(new TradeFilter(filterText[0], enchLevel, 0));
                                }
                            }
                        }
                    } else {
                        filters.add(new TradeFilter(filterText[0], 0, 0));
                    }
                }
            }
        }
        return filters;
    }

    private static List<String> getSignTexts(Level world, Block blockClicked, BlockPos clickedPos) {
        if (blockClicked == Blocks.LECTERN) {
            Direction facingDirection = world.getBlockState(clickedPos).getValue(LecternBlock.FACING);
            BlockPos signPos = clickedPos.relative(facingDirection);
            if (world.getBlockEntity(signPos) instanceof SignBlockEntity signEntity) {
                return Arrays.stream(signEntity.getFrontText().getMessages(false)).map(Component::getString).toList();
            }
        }

        if (blockClicked != Blocks.AIR) {
            BlockState blockState = world.getBlockState(clickedPos);
            if (PoiTypes.hasPoi(blockState)) {
                SignBlockEntity signEntity = getAttachedSign(world, clickedPos);
                if (signEntity != null) {
                    return Arrays.stream(signEntity.getFrontText().getMessages(false)).map(Component::getString).toList();
                }
            }
        }
        return new ArrayList<>();
    }

    private static Villager getVillagerForWorkstation(Player player, ServerLevel world, BlockPos clickedPos) {
        AABB box = player.getBoundingBox().inflate(VILLAGER_SEARCH_RADIUS);
        List<Villager> nearbyVillagers = world.getEntitiesOfClass(Villager.class, box, v -> true);
        for (Villager villager : nearbyVillagers) {
            if (villager.getVillagerData().profession().is(VillagerProfession.LIBRARIAN)) {
                Optional<GlobalPos> jobSitePosOptional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                if (jobSitePosOptional.isPresent()) {
                    BlockPos jobSitePos = jobSitePosOptional.get().pos();
                    if (jobSitePos.equals(clickedPos)) {
                        if (TradeConfig.INSTANCE.enableEachLevelReroll || villager.getVillagerXp() == 0) {
                            return villager;
                        }
                    }
                }
            } else {
                Optional<GlobalPos> jobSitePosOptional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
                if (jobSitePosOptional.isPresent()) {
                    BlockPos jobSitePos = jobSitePosOptional.get().pos();
                    if (jobSitePos.equals(clickedPos)) {
                        if (TradeConfig.INSTANCE.enableEachLevelReroll || villager.getVillagerXp() == 0) {
                            return villager;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static FilterResult filterTrade(Villager villager, List<TradeFilter> filters) {
        RegistryAccess access = villager.level().registryAccess();
        int recycleCount = 0;
        MerchantOffers originalOffers = villager.getOffers();
        if (checkIfPlayerHasTradedLastOffers(originalOffers)) {
            return FilterResult.FAILED;
        }
        while (recycleCount <= MAX_REROLL_COUNT) {
            VillagerData data = villager.getVillagerData();
            Holder<VillagerProfession> profession = data.profession();
            Holder<VillagerProfession> noneProfession = access.getOrThrow(VillagerProfession.NONE);
            villager.setVillagerData(data.withProfession(noneProfession));
            villager.setVillagerData(villager.getVillagerData().withProfession(profession));

            recycleCount++;
            MerchantOffers offers = villager.getOffers();
            for (MerchantOffer trade : offers) {
                if (profession.is(VillagerProfession.LIBRARIAN)) {
                    if (trade.getResult().getItem() == Items.ENCHANTED_BOOK) {
                        if (filterEnchantmentBook(filters, trade) == FilterResult.SUCCESS) {
                            applyNewOffers(villager, originalOffers, offers);
                            return FilterResult.SUCCESS;
                        }
                    }
                } else if (filterTrades(filters, trade) == FilterResult.SUCCESS) {
                    applyNewOffers(villager, originalOffers, offers);
                    return FilterResult.SUCCESS;
                }
            }
        }
        villager.setOffers(new MerchantOffers());
        villager.setOffers(originalOffers);
        return FilterResult.FAILED;
    }

    private static void applyNewOffers(Villager villager, MerchantOffers originalOffers, MerchantOffers newOffers) {
        villager.setOffers(new MerchantOffers());
        originalOffers.removeLast();
        originalOffers.removeLast();
        originalOffers.addAll(newOffers);
        villager.setOffers(originalOffers);
    }

    private static boolean checkIfPlayerHasTradedLastOffers(MerchantOffers originalOffers) {
        if (originalOffers == null || originalOffers.isEmpty()) return false;
        int offersSize = originalOffers.size();
        if (offersSize % 2 == 0 && offersSize >= 2) {
            return originalOffers.get(offersSize - 2).getUses() > 0 || originalOffers.get(offersSize - 1).getUses() > 0;
        }
        return originalOffers.get(offersSize - 1).getUses() > 0;
    }

    private static FilterResult filterEnchantmentBook(List<TradeFilter> filters, MerchantOffer trade) {
        ItemEnchantments enchantments = trade.getResult().getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchHolder = entry.getKey();
            int enchBookLevel = entry.getIntValue();
            String enchName = enchHolder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");

            for (TradeFilter filter : filters) {
                int expectedLevel = filter.enchLevel == 0 ? enchHolder.value().getMaxLevel() : filter.enchLevel;
                if (enchName.toLowerCase().startsWith(filter.filterName.toLowerCase()) && enchBookLevel == expectedLevel) {
                    if (filter.price == 0 || trade.getCostA().getCount() <= filter.price) {
                        return FilterResult.SUCCESS;
                    }
                }
            }
        }
        return null;
    }

    private static FilterResult filterTrades(List<TradeFilter> filters, MerchantOffer trade) {
        String sellItemName = trade.getResult().getItemName().getString().toLowerCase();
        if (filters.stream().anyMatch(f -> sellItemName.contains(formatFilterName(f)))) return FilterResult.SUCCESS;
        
        String buyItem1Name = trade.getCostA().getItemName().getString().toLowerCase();
        if (filters.stream().anyMatch(f -> buyItem1Name.contains(formatFilterName(f)))) return FilterResult.SUCCESS;

        ItemStack costB = trade.getCostB();
        if (costB != ItemStack.EMPTY) {
            String buyItem2Name = costB.getItemName().getString().toLowerCase();
            if (filters.stream().anyMatch(f -> buyItem2Name.contains(formatFilterName(f)))) return FilterResult.SUCCESS;
        }
        return null;
    }

    private static String formatFilterName(TradeFilter f) {
        return f.filterName.toLowerCase().replaceAll("_", " ");
    }

    public static SignBlockEntity getAttachedSign(Level world, BlockPos pos) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (world.getBlockEntity(side) instanceof SignBlockEntity signEntity) return signEntity;
        }
        return null;
    }

    private static void spawnParticles(ServerLevel world, FilterResult filterResult, Villager villager, BlockPos clickedPos) {
        if (filterResult == FilterResult.SUCCESS) {
            world.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX() + 0.5, villager.getY() + 1, villager.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
                world.sendParticles(ParticleTypes.HAPPY_VILLAGER, clickedPos.getX() + 0.5, clickedPos.getY() + 1, clickedPos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
            }
        } else if (filterResult == FilterResult.FAILED) {
            world.playSound(null, villager, SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, 1f);
            for (int i = 0; i < durationTicks; i++) {
                world.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX() + 0.5, villager.getY() + 1, villager.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
                world.sendParticles(ParticleTypes.ANGRY_VILLAGER, clickedPos.getX() + 0.5, clickedPos.getY() + 1, clickedPos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    public static int executeFind(CommandSourceStack source, String query) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        net.minecraft.server.level.ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();

        double radius = 128.0;
        AABB box = player.getBoundingBox().inflate(radius);
        List<Villager> villagers = level.getEntitiesOfClass(
            Villager.class,
            box,
            v -> v.isAlive()
        );

        int matchCount = 0;
        for (Villager villager : villagers) {
            MerchantOffers offers = villager.getOffers();
            boolean matchFound = false;
            for (MerchantOffer trade : offers) {
                if (trade.getResult().getItem() == Items.ENCHANTED_BOOK) {
                    ItemEnchantments enchantments = trade.getResult().getOrDefault(
                        DataComponents.STORED_ENCHANTMENTS,
                        ItemEnchantments.EMPTY
                    );
                    for (var entry : enchantments.entrySet()) {
                        Holder<Enchantment> enchHolder = entry.getKey();
                        String enchName = enchHolder.unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
                        if (enchName.toLowerCase().contains(query.toLowerCase())) {
                            matchFound = true;
                            break;
                        }
                    }
                }
                if (matchFound) {
                    break;
                }
            }

            if (matchFound) {
                // Apply GLOWING effect for 30 seconds (600 ticks)
                villager.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING,
                    600, // 30 seconds
                    0,
                    false,
                    false
                ));
                matchCount++;
            }
        }

        final int count = matchCount;
        if (count > 0) {
            source.sendSuccess(() -> Component.literal("§aFound " + count + " villager(s) offering '" + query + "'. They are now glowing for 30 seconds!"), true);
        } else {
            source.sendFailure(Component.literal("No loaded villagers found offering '" + query + "'."));
        }

        return count;
    }

    public record TradeFilter(String filterName, int enchLevel, int price) {}
    enum FilterResult { SUCCESS, FAILED }
}
