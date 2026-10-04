package com.vomiter.farmersbp.pot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.ITickableUpgrade;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import org.jetbrains.annotations.NotNull;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;
import vectorwing.farmersdelight.common.crafting.CookingPotRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;

import javax.annotation.Nullable;
import java.util.function.Consumer;

public final class CookingPotWrapper extends UpgradeWrapperBase<CookingPotWrapper, CookingPotUpgradeItem>
        implements ITickableUpgrade {
    static final int MEAL_DISPLAY_SLOT = 6;
    static final int CONTAINER = 7;
    static final int OUTPUT = 8;
    static final int INVENTORY_SIZE = 9;
    private final ItemStackHandler inventory;
    private ResourceLocation activeRecipe;
    private int cookTime;
    private int totalCookTime;
    private long lastTick = Long.MIN_VALUE;
    private static boolean shouldRenderOriginalTooltip = true;

    public CookingPotWrapper(IStorageWrapper storage, ItemStack upgrade, Consumer<ItemStack> saveHandler) {
        super(storage, upgrade, saveHandler);
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
        ItemStack mealStack = inventory.getStackInSlot(MEAL_DISPLAY_SLOT);
        if (!mealStack.isEmpty()) {
            if (!doesMealHaveContainer(mealStack)) {
                moveMealToOutput();
            } else if (!inventory.getStackInSlot(CONTAINER).isEmpty() && ItemStack.isSameItem(inventory.getStackInSlot(CONTAINER), mealStack.getCraftingRemainingItem())) {
                useStoredContainersOnMeal();
            }
        }

        lastTick = level.getGameTime();
        RecipeWrapper inputs = new RecipeWrapper(inventory);
        CookingPotRecipe recipe = level.getRecipeManager()
                .getRecipeFor(ModRecipeTypes.COOKING.get(), inputs, level).orElse(null);
        if (recipe == null || !canCook(recipe, level)) {
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
