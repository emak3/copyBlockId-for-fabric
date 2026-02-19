package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashSet;
import java.util.Set;

public class CopyBlockIdClient implements ClientModInitializer {

    private final Set<String> tempBlockIds = new LinkedHashSet<>();
    private boolean wasShiftPressed = false;

    @Override
    public void onInitializeClient() {

        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            if (player.getItemInHand(hand).getItem() != Items.PAPER) return InteractionResult.PASS;

            Block block = level.getBlockState(hitResult.getBlockPos()).getBlock();
            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();

            if (player.isShiftKeyDown()) {
                if (!wasShiftPressed) {
                    tempBlockIds.clear();
                }
                tempBlockIds.add(blockId);

                String recording = String.join(",", tempBlockIds);
                player.displayClientMessage(
                    Component.translatable("copy_block_id.message.recording").append(recording),
                    true
                );
            }

            return InteractionResult.SUCCESS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            boolean shiftPressed = client.player.isShiftKeyDown();

            if (wasShiftPressed && !shiftPressed && !tempBlockIds.isEmpty()) {
                String content = String.join(",", tempBlockIds);

                // リフレクション不要。Minecraftの公式APIでクリップボードに書き込む
                Minecraft.getInstance().keyboardHandler.setClipboard(content);

                client.player.displayClientMessage(
                    Component.translatable("copy_block_id.message.copied").append(content),
                    true
                );
                tempBlockIds.clear();
            }

            wasShiftPressed = shiftPressed;
        });
    }
}