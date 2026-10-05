package com.vomiter.farmersbp.pot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import org.jetbrains.annotations.NotNull;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public final class CookingPotWrapper extends UpgradeWrapperBase<CookingPotWrapper, CookingPotUpgradeItem>
        implements ITickableUpgrade {
    static final int MEAL_DISPLAY_SLOT = 6;
    static final int CONTAINER = 7;
    static final int OUTPUT = 8;
    static final int INVENTORY_SIZE = 9;
    static final int SELECTED_MEAL = 0;
    static final int FILTER_START = 1;
    static final int FILTER_COUNT = 7;
    private final ItemStackHandler inventory;
    private final ItemStackHandler settings;
    private FilterLogic inputFilterLogic;
    private static final String INPUT_FILTER_TAG = "AutoPotInputFilter";

    private ResourceLocation activeRecipe;
    private int cookTime;
    private int totalCookTime;
    private long lastTick = Long.MIN_VALUE;
    private static boolean shouldRenderOriginalTooltip = true;

    public CookingPotWrapper(IStorageWrapper storage, ItemStack upgrade, Consumer<ItemStack> saveHandler) {
        super(storage, upgrade, saveHandler);
        settings = new ItemStackHandler(1 + FILTER_COUNT) {
            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                upgrade.addTagElement("AutoPotSettings", serializeNBT());
                save();
            }
        };
        NBTHelper.getCompound(upgrade, "AutoPotSettings").ifPresent(settings::deserializeNBT);
        inventory = new ItemStackHandler(INVENTORY_SIZE) {
            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return slot != OUTPUT;
            }

            @Override
            protected void onContentsChanged(int slot) {
                saveState();
            }
        };
        NBTHelper.getCompound(upgrade, "PotInventory").ifPresent(inventory::deserializeNBT);
        CompoundTag data = upgrade.getTagElement("PotState");
        cookTime = data == null ? 0 : data.getInt("CookTime");
        totalCookTime = data == null ? 0 : data.getInt("TotalCookTime");
        if (data != null && data.contains("Recipe", 8)) {
            activeRecipe = ResourceLocation.tryParse(data.getString("Recipe"));
        }
    }

    public static boolean isShouldRenderOriginalTooltip() {
        return shouldRenderOriginalTooltip;
    }

    public static void setShouldRenderOriginalTooltip(boolean shouldRenderOriginalTooltip) {
        CookingPotWrapper.shouldRenderOriginalTooltip = shouldRenderOriginalTooltip;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public boolean isAutomatic() {
        return ((CookingPotUpgradeItem) upgrade.getItem()).isAutomatic();
    }

    public ItemStackHandler getSettings() {
        return settings;
    }

    @Nullable
    private CookingPotRecipe getPotRecipeForMeal(ItemStack mealStack, Level level) {
        if (mealStack.isEmpty()) {
            return null;
        }
        // Keep selection deterministic when several recipes produce the same meal.
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.COOKING.get()).stream()
                .filter(recipe -> ItemStack.isSameItemSameTags(recipe.getResultItem(level.registryAccess()), mealStack)).min(Comparator.comparing(recipe -> recipe.getId().toString())).orElse(null);
    }

    public Ingredient getAssignedIngredient(int slot, Level level) {
        CookingPotRecipe recipe = getPotRecipeForMeal(settings.getStackInSlot(SELECTED_MEAL), level);
        if (slot == CONTAINER) {
            ItemStack container = getServingContainer(recipe, level);
            return container.isEmpty() ? Ingredient.EMPTY : Ingredient.of(container);
        }
        if (recipe == null) {
            return Ingredient.EMPTY;
        }
        List<Ingredient> ingredients = recipe.getIngredients();
        return slot >= 0 && slot < ingredients.size() ? ingredients.get(slot) : Ingredient.EMPTY;
    }

    private ItemStack getServingContainer(@Nullable CookingPotRecipe selectedRecipe, Level level) {
        ItemStack pending = inventory.getStackInSlot(MEAL_DISPLAY_SLOT);
        if (pending.isEmpty()) {
            return selectedRecipe == null ? ItemStack.EMPTY : selectedRecipe.getOutputContainer();
        }
        // Finish serving the previous meal even after the selection is changed or cleared.
        CookingPotRecipe pendingRecipe = getPotRecipeForMeal(pending, level);
        return pendingRecipe == null ? pending.getCraftingRemainingItem() : pendingRecipe.getOutputContainer();
    }

    public FilterLogic getInputFilterLogic() {
        if (inputFilterLogic == null) {
            int filterSlots = Config.SERVER.autoSmeltingUpgrade.inputFilterSlots.get();
            // A smaller configured grid must not discard existing ghost filters.
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

    private boolean matchesFilter(ItemStack stack) {
        return getInputFilterLogic().matchesFilter(stack);
    }

    private boolean returnInvalidInput(int slot, Ingredient ingredient) {
        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty() || ingredient.test(stack)) {
            return true;
        }
        //put non-fitting stack back to backpack storage
        ItemStack remainder = ItemHandlerHelper.insertItem(storageWrapper.getInventoryHandler(), stack.copy(), false);
        if (remainder.getCount() != stack.getCount()) {
            inventory.extractItem(slot, stack.getCount(), false);
            inventory.insertItem(slot, remainder, false);
        }
        return remainder.isEmpty();
    }

    private void refillInput(int slot, Ingredient ingredient) {
        if (ingredient.isEmpty()) {
            return;
        }
        IItemHandler storage = storageWrapper.getInventoryHandler();
        for (int i = 0; i < storage.getSlots(); i++) {
            ItemStack candidate = storage.extractItem(i, 1, true);
            if (candidate.isEmpty() || !ingredient.test(candidate) || !matchesFilter(candidate)
                    || !inventory.insertItem(slot, candidate, true).isEmpty()) {
                continue;
            }
            ItemStack extracted = storage.extractItem(i, 1, false);
            if (!extracted.isEmpty()) {
                inventory.insertItem(slot, extracted, false);
                return;
            }
        }
    }

    private boolean prepareAutomaticInputs(@Nullable CookingPotRecipe recipe, ItemStack container) {
        Ingredient[] assigned = new Ingredient[CONTAINER + 1];
        for (int slot = 0; slot < MEAL_DISPLAY_SLOT; slot++) {
            assigned[slot] = recipe != null && slot < recipe.getIngredients().size()
                    ? recipe.getIngredients().get(slot) : Ingredient.EMPTY;
        }
        assigned[CONTAINER] = container.isEmpty() ? Ingredient.EMPTY : Ingredient.of(container);
        boolean cleared = true;
        for (int slot = 0; slot <= CONTAINER; slot++) {
            if (slot == MEAL_DISPLAY_SLOT) {
                continue;
            }
            if (!returnInvalidInput(slot, assigned[slot])) {
                cleared = false;
            }
        }
        if (!cleared) {
            return false;
        }
        for (int slot = 0; slot <= CONTAINER; slot++) {
            if (slot != MEAL_DISPLAY_SLOT) {
                refillInput(slot, assigned[slot]);
            }
        }

        // A broad ingredient may have taken a stack also needed by a later slot.
        // Include the serving-container slot, but always leave one item in the donor.
        for (int slot = 0; slot <= CONTAINER; slot++) {
            if (slot == MEAL_DISPLAY_SLOT || assigned[slot].isEmpty() || !inventory.getStackInSlot(slot).isEmpty()) {
                continue;
            }
            for (int donor = 0; donor <= CONTAINER; donor++) {
                if (donor == MEAL_DISPLAY_SLOT || donor == slot) {
                    continue;
                }
                ItemStack candidate = inventory.getStackInSlot(donor);
                if (candidate.getCount() > 1 && assigned[slot].test(candidate) && matchesFilter(candidate)) {
                    inventory.insertItem(slot, inventory.extractItem(donor, 1, false), false);
                    break;
                }
            }
        }
        for (int slot = 0; slot < CONTAINER; slot++) {
            if (slot != MEAL_DISPLAY_SLOT && !assigned[slot].isEmpty()
                    && !assigned[slot].test(inventory.getStackInSlot(slot))) {
                return false;
            }
        }
        return recipe != null;
    }

    public int getCookTime() {
        CompoundTag data = upgrade.getTagElement("PotState");
        return data == null ? cookTime : data.getInt("CookTime");
    }

    public int getTotalCookTime() {
        CompoundTag data = upgrade.getTagElement("PotState");
        return data == null ? totalCookTime : data.getInt("TotalCookTime");
    }

    @Override
    public boolean canBeDisabled() {
        return false;
    }

    private boolean doesMealHaveContainer(ItemStack meal) {
        return !this.inventory.getStackInSlot(CONTAINER).isEmpty() || meal.hasCraftingRemainingItem();
    }

    private void moveMealToOutput() {
        ItemStack mealStack = this.inventory.getStackInSlot(MEAL_DISPLAY_SLOT);
        ItemStack outputStack = this.inventory.getStackInSlot(OUTPUT);
        int mealCount = Math.min(mealStack.getCount(), mealStack.getMaxStackSize() - outputStack.getCount());
        if (outputStack.isEmpty()) {
            this.inventory.setStackInSlot(OUTPUT, mealStack.split(mealCount));
        } else if (ItemStack.isSameItemSameTags(mealStack, outputStack)) {
            mealStack.shrink(mealCount);
            outputStack.grow(mealCount);
        }
    }

    public boolean isContainerValid(ItemStack containerItem) {
        if (containerItem.isEmpty()) {
            return false;
        } else {
            return !inventory.getStackInSlot(CONTAINER).isEmpty() && ItemStack.isSameItem(inventory.getStackInSlot(CONTAINER), containerItem);
        }
    }

    private void useStoredContainersOnMeal() {
        ItemStack mealStack = this.inventory.getStackInSlot(MEAL_DISPLAY_SLOT);
        ItemStack containerInputStack = this.inventory.getStackInSlot(CONTAINER);
        ItemStack outputStack = this.inventory.getStackInSlot(OUTPUT);
        if (this.isContainerValid(containerInputStack) && outputStack.getCount() < outputStack.getMaxStackSize()) {
            int smallerStackCount = Math.min(mealStack.getCount(), containerInputStack.getCount());
            int mealCount = Math.min(smallerStackCount, mealStack.getMaxStackSize() - outputStack.getCount());
            if (outputStack.isEmpty()) {
                containerInputStack.shrink(mealCount);
                this.inventory.setStackInSlot(OUTPUT, mealStack.split(mealCount));
            } else if (ItemStack.isSameItemSameTags(outputStack, mealStack)) {
                mealStack.shrink(mealCount);
                containerInputStack.shrink(mealCount);
                outputStack.grow(mealCount);
            }
        }

    }
    private boolean canCook(CookingPotRecipe recipe, Level level) {
        ItemStack resultStack = recipe.assemble(new RecipeWrapper(this.inventory), level.registryAccess());
        if (resultStack.isEmpty()) {
            return false;
        } else {
            ItemStack storedMealStack = this.inventory.getStackInSlot(MEAL_DISPLAY_SLOT);
            if (storedMealStack.isEmpty()) {
                return true;
            } else if (!ItemStack.isSameItemSameTags(storedMealStack, resultStack)) {
                return false;
            } else if (storedMealStack.getCount() + resultStack.getCount() <= this.inventory.getSlotLimit(MEAL_DISPLAY_SLOT)) {
                return true;
            } else {
                return storedMealStack.getCount() + resultStack.getCount() <= resultStack.getMaxStackSize();
            }
        }
    }


    @Override
    public void tick(@Nullable Entity entity, Level level, @NotNull BlockPos pos) {
        if (level.isClientSide || lastTick == level.getGameTime()) {
            return;
        }
        lastTick = level.getGameTime();
        CookingPotRecipe selectedRecipe = isAutomatic()
                ? getPotRecipeForMeal(settings.getStackInSlot(SELECTED_MEAL), level) : null;
        ItemStack servingContainer = isAutomatic() ? getServingContainer(selectedRecipe, level) : ItemStack.EMPTY;
        CompoundTag beforeServing = isAutomatic() ? inventory.serializeNBT() : null;
        boolean ready = !isAutomatic() || prepareAutomaticInputs(selectedRecipe, servingContainer);
        ItemStack mealStack = inventory.getStackInSlot(MEAL_DISPLAY_SLOT);
        if (!mealStack.isEmpty()) {
            if (isAutomatic() ? servingContainer.isEmpty() : !doesMealHaveContainer(mealStack)) {
                moveMealToOutput();
            } else if (!inventory.getStackInSlot(CONTAINER).isEmpty() && ItemStack.isSameItem(
                    inventory.getStackInSlot(CONTAINER), isAutomatic() ? servingContainer : mealStack.getCraftingRemainingItem())) {
                useStoredContainersOnMeal();
            }
        }

        if (isAutomatic() && !inventory.serializeNBT().equals(beforeServing)) {
            saveState(); // Serving mutates existing stacks as well as setting slots.
        }
        RecipeWrapper inputs = new RecipeWrapper(inventory);
        CookingPotRecipe recipe = isAutomatic() ? selectedRecipe : level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.COOKING.get(), inputs, level).orElse(null);
        if (!ready || recipe == null || !recipe.matches(inputs, level) || !canCook(recipe, level)) {
            if (cookTime != 0 || activeRecipe != null) {
                cookTime = 0;
                totalCookTime = 0;
                activeRecipe = null;
                saveState();
            }
            return;
        }

        if (!recipe.getId().equals(activeRecipe)) {
            cookTime = 0;
            activeRecipe = recipe.getId();
        }

        totalCookTime = Math.max(1, recipe.getCookTime());
        if (++cookTime < totalCookTime) {
            saveState();
            return;
        }

        // Construct the meal before consuming ingredients. The hidden pending stack
        ItemStack pendingMeal = recipe.assemble(inputs, level.registryAccess());
        if (pendingMeal.isEmpty()) {
            cookTime = 0;
            saveState();
            return;
        }
        inventory.insertItem(MEAL_DISPLAY_SLOT, pendingMeal, false);

        for (int slot = 0; slot < MEAL_DISPLAY_SLOT; slot++) {
            ItemStack ingredient = inventory.getStackInSlot(slot);
            if (ingredient.isEmpty()) {
                continue;
            }
            ItemStack remainder = ingredient.hasCraftingRemainingItem() ? ingredient.getCraftingRemainingItem()
                    : CookingPotBlockEntity.INGREDIENT_REMAINDER_OVERRIDES.containsKey(ingredient.getItem())
                    ? CookingPotBlockEntity.INGREDIENT_REMAINDER_OVERRIDES.get(ingredient.getItem()).getDefaultInstance()
                    : ItemStack.EMPTY;
            inventory.extractItem(slot, 1, false);
            if (!remainder.isEmpty()) {
                ItemStack leftover = ItemHandlerHelper.insertItem(storageWrapper.getInventoryHandler(), remainder, false);
                if (!leftover.isEmpty()) {
                    level.addFreshEntity(new ItemEntity(level, pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5, leftover));
                }
            }
        }

        cookTime = 0;
        activeRecipe = null;
        saveState();
    }

    private void saveState() {
        upgrade.addTagElement("PotInventory", inventory.serializeNBT());
        CompoundTag data = new CompoundTag();
        data.putInt("CookTime", cookTime);
        data.putInt("TotalCookTime", totalCookTime);
        if (activeRecipe != null) {
            data.putString("Recipe", activeRecipe.toString());
        }
        upgrade.addTagElement("PotState", data);
        save();
    }
}
