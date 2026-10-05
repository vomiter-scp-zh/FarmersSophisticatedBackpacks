package com.vomiter.farmersbp.pot;

import com.vomiter.farmersbp.mixin.SlotAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.p3pp3rf1y.sophisticatedbackpacks.Config;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import net.p3pp3rf1y.sophisticatedcore.upgrades.FilterLogicControl;
import org.jetbrains.annotations.NotNull;
import vectorwing.farmersdelight.common.utility.TextUtils;

import java.util.List;

public final class CookingPotTab extends UpgradeSettingsTab<CookingPotContainer> {
    private final FilterLogicControl.Advanced inputFilterLogicControl;

    private static final ResourceLocation POT_BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath("farmersdelight", "textures/gui/cooking_pot.png");
    private static Component getLabelComponent(CookingPotContainer container){
        return Component.translatable(container.isAutomatic() ? "gui.farmersbp.automatic_cooking_pot" : "gui.farmersbp.cooking_pot");
    }

    public CookingPotTab(CookingPotContainer container, Position position, StorageScreenBase<?> screen) {
        super(container, position, screen, getLabelComponent(container), getLabelComponent(container));
        if (container.isAutomatic()) {
            inputFilterLogicControl = addHideableChild(
                    new FilterLogicControl.Advanced(screen,
                    new Position(x + 3, y + 48), container.getInputFilterLogicContainer(),
                    Config.SERVER.autoSmeltingUpgrade.inputFilterSlotsInRow.get())
            );
            inputFilterLogicControl.setDimensionsChangedHandler(() -> {
                updateAutomaticTabDimension();
                if (getContainer().isOpen()) {
                    moveSlotsToTab();
                }
            });
            updateAutomaticTabDimension();
        } else {
            inputFilterLogicControl = null;
            openTabDimension = new Dimension(64, 126);
        }
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, @NotNull Minecraft minecraft, int mouseX, int mouseY) {
        super.renderBg(graphics, minecraft, mouseX, mouseY);
        if (getContainer().isOpen()) {
            int offset = getCookingOffset();
            if (getContainer().isAutomatic()) {
                GuiHelper.renderSlotsBackground(graphics, x + 4, y + 25, 1, 1);
                graphics.drawString(minecraft.font, Component.translatable("gui.farmersbp.selected_meal"), x + 25, y + 30, 0x404040, false);
            }
            GuiHelper.renderSlotsBackground(graphics, x + 4, y + 25 + offset, 3, 2);

            GuiHelper.renderSlotsBackground(graphics, x + 36, y + 71 + offset, 1, 1);
            GuiHelper.renderSlotsBackground(graphics, x + 4, y + 92 + offset, 1, 1);
            GuiHelper.renderSlotsBackground(graphics, x + 36, y + 92 + offset, 1, 1);

            graphics.blit(POT_BACKGROUND_TEXTURE, this.x + 4, this.y + 71 + offset, 89, 25, 25, 17);
            graphics.blit(POT_BACKGROUND_TEXTURE, this.x + 4, this.y + 71 + offset, 176, 15, (int) (24 * getContainer().getProgress() + 1), 17);
            graphics.blit(POT_BACKGROUND_TEXTURE, this.x + 23, this.y + 92 + offset, 110, 55, 12, 17);
        }
    }

    @Override
    protected void moveSlotsToTab() {
        int offset = getCookingOffset();
        if (getContainer().isAutomatic()) {
            moveSlot(getContainer().getSlots().get(CookingPotWrapper.INVENTORY_SIZE), x + 5, y + 26);
            inputFilterLogicControl.moveSlotsToView();
        }
        for (int i = 0; i < 6; i++) {
            Slot ingredient = getContainer().getSlots().get(i);
            if (ingredient instanceof SlotAccess ingAcc){
                ingAcc.setX(x + 5 + (i % 3) * 18 - screen.getGuiLeft());
                ingAcc.setY(y + 26 + offset + (i / 3) * 18 - screen.getGuiTop());
            }
        }
        Slot display = getContainer().getSlots().get(CookingPotWrapper.MEAL_DISPLAY_SLOT);

        if (display instanceof SlotAccess displayAcc){
            displayAcc.setX(x + 37 - screen.getGuiLeft());
            displayAcc.setY(y + 72 + offset - screen.getGuiTop());
        }

        Slot container = getContainer().getSlots().get(CookingPotWrapper.CONTAINER);
        if (container instanceof SlotAccess conAcc){
            conAcc.setX(x + 5 - screen.getGuiLeft());
            conAcc.setY(y + 93 + offset - screen.getGuiTop());
        }

        Slot output = getContainer().getSlots().get(CookingPotWrapper.OUTPUT);
        if (output instanceof SlotAccess outAcc){
            outAcc.setX(x + 37 - screen.getGuiLeft());
            outAcc.setY(y + 93 + offset - screen.getGuiTop());
        }
    }

    private int getCookingOffset() {
        return inputFilterLogicControl == null ? 0 : 48 + inputFilterLogicControl.getHeight() + 4 - 25;
    }

    private void updateAutomaticTabDimension() {
        openTabDimension = new Dimension(Math.max(82, inputFilterLogicControl.getWidth() + 9),
                126 + getCookingOffset());
        if (getContainer().isOpen()) {
            setWidth(openTabDimension.width());
            setHeight(openTabDimension.height());
        }
    }

    private void moveSlot(Slot slot, int slotX, int slotY) {
        if (slot instanceof SlotAccess access) {
            access.setX(slotX - screen.getGuiLeft());
            access.setY(slotY - screen.getGuiTop());
        }
    }

    @Override
    public void renderTooltip(@NotNull Screen tooltipScreen, @NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(tooltipScreen, graphics, mouseX, mouseY);
        if (!getContainer().isOpen() || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        if (getContainer().isAutomatic()) {
            Slot selectedMealSlot = getContainer().getSlots().get(CookingPotWrapper.INVENTORY_SIZE);
            if (screen.isMouseOverSlot(selectedMealSlot, mouseX, mouseY)) {
                CookingPotWrapper.setShouldRenderOriginalTooltip(false);
                GuiHelper.renderTooltip(tooltipScreen, graphics, List.of(Component.translatable(
                        "gui.farmersbp.selected_meal.tooltip")), mouseX, mouseY);
                return;
            }
        }
        Slot displaySlot = getContainer().getSlots().get(CookingPotWrapper.MEAL_DISPLAY_SLOT);
        if (screen.isMouseOverSlot(displaySlot, mouseX, mouseY)){
            CookingPotWrapper.setShouldRenderOriginalTooltip(false);
            if (displaySlot.hasItem() && displaySlot.isActive()) {
                ItemStack mealStack = displaySlot.getItem();
                ItemStack containerStack = mealStack.getCraftingRemainingItem();
                GuiHelper.renderTooltip(tooltipScreen, graphics, List.of(
                        ((MutableComponent)mealStack.getHoverName()).withStyle(mealStack.getRarity().getStyleModifier()),
                        TextUtils.container("cooking_pot.served_on", containerStack.getHoverName()).withStyle(ChatFormatting.GRAY)
                ), mouseX, mouseY);
            }
        } else {
            CookingPotWrapper.setShouldRenderOriginalTooltip(true);
        }
    }
}
