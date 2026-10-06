package com.vomiter.farmersbp.cutting;

import com.vomiter.farmersbp.FarmerSBP;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.InventoryMenu;
import net.p3pp3rf1y.sophisticatedcore.common.gui.SlotSuppliedHandler;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerBase;
import net.p3pp3rf1y.sophisticatedcore.common.gui.UpgradeContainerType;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogic;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogicContainer;

public final class CuttingBoardContainer extends UpgradeContainerBase<CuttingBoardWrapper, CuttingBoardContainer> {
    private final FilterLogicContainer<FilterLogic> inputFilterLogicContainer;
    public static final ResourceLocation KNIFE_SLOT_BACKGROUND = FarmerSBP.modLoc("item/knife_slot");

    public CuttingBoardContainer(Player player, int id, CuttingBoardWrapper wrapper, UpgradeContainerType<CuttingBoardWrapper, CuttingBoardContainer> type) {
        super(player, id, wrapper, type);
        if (!wrapper.isAutomatic()) {
            slots.add(new SlotSuppliedHandler(supplyFromWrapper(CuttingBoardWrapper::getInventory), 0, -100, -100));
        }
        slots.add(
                new SlotSuppliedHandler(
                        supplyFromWrapper(CuttingBoardWrapper::getInventory),
                        1, -100, -100)
                        .setBackground(
                                InventoryMenu.BLOCK_ATLAS,
                                KNIFE_SLOT_BACKGROUND
                        )
        );
        inputFilterLogicContainer = wrapper.isAutomatic()
                ? new FilterLogicContainer<>(supplyFromWrapper(CuttingBoardWrapper::getInputFilterLogic), this, slots::add)
                : null;
    }

    public boolean isAutomatic() {
        return upgradeWrapper.isAutomatic();
    }

    public FilterLogicContainer<FilterLogic> getInputFilterLogicContainer() {
        if (inputFilterLogicContainer == null) {
            throw new IllegalStateException("The manual cutting board has no input filter");
        }
        return inputFilterLogicContainer;
    }

    @Override
    public void handleMessage(CompoundTag data) {
        if (inputFilterLogicContainer != null) {
            inputFilterLogicContainer.handleMessage(data);
        }
        if (!isAutomatic() && data.getBoolean("cut") && player instanceof ServerPlayer serverPlayer) {
            upgradeWrapper.cut(serverPlayer);
        }
    }

    public void requestCut() {
        sendDataToServer(() -> {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("cut", true);
            return tag;
        });
    }
}
