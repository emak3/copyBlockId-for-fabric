package com.example;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

public class CopyBlockIdClient implements ClientModInitializer {
    // シフトキーが押されている間に表示するためのリスト
    private final Set<String> tempBlockIds = new HashSet<>();
    // シフトキーが押されているかどうかを追跡
    private boolean isShiftKeyPressed = false;
    // ウィンドウハンドルをキャッシュ
    private long cachedWindowHandle = 0;

    @Override
    public void onInitializeClient() {
        // ブロック右クリックイベントの登録
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            // クライアント側であることを確認
            if (level.isClientSide()) {
                // プレイヤーが紙を持っているかチェック
                ItemStack heldItem = player.getItemInHand(hand);

                if (heldItem.getItem() == Items.PAPER) {
                    // ブロックの情報を取得
                    Block block = level.getBlockState(hitResult.getBlockPos()).getBlock();
                    // ブロックのレジストリ名（ID）を取得 - varを使用して型に依存しない
                    var blockId = BuiltInRegistries.BLOCK.getKey(block);
                    String blockIdStr = blockId.toString();

                    // シフトキーが押されている場合の処理
                    if (player.isShiftKeyDown()) {
                        if (!isShiftKeyPressed) {
                            // シフトキーが押されたらテンポラリリストをクリア
                            isShiftKeyPressed = true;
                            tempBlockIds.clear();
                        }

                        // 一時リストにブロックIDを追加（重複は自動的に排除される）
                        tempBlockIds.add(blockIdStr);

                        // カンマ区切りで表示するための準備
                        StringJoiner joiner = new StringJoiner(",");
                        for (String id : tempBlockIds) {
                            joiner.add(id);
                        }
                        Component message = Component.translatable("copy_block_id.message.recording");
                        Component recordingContext = Component.empty().append(message).append(joiner.toString());
                        // 現在の一時リストを表示
                        player.displayClientMessage(recordingContext, true);
                    }

                    // ブロックの配置や操作をキャンセル
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });

        // クライアントティックイベントでシフトキーの状態を監視
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                // ウィンドウハンドルを取得（キャッシュを使用）
                if (cachedWindowHandle == 0) {
                    cachedWindowHandle = getWindowHandle(client.getWindow());
                }
                
                if (cachedWindowHandle == 0) {
                    // ウィンドウハンドルが取得できない場合はスキップ
                    return;
                }
                
                // シフトキーの状態を確認（左シフトと右シフト）
                boolean leftShiftPressed = GLFW.glfwGetKey(cachedWindowHandle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS;
                boolean rightShiftPressed = GLFW.glfwGetKey(cachedWindowHandle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                boolean currentShiftPressed = leftShiftPressed || rightShiftPressed;

                // シフトキーが離されたかを確認
                if (isShiftKeyPressed && !currentShiftPressed) {
                    isShiftKeyPressed = false;

                    // シフトキーが離されたときに集めたブロックIDをチャットに表示
                    if (!tempBlockIds.isEmpty()) {
                        StringJoiner joiner = new StringJoiner(",");
                        for (String id : tempBlockIds) {
                            joiner.add(id);
                        }

                        final String content = joiner.toString();

                        // GLFWを使ってクリップボードにコピーする
                        copyToClipboardWithGLFW(cachedWindowHandle, content);

                        Component message = Component.translatable("copy_block_id.message.copied");
                        Component copiedContext = Component.empty().append(message).append(content);

                        client.player.displayClientMessage(copiedContext, true);

                        tempBlockIds.clear();
                    }
                }
            }
        });
    }

    // Windowからハンドルを取得するヘルパーメソッド（リフレクション使用）
    private long getWindowHandle(com.mojang.blaze3d.platform.Window window) {
        try {
            // まずpublicメソッドを試す
            try {
                var method = window.getClass().getMethod("getHandle");
                return (long) method.invoke(window);
            } catch (NoSuchMethodException e) {
                try {
                    var method = window.getClass().getMethod("getWindow");
                    return (long) method.invoke(window);
                } catch (NoSuchMethodException ex) {
                    // メソッドがない場合はフィールドから取得
                    Field windowField = null;
                    // すべてのフィールドを調べる
                    for (Field field : window.getClass().getDeclaredFields()) {
                        if (field.getType() == long.class) {
                            field.setAccessible(true);
                            long value = field.getLong(window);
                            // 0以外の値を持つlongフィールドを探す
                            if (value != 0) {
                                System.out.println("ウィンドウハンドルをフィールド " + field.getName() + " から取得しました");
                                return value;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("ウィンドウハンドルの取得に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    // GLFWを使ってクリップボードにコピーするメソッド
    private void copyToClipboardWithGLFW(long handle, String content) {
        try {
            if (handle != 0) {
                GLFW.glfwSetClipboardString(handle, content);
                System.out.println("GLFWを使用してクリップボードにテキストをコピーしました");
            } else {
                System.err.println("GLFWウィンドウハンドルが無効です");
            }
        } catch (Exception e) {
            System.err.println("GLFWクリップボード操作に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }
}