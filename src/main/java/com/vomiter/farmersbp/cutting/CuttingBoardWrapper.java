package com.vomiter.farmersbp.cutting;

import com.vomiter.farmersbp.network.ModNetwork;
import com.vomiter.farmersbp.FarmerSBP;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;
import net.neoforged.neoforge.network.PacketDistributor;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.init.ModCoreDataComponents;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipeInput;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.common.registry.ModSounds;
import vectorwing.farmersdelight.common.utility.TextUtils;
import vectorwing.farmersdelight.common.utility.ItemUtils;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CuttingBoardWrapper extends UpgradeWrapperBase<CuttingBoardWrapper, CuttingBoardUpgradeItem>
        implements ITickableUpgrade {
    private final ItemStackHandler inventory;
    private FilterLogic inputFilterLogic;
    private long lastTick;
    private int blockedInventorySignature;

    public enum CuttingResult{
        EMPTY,
        INVALID_ITEM,
        INVALID_TOOL
    }

    private static CuttingResult cuttingResult = CuttingResult.EMPTY;
    public static CuttingResult getCuttingResult() {
        return cuttingResult;
    }

    public static void setCuttingResult(CuttingResult cuttingResult) {
        CuttingBoardWrapper.cuttingResult = cuttingResult;
    }

    static Map<CuttingResult, Component> messages = new EnumMap<>(CuttingResult.class);
    static {
        messages.put(CuttingResult.EMPTY, Component.empty());
        messages.put(CuttingResult.INVALID_ITEM, TextUtils.block("cutting_board.invalid_item"));
        messages.put(CuttingResult.INVALID_TOOL, TextUtils.block("cutting_board.invalid_tool"));
    }

    public CuttingBoardWrapper(IStorageWrapper storage, ItemStack upgrade, Consumer<ItemStack> saveHandler) {
        super(storage, upgrade, saveHandler);
        inventory = new ItemStackHandler(2) {
            @Override
            protected void onContentsChanged(int slot) {
                CuttingBoardWrapper.setCuttingResult(CuttingResult.EMPTY);
                saveInventory();
                save();
            }
        };
        NonNullList<ItemStack> saved = NonNullList.withSize(2, ItemStack.EMPTY);
        upgrade.getOrDefault(FarmerSBP.CUTTING_INVENTORY, net.minecraft.world.item.component.ItemContainerContents.EMPTY)
                .copyInto(saved);
        for (int slot = 0; slot < saved.size(); slot++) {
            inventory.setStackInSlot(slot, saved.get(slot));
        }
        lastTick = Long.MIN_VALUE;
    }

    private void saveInventory() {
        NonNullList<ItemStack> contents = NonNullList.withSize(inventory.getSlots(), ItemStack.EMPTY);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            contents.set(slot, inventory.getStackInSlot(slot).copy());
        }
        upgrade.set(FarmerSBP.CUTTING_INVENTORY,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(contents));
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public boolean canBeDisabled() {
        return isAutomatic();
    }

    public boolean isAutomatic() {
        return upgradeItem.isAutomatic();
    }

    public FilterLogic getInputFilterLogic() {
        if (inputFilterLogic == null) {
            int filterSlots = Config.SERVER.autoSmeltingUpgrade.inputFilterSlots.get();
            inputFilterLogic = new FilterLogic(upgrade, upgradeSaveHandler, filterSlots,
                    ModCoreDataComponents.FILTER_ATTRIBUTES);
            inputFilterLogic.setAllowByDefault(true);
            inputFilterLogic.setEmptyAllowListMatchesEverything();
        }
        return inputFilterLogic;
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, @NotNull BlockPos pos) {
        if (!isAutomatic() || !isEnabled() || level.isClientSide() || lastTick == level.getGameTime()) {
            return;
        }
        lastTick = level.getGameTime();
        InventoryHandler storage = storageWrapper.getInventoryHandler();
        int currentSignature = inventorySignature(storage);
        if (blockedInventorySignature != 0 && blockedInventorySignature == currentSignature) {
            return;
        }
        blockedInventorySignature = 0;
        ItemStack tool = inventory.getStackInSlot(1);
        if (tool.isEmpty()) {
            return;
        }
        ItemStackHandler recipeInventory = new ItemStackHandler(2);
        recipeInventory.setStackInSlot(1, tool.copy());
        RecipeWrapper inputs = new RecipeWrapper(recipeInventory);
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack ingredient = storage.getStackInSlot(slot);
            if (ingredient.isEmpty() || !storage.isSlotAccessible(slot) || storage.isInfinite(slot)
                    || !getInputFilterLogic().matchesFilter(ingredient)) {
                continue;
            }
            recipeInventory.setStackInSlot(0, ingredient.copyWithCount(1));
            CuttingBoardRecipeInput recipeInput = new CuttingBoardRecipeInput(
                    recipeInventory.getStackInSlot(0), tool);
            CuttingBoardRecipe recipe = level.getRecipeManager().getRecipesFor(
                            ModRecipeTypes.CUTTING.get(), recipeInput, level)
                    .stream().map(holder -> holder.value())
                    .filter(candidate -> candidate.getTool().test(tool)).findFirst().orElse(null);
            if (recipe == null) {
                continue;
            }
            int fortune = ItemUtils.getValidatedEnchantmentLevel(
                    Enchantments.FORTUNE, level.registryAccess(), tool);
            List<ItemStack> results = recipe.rollResults(level.random, fortune, inputs);
            if (!storeAutomaticResults(storage, slot, results)) {
                blockedInventorySignature = currentSignature;
                return;
            }
            ItemStack damaged = tool.copy();
            if (level instanceof ServerLevel serverLevel) {
                damaged.hurtAndBreak(1, serverLevel,
                        entity instanceof ServerPlayer player ? player : null, item -> {});
            }
            inventory.setStackInSlot(1, damaged);
            playCuttingSound(level, pos, recipe);
            return; // At most one ingredient per server tick.
        }
    }

    private int inventorySignature(InventoryHandler storage) {
        int hash = 1;
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            hash = 31 * hash + ItemStack.hashItemAndComponents(stack);
            hash = 31 * hash + stack.getCount();
        }
        return hash;
    }

    private boolean storeAutomaticResults(InventoryHandler storage, int ingredientSlot, List<ItemStack> results) {
        // Reserve room for the entire roll before touching real storage. In particular,
        // two different results must not both claim the same last empty slot.
        ItemStackHandler staged = new ItemStackHandler(storage.getSlots()) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return storage.isSlotAccessible(slot) && !storage.isInfinite(slot) && storage.isItemValid(slot, stack);
            }

            @Override
            public int getSlotLimit(int slot) {
                return storage.getSlotLimit(slot);
            }

            @Override
            protected int getStackLimit(int slot, @NotNull ItemStack stack) {
                return storage.getStackLimit(slot, stack);
            }
        };
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            staged.setStackInSlot(slot, storage.getStackInSlot(slot).copy());
        }
        for (ItemStack result : results) {
            if (!ItemHandlerHelper.insertItemStacked(staged, result.copy(), false).isEmpty()) {
                return false;
            }
        }
        // Write the validated plan directly, avoiding insert hooks that could redirect
        // individual results or change storage between the capacity check and commit.
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack planned = staged.getStackInSlot(slot);
            if (!ItemStack.matches(storage.getStackInSlot(slot), planned)) {
                storage.setStackInSlot(slot, planned.copy());
            }
        }
        ItemStack ingredient = storage.getStackInSlot(ingredientSlot).copy();
        ingredient.shrink(1);
        storage.setStackInSlot(ingredientSlot, ingredient);
        return true;
    }

    public void cut(ServerPlayer player) {
        if (isAutomatic()) {
            return;
        }
        ItemStack input = inventory.getStackInSlot(0);
        ItemStack tool = inventory.getStackInSlot(1);
        if (input.isEmpty() || tool.isEmpty()) {
            return;
        }
        RecipeWrapper inputs = new RecipeWrapper(inventory);
        CuttingBoardRecipeInput recipeInput = new CuttingBoardRecipeInput(input, tool);
        List<CuttingBoardRecipe> recipes = player.level().getRecipeManager()
                .getRecipesFor(ModRecipeTypes.CUTTING.get(), recipeInput, player.level())
                .stream().map(holder -> holder.value()).toList();
        if (recipes.isEmpty()){
            PacketDistributor.sendToPlayer(player,
                    new ModNetwork.FBPCuttingResponse(CuttingResult.INVALID_ITEM.ordinal()));
            return;
        }
        var recipe = recipes.stream().filter((cuttingRecipe) -> cuttingRecipe.getTool().test(tool)).findFirst().orElse(null);
        if (recipe == null) {
            PacketDistributor.sendToPlayer(player,
                    new ModNetwork.FBPCuttingResponse(CuttingResult.INVALID_TOOL.ordinal()));
            return;
        }

        int fortune = ItemUtils.getValidatedEnchantmentLevel(
                Enchantments.FORTUNE, player.level().registryAccess(), tool);
        List<ItemStack> results = recipe.rollResults(player.level().random, fortune, inputs);
        inventory.extractItem(0, 1, false);
        for (ItemStack result : results) {
            ItemStack leftover = ItemHandlerHelper.insertItem(storageWrapper.getInventoryHandler(), result.copy(), false);
            if (!leftover.isEmpty()) {
                player.drop(leftover, false);
            }
        }

        if (!player.getAbilities().instabuild){
            ItemStack damaged = tool.copy();
            damaged.hurtAndBreak(1, player.serverLevel(), player, item -> {});
            inventory.setStackInSlot(1, damaged);
        }

        playCuttingSound(player.level(), player.blockPosition(), recipe);
        PacketDistributor.sendToPlayer(player,
                new ModNetwork.FBPCuttingResponse(CuttingResult.EMPTY.ordinal()));
    }

    private void playCuttingSound(Level level, BlockPos pos, CuttingBoardRecipe recipe) {
        SoundEvent sound = ModSounds.BLOCK_CUTTING_BOARD_KNIFE.get();
        sound = recipe.getSoundEvent().orElse(sound);
        level.playSound(null, pos, sound, SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}
