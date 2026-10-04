package com.vomiter.farmersbp.pot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SlotSuppliedHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import org.jetbrains.annotations.NotNull;

public final class CookingPotContainer extends UpgradeContainerBase<CookingPotWrapper, CookingPotContainer> {
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
    }

    @Override
    public void handleMessage(@NotNull CompoundTag data) {
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
