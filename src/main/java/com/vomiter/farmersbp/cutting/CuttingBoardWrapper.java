package com.vomiter.farmersbp.cutting;

import com.vomiter.farmersbp.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.p3pp3rf1y.sophisticatedcore.api.IStorageWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.UpgradeWrapperBase;
import net.p3pp3rf1y.sophisticatedcore.util.NBTHelper;
import vectorwing.farmersdelight.common.crafting.CuttingBoardRecipe;
import vectorwing.farmersdelight.common.registry.ModRecipeTypes;
import vectorwing.farmersdelight.common.registry.ModSounds;
import vectorwing.farmersdelight.common.utility.TextUtils;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class CuttingBoardWrapper extends UpgradeWrapperBase<CuttingBoardWrapper, CuttingBoardUpgradeItem> {
    private final ItemStackHandler inventory;

    public enum CuttingResult{
        EMPTY,
        INVALID_ITEM,
        INVALID_TOOL
    }

    private static CuttingResult cuttingResult = CuttingResult.EMPTY;
    public static CuttingResult getCuttingResult() {
        return cuttingResult;
    }

    public static void setCuttingResult(CuttingResult cuttingResult) {
        CuttingBoardWrapper.cuttingResult = cuttingResult;
    }

    static Map<CuttingResult, Component> messages = new EnumMap<>(CuttingResult.class);
    static {
        messages.put(CuttingResult.EMPTY, Component.empty());
        messages.put(CuttingResult.INVALID_ITEM, TextUtils.block("cutting_board.invalid_item"));
        messages.put(CuttingResult.INVALID_TOOL, TextUtils.block("cutting_board.invalid_tool"));
    }

    public CuttingBoardWrapper(IStorageWrapper storage, ItemStack upgrade, Consumer<ItemStack> saveHandler) {
        super(storage, upgrade, saveHandler);
        inventory = new ItemStackHandler(2) {
            @Override
            protected void onContentsChanged(int slot) {
                CuttingBoardWrapper.setCuttingResult(CuttingResult.EMPTY);
                upgrade.addTagElement("CuttingInventory", serializeNBT());
                save();
            }
        };
        NBTHelper.getCompound(upgrade, "CuttingInventory").ifPresent(inventory::deserializeNBT);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public boolean canBeDisabled() {
        return false;
    }

    public void cut(ServerPlayer player) {
        ItemStack input = inventory.getStackInSlot(0);
        ItemStack tool = inventory.getStackInSlot(1);
        if (input.isEmpty() || tool.isEmpty()) {
            return;
        }
        RecipeWrapper inputs = new RecipeWrapper(inventory);
        List<CuttingBoardRecipe> recipes = player.level().getRecipeManager().getRecipesFor(ModRecipeTypes.CUTTING.get(), inputs, player.level());
        if (recipes.isEmpty()){
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ModNetwork.FBPCuttingResponse(CuttingResult.INVALID_ITEM.ordinal())
            );
            return;
        }
        var recipe = recipes.stream().filter((cuttingRecipe) -> cuttingRecipe.getTool().test(tool)).findFirst().orElse(null);
        if (recipe == null) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new ModNetwork.FBPCuttingResponse(CuttingResult.INVALID_TOOL.ordinal())
            );
            return;
        }

        int fortune = EnchantmentHelper.getTagEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool);
        List<ItemStack> results = recipe.rollResults(player.level().random, fortune, inputs);
        inventory.extractItem(0, 1, false);
        for (ItemStack result : results) {
            ItemStack leftover = ItemHandlerHelper.insertItem(storageWrapper.getInventoryHandler(), result.copy(), false);
            if (!leftover.isEmpty()) {
                player.drop(leftover, false);
            }
        }

        if (!player.getAbilities().instabuild){
            ItemStack damaged = tool.copy();
            if (damaged.hurt(1, player.level().random, player)) {
                damaged.shrink(1);
            }
            inventory.setStackInSlot(1, damaged);
        }

        SoundEvent sound = ModSounds.BLOCK_CUTTING_BOARD_KNIFE.get();
        String soundId = recipe.getSoundEventID();
        if (!soundId.isEmpty()) {
            ResourceLocation key = ResourceLocation.tryParse(soundId);
            SoundEvent custom = key == null ? null : ForgeRegistries.SOUND_EVENTS.getValue(key);
            if (custom != null) {
                sound = custom;
            }
        }
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.8F, 1.0F);
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new ModNetwork.FBPCuttingResponse(CuttingResult.EMPTY.ordinal())
        );
    }
}
