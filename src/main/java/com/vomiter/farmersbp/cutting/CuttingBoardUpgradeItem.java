package com.vomiter.farmersbp.cutting;

import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CuttingBoardUpgradeItem extends UpgradeItemBase<CuttingBoardWrapper> {
    private static final UpgradeType<CuttingBoardWrapper> TYPE = new UpgradeType<>(CuttingBoardWrapper::new);

    public CuttingBoardUpgradeItem(IUpgradeCountLimitConfig limits) {
        super(limits);
    }

    @Override
    public @NotNull UpgradeType<CuttingBoardWrapper> getType() {
        return TYPE;
    }

    @Override
    public @NotNull List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }
}
