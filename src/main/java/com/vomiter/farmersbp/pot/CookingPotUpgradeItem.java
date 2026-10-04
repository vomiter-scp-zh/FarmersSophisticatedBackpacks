package com.vomiter.farmersbp.pot;

import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CookingPotUpgradeItem extends UpgradeItemBase<CookingPotWrapper> {
    private static final UpgradeType<CookingPotWrapper> TYPE = new UpgradeType<>(CookingPotWrapper::new);

    public CookingPotUpgradeItem(IUpgradeCountLimitConfig limits) {
        super(limits);
    }

    @Override
    public @NotNull UpgradeType<CookingPotWrapper> getType() {
        return TYPE;
    }

    @Override
    public @NotNull List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of();
    }
}
