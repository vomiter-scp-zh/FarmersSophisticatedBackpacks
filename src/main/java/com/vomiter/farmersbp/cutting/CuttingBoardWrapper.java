package com.vomiter.farmersbp.cutting;

import com.vomiter.farmersbp.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedcore.inventory.InventoryHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.common.registry.ModSounds;
import vectorwing.farmersdelight.common.utility.TextUtils;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CuttingBoardWrapper extends UpgradeWrapperBase<CuttingBoardWrapper, CuttingBoardUpgradeItem>
        implements ITickableUpgrade {
    private final ItemStackHandler inventory;
    private static final String INPUT_FILTER_TAG = "AutoCuttingInputFilter";
    private static final String BLOCKED_INVENTORY_TAG = "AutoCuttingBlockedInventory";
    private static final String LAST_TICK_TAG = "AutoCuttingLastTick";
    private FilterLogic inputFilterLogic;
    private long lastTick;

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
                upgrade.addTagElement("CuttingInventory", serializeNBT());
                save();
            }
        };
        NBTHelper.getCompound(upgrade, "CuttingInventory").ifPresent(inventory::deserializeNBT);
        CompoundTag data = upgrade.getTag();
        lastTick = data != null && data.contains(LAST_TICK_TAG) ? data.getLong(LAST_TICK_TAG) : Long.MIN_VALUE;
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
            CompoundTag saved = upgrade.getTagElement(INPUT_FILTER_TAG);
            if (saved != null && saved.contains("filters", 10)) {
                filterSlots = Math.max(filterSlots, saved.getCompound("filters").getInt("Size"));
            }
            inputFilterLogic = new FilterLogic(upgrade, upgradeSaveHandler, filterSlots, INPUT_FILTER_TAG);
            inputFilterLogic.setAllowByDefault(true);
            inputFilterLogic.setEmptyAllowListMatchesEverything();
        }
        return inputFilterLogic;
    }

    @Override
    public void tick(@Nullable Entity entity, Level level, @NotNull BlockPos pos) {
        if (!isAutomatic() || !isEnabled() || level.isClientSide || lastTick == level.getGameTime()) {
            return;
        }
        lastTick = level.getGameTime();
        upgrade.getOrCreateTag().putLong(LAST_TICK_TAG, lastTick);
        InventoryHandler storage = storageWrapper.getInventoryHandler();
        CompoundTag blockedInventory = upgrade.getTagElement(BLOCKED_INVENTORY_TAG);
        if (blockedInventory != null) {
            if (blockedInventory.equals(storage.serializeNBT())) {
                return;
            }
            upgrade.removeTagKey(BLOCKED_INVENTORY_TAG);
            save();
        }
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
            CuttingBoardRecipe recipe = level.getRecipeManager().getRecipesFor(ModRecipeTypes.CUTTING.get(), inputs, level)
                    .stream().filter(candidate -> candidate.getTool().test(tool)).findFirst().orElse(null);
            if (recipe == null) {
                continue;
            }
            int fortune = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
            List<ItemStack> results = recipe.rollResults(level.random, fortune, inputs);
            if (!storeAutomaticResults(storage, slot, results)) {
                upgrade.addTagElement(BLOCKED_INVENTORY_TAG, storage.serializeNBT());
                save();
                return;
            }
            ItemStack damaged = tool.copy();
            if (damaged.hurt(1, level.random, entity instanceof ServerPlayer player ? player : null)) {
                damaged.shrink(1);
            }
            inventory.setStackInSlot(1, damaged);
            playCuttingSound(level, pos, recipe);
            return; // At most one ingredient per server tick.
        }
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
        List<CuttingBoardRecipe> recipes = player.level().getRecipeManager().getRecipesFor(ModRecipeTypes.CUTTING.get(), inputs, player.level());
        if (recipes.isEmpty()){
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ModNetwork.FBPCuttingResponse(CuttingResult.INVALID_ITEM.ordinal())
            );
            return;
        }
        var recipe = recipes.stream().filter((cuttingRecipe) -> cuttingRecipe.getTool().test(tool)).findFirst().orElse(null);
        if (recipe == null) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ModNetwork.FBPCuttingResponse(CuttingResult.INVALID_TOOL.ordinal())
            );
            return;
        }

        int fortune = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
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
            if (damaged.hurt(1, player.level().random, player)) {
                damaged.shrink(1);
            }
            inventory.setStackInSlot(1, damaged);
        }

        playCuttingSound(player.level(), player.blockPosition(), recipe);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ModNetwork.FBPCuttingResponse(CuttingResult.EMPTY.ordinal())
        );
    }

    private void playCuttingSound(Level level, BlockPos pos, CuttingBoardRecipe recipe) {
        SoundEvent sound = ModSounds.BLOCK_CUTTING_BOARD_KNIFE.get();
        String soundId = recipe.getSoundEventID();
        if (!soundId.isEmpty()) {
            ResourceLocation key = ResourceLocation.tryParse(soundId);
            SoundEvent custom = key == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(key);
            if (custom != null) {
                sound = custom;
            }
        }
        level.playSound(null, pos, sound, SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}
