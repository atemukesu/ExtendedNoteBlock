package com.atemukesu.extendednoteblock.item;

import com.atemukesu.extendednoteblock.network.ModMessages;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import org.jetbrains.annotations.Nullable;

public class ConductorWandItem extends Item {
    public ConductorWandItem() {
        super(new Item.Settings().maxCount(1));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, java.util.List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.extendednoteblock.conductor_wand.tooltip1"));
        tooltip.add(Text.translatable("item.extendednoteblock.conductor_wand.tooltip2"));
        tooltip.add(Text.translatable("item.extendednoteblock.conductor_wand.tooltip3"));
        super.appendTooltip(stack, context, tooltip, type);
    }

    // 左键点击方块 (BlockBreak事件前) - 设置点1
    // 注意：Fabric Item类没有直接的onLeftClickBlock，通常通过AttackBlockCallback事件处理，
    // 或者利用 canMine 返回 false 并在里面处理逻辑 (虽然hacky但常用)。
    // 这里假设你在模组主类注册了 AttackBlockCallback.EVENT.register(...) 来调用此逻辑。
    @Environment(EnvType.CLIENT)
    public void onLeftClick(PlayerEntity player, BlockPos pos) {
        ItemStack stack = player.getMainHandStack();
        if (stack.getItem() == this) {
            NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            nbt.put("Pos1", NbtHelper.fromBlockPos(pos));
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
            player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.pos_set", 1, pos.toShortString()),
                    true);
        }
    }

    // Right Click Block - Set Pos2
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        PlayerEntity player = context.getPlayer();
        ItemStack stack = context.getStack();

        // 1. Always set Pos2 on Client for immediate visual feedback
        // 2. Server will handle the actual logic storage/sync via standard item sync or
        // packets if needed,
        // but here we also set it on Server side for persistence.
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        nbt.put("Pos2", NbtHelper.fromBlockPos(pos));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));

        if (world.isClient) {
            player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.pos_set", 2, pos.toShortString()),
                    true);
        }

        return ActionResult.SUCCESS;
    }

    // Remove direct GUI opening from Item use
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        return super.use(world, player, hand);
    }

    // 抽取打开GUI的逻辑
    public void openGui(PlayerEntity player, ItemStack stack) {
        NbtCompound nbt = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (nbt.contains("Pos1") && nbt.contains("Pos2")) {
            BlockPos p1 = NbtHelper.toBlockPos(nbt, "Pos1").orElse(null);
            BlockPos p2 = NbtHelper.toBlockPos(nbt, "Pos2").orElse(null);
            ModMessages.sendScanRequest((ServerPlayerEntity) player, p1, p2);
        } else {
            player.sendMessage(Text.translatable("gui.extendednoteblock.conductor.incomplete"), false);
        }
    }
}
