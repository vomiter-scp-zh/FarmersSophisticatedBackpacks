package com.vomiter.farmersbp.cutting;

import net.minecraft.network.chat.Component;
import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CuttingBoardUpgradeItem extends UpgradeItemBase<CuttingBoardWrapper> {
    private final boolean automatic;
    private static final UpgradeType<CuttingBoardWrapper> TYPE = new UpgradeType<>(CuttingBoardWrapper::new);

    public CuttingBoardUpgradeItem(IUpgradeCountLimitConfig limits) {
        this(limits, false);
    }

    public CuttingBoardUpgradeItem(IUpgradeCountLimitConfig limits, boolean automatic) {
        super(limits);
        this.automatic = automatic;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    @Override
    public @NotNull UpgradeType<CuttingBoardWrapper> getType() {
        return TYPE;
    }

    @Override
    public @NotNull List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of(new UpgradeConflictDefinition(CuttingBoardUpgradeItem.class::isInstance, 0,
                Component.translatable("error.farmersbp.cutting_board_exists")));
    }
}
