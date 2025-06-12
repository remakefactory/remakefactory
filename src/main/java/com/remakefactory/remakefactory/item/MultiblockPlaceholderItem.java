package com.remakefactory.remakefactory.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MultiblockPlaceholderItem extends Item {

    public static final String NBT_KEY_MULTIBLOCK = "multiblock";
    public static final String NBT_KEY_ID = "id";
    public static final String NBT_KEY_INDEX = "index";

    public MultiblockPlaceholderItem(Properties pProperties) {
        super(pProperties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level pLevel, @NotNull Player pPlayer, @NotNull InteractionHand pUsedHand) {
        ItemStack heldStack = pPlayer.getItemInHand(pUsedHand);
        if (pPlayer.isShiftKeyDown()) {
            if (!pLevel.isClientSide()) {
                CompoundTag rootTag = heldStack.getTag();
                if (rootTag != null && rootTag.contains(NBT_KEY_MULTIBLOCK)) {
                    rootTag.remove(NBT_KEY_MULTIBLOCK);
                    if (rootTag.isEmpty()) {
                        heldStack.setTag(null);
                    }
                    pPlayer.displayClientMessage(Component.translatable("message.remakefactory.placeholder_cleared"), true);
                    pLevel.playSound(null, pPlayer.getX(), pPlayer.getY(), pPlayer.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.0F);
                    return InteractionResultHolder.success(heldStack);
                }
            }
            return InteractionResultHolder.success(heldStack);
        }
        return super.use(pLevel, pPlayer, pUsedHand);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack pStack) {
        ItemStack targetStack = getTargetStack(pStack);
        if (targetStack != null) {
            return targetStack.getHoverName();
        }
        return super.getName(pStack);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack pStack) {
        ItemStack targetStack = getTargetStack(pStack);
        if (targetStack != null) {
            return targetStack.getItem().isFoil(targetStack);
        }
        return super.isFoil(pStack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack pStack, @Nullable Level pLevel, @NotNull List<Component> pTooltipComponents, @NotNull TooltipFlag pIsAdvanced) {
        super.appendHoverText(pStack, pLevel, pTooltipComponents, pIsAdvanced);
        if (pStack.hasTag()) {
            CompoundTag rootTag = pStack.getTag();
            if (rootTag != null && rootTag.contains(NBT_KEY_MULTIBLOCK, Tag.TAG_COMPOUND)) {
                CompoundTag multiblockTag = rootTag.getCompound(NBT_KEY_MULTIBLOCK);
                if (multiblockTag.contains(NBT_KEY_INDEX, Tag.TAG_INT)) {
                    int shapeIndex = multiblockTag.getInt(NBT_KEY_INDEX);
                    pTooltipComponents.add(
                            Component.translatable("tooltip.remakefactory.placeholder.shape", shapeIndex)
                                    .withStyle(ChatFormatting.GRAY)
                    );
                }
            }
        }
    }

    @Nullable
    public static ItemStack getTargetStack(ItemStack placeholderStack) {
        if (placeholderStack.hasTag()) {
            CompoundTag rootTag = placeholderStack.getTag();
            if (rootTag != null && rootTag.contains(NBT_KEY_MULTIBLOCK, Tag.TAG_COMPOUND)) {
                CompoundTag multiblockTag = rootTag.getCompound(NBT_KEY_MULTIBLOCK);
                if (multiblockTag.contains(NBT_KEY_ID, Tag.TAG_STRING)) {
                    try {
                        ResourceLocation multiblockId = ResourceLocation.tryParse(multiblockTag.getString(NBT_KEY_ID));
                        if (multiblockId != null) {
                            Item controllerItem = ForgeRegistries.ITEMS.getValue(multiblockId);
                            if (controllerItem != null && controllerItem != Items.AIR) {
                                return new ItemStack(controllerItem);
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }
}