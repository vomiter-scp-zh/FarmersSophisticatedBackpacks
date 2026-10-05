package com.vomiter.farmersbp.pot;

import net.p3pp3rf1y.sophisticatedcore.upgrades.IUpgradeCountLimitConfig;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeItemBase;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeType;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import net.minecraft.network.chat.Component;

public final class CookingPotUpgradeItem extends UpgradeItemBase<CookingPotWrapper> {
    private final boolean automatic;
    private static final UpgradeType<CookingPotWrapper> TYPE = new UpgradeType<>(CookingPotWrapper::new);

    public CookingPotUpgradeItem(IUpgradeCountLimitConfig limits) {
        this(limits, false);
    }

    public CookingPotUpgradeItem(IUpgradeCountLimitConfig limits, boolean automatic) {
        super(limits);
        this.automatic = automatic;
    }

    public boolean isAutomatic() {
        return automatic;
    }

    @Override
    public @NotNull UpgradeType<CookingPotWrapper> getType() {
        return TYPE;
    }

    @Override
    public @NotNull List<UpgradeConflictDefinition> getUpgradeConflicts() {
        return List.of(new UpgradeConflictDefinition(CookingPotUpgradeItem.class::isInstance, 0,
                Component.translatable("error.farmersbp.cooking_pot_exists")));
    }
}
