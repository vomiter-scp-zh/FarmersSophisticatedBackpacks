package com.vomiter.farmersbp.cutting;

import com.vomiter.farmersbp.mixin.SlotAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.p3pp3rf1y.sophisticatedcore.client.gui.StorageScreenBase;
import net.p3pp3rf1y.sophisticatedcore.client.gui.UpgradeSettingsTab;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.Button;
import net.p3pp3rf1y.sophisticatedcore.client.gui.controls.ButtonDefinition;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Dimension;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.GuiHelper;
import net.p3pp3rf1y.sophisticatedcore.client.gui.utils.Position;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class CuttingBoardTab extends UpgradeSettingsTab<CuttingBoardContainer> {
    public CuttingBoardTab(CuttingBoardContainer container, Position position, StorageScreenBase<?> screen) {
        super(container, position, screen, Component.translatable("gui.farmersbp.cutting_board"),
                Component.translatable("gui.farmersbp.cutting_board"));
        openTabDimension = new Dimension(60, 92);
        CuttingBoardWrapper.setCuttingResult(CuttingBoardWrapper.CuttingResult.EMPTY);
        ButtonDefinition button = new ButtonDefinition(
                new Dimension(46, 18),
                null,
                null,
                null,
                Component.translatable("gui.farmersbp.cut.tooltip")
        );

        addHideableChild(new Button(new Position(x + 4, y + 62), button, mouseButton -> {
            if (mouseButton == 0) {
                getContainer().requestCut();
            }
        }) {
            @Override
            protected @NotNull List<Component> getTooltip() {
                CuttingBoardWrapper.CuttingResult result = CuttingBoardWrapper.getCuttingResult();

                if (result == CuttingBoardWrapper.CuttingResult.EMPTY) {
                    return List.of(Component.translatable("gui.farmersbp.cut.tooltip"));
                }

                return List.of(CuttingBoardWrapper.messages.get(result));
            }

            @Override
            protected void renderBg(@NotNull GuiGraphics graphics, @NotNull Minecraft minecraft,
                                    int mouseX, int mouseY) {
                boolean hovered = isMouseOver(mouseX, mouseY);

                int shadow = 0xFF373737;
                int background = hovered ? 0xFF7E7E7E : 0xFF666666;
                int highlight = hovered ? 0xFFFFFFFF : 0xFFA0A0A0;

                graphics.fill(
                        x, y,
                        x + getWidth(), y + getHeight(),
                        0xFF000000
                );

                graphics.fill(
                        x + 1, y + 1,
                        x + getWidth() - 1, y + getHeight() - 1,
                        background
                );

                graphics.fill(
                        x + 1, y + 1,
                        x + getWidth() - 1, y + 2,
                        highlight
                );
                graphics.fill(
                        x + 1, y + 1,
                        x + 2, y + getHeight() - 1,
                        highlight
                );

                graphics.fill(
                        x + 1, y + getHeight() - 2,
                        x + getWidth() - 1, y + getHeight() - 1,
                        shadow
                );
                graphics.fill(
                        x + getWidth() - 2, y + 1,
                        x + getWidth() - 1, y + getHeight() - 1,
                        shadow
                );
            }

            @Override
            protected void renderWidget(@NotNull GuiGraphics graphics, int mouseX,
                                        int mouseY, float partialTicks) {
                graphics.drawCenteredString(
                        Minecraft.getInstance().font,
                        Component.translatable("gui.farmersbp.cut"),
                        x + getWidth() / 2,
                        y + 5,
                        0xFFFFFF
                );
            }
        });
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, @NotNull Minecraft minecraft, int mouseX, int mouseY) {
        super.renderBg(graphics, minecraft, mouseX, mouseY);

        if (getContainer().isOpen()) {
            GuiHelper.renderSlotsBackground(graphics, x + 4, y + 30, 1, 1);
            GuiHelper.renderSlotsBackground(graphics, x + 36, y + 30, 1, 1);
        }
    }

    @Override
    protected void moveSlotsToTab() {
        Slot input = getContainer().getSlots().get(0);
        Slot knife = getContainer().getSlots().get(1);
        if (input instanceof SlotAccess inAcc){
            inAcc.setX(x + 5 - screen.getGuiLeft());
            inAcc.setY(y + 31 - screen.getGuiTop());
        }
        if (knife instanceof SlotAccess knifeAcc){
            knifeAcc.setX(x + 37 - screen.getGuiLeft());
            knifeAcc.setY(y + 31 - screen.getGuiTop());
        }
    }
}
