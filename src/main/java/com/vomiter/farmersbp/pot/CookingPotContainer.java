package com.vomiter.farmersbp.pot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.FilterSlotItemHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SlotSuppliedHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogicContainer;
import org.jetbrains.annotations.NotNull;



public final class CookingPotContainer extends UpgradeContainerBase<CookingPotWrapper, CookingPotContainer> {
    private final FilterLogicContainer<FilterLogic> inputFilterLogicContainer;

    public static final ResourceLocation EMPTY_CONTAINER_SLOT_BOWL = ResourceLocation.fromNamespaceAndPath("farmersdelight", "item/empty_container_slot_bowl");

    public CookingPotContainer(Player player, int id, CookingPotWrapper wrapper, UpgradeContainerType<CookingPotWrapper, CookingPotContainer> type) {
        super(player, id, wrapper, type);

        for (int slotIndex = 0; slotIndex < CookingPotWrapper.INVENTORY_SIZE; slotIndex++) {

            if (slotIndex == CookingPotWrapper.MEAL_DISPLAY_SLOT) {
                slots.add(new SlotSuppliedHandler(
                        supplyFromWrapper(CookingPotWrapper::getInventory),
                        slotIndex,
                        -100,
                        -100
                ) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }

                    @Override
                    public boolean mayPickup(Player player) {
                        return false;
                    }
                });
            } else if (wrapper.isAutomatic()
                    && (slotIndex < CookingPotWrapper.MEAL_DISPLAY_SLOT
                        || slotIndex == CookingPotWrapper.CONTAINER)
            ) {
                RestrictedInputSlot slot = getInputSlot(player, slotIndex);
                slot.setIngredient(wrapper.getAssignedIngredient(slotIndex, player.level()));
                if (slotIndex == CookingPotWrapper.CONTAINER) {
                    slot.setBackground(InventoryMenu.BLOCK_ATLAS, EMPTY_CONTAINER_SLOT_BOWL);
                }
                slots.add(slot);
            } else if (slotIndex == CookingPotWrapper.CONTAINER) {
                slots.add(new SlotSuppliedHandler(
                        supplyFromWrapper(CookingPotWrapper::getInventory),
                        slotIndex,
                        -100,
                        -100
                ).setBackground(InventoryMenu.BLOCK_ATLAS, EMPTY_CONTAINER_SLOT_BOWL));
            } else {
                slots.add(new SlotSuppliedHandler(
                        supplyFromWrapper(CookingPotWrapper::getInventory),
                        slotIndex,
                        -100,
                        -100
                ));
            }
        }

        if (wrapper.isAutomatic()) {
            slots.add(
                    new FilterSlotItemHandler(supplyFromWrapper(CookingPotWrapper::getSettings),
                    CookingPotWrapper.SELECTED_MEAL, -100, -100)
            );
            inputFilterLogicContainer
                    = new FilterLogicContainer<>(
                        supplyFromWrapper(CookingPotWrapper::getInputFilterLogic),
                        this,
                        slots::add);
        } else {
            inputFilterLogicContainer = null;
        }
    }

    private @NotNull RestrictedInputSlot getInputSlot(Player player, int slotIndex) {
        return new RestrictedInputSlot(supplyFromWrapper(CookingPotWrapper::getInventory), slotIndex, -100, -100) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                // Wrappers are replaced by menu synchronization; never capture the old wrapper.
                setIngredient(upgradeWrapper.getAssignedIngredient(slotIndex, player.level()));
                return super.mayPlace(stack);
            }
        };
    }

    public FilterLogicContainer<FilterLogic> getInputFilterLogicContainer() {
        if (inputFilterLogicContainer == null) {
            throw new IllegalStateException("The manual cooking pot has no input filter");
        }
        return inputFilterLogicContainer;
    }

    public boolean isAutomatic() {
        return upgradeWrapper.isAutomatic();
    }

    @Override
    public void handleMessage(@NotNull CompoundTag data) {
        if (inputFilterLogicContainer != null) {
            inputFilterLogicContainer.handleMessage(data);
        }
    }

    public double getProgress() {
        int total = upgradeWrapper.getTotalCookTime();
        return total == 0 ? 0 : Math.min(1.0, (double) upgradeWrapper.getCookTime() / total);
    }

    @Override
    public boolean mergeIntoStorageFirst(@NotNull Slot slot) {
        return true;
    }

    @Override
    public boolean allowsPickupAll(@NotNull Slot slot) {
        return true;
    }
}
