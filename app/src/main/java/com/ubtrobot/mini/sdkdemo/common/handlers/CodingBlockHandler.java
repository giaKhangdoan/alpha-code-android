package com.ubtrobot.mini.sdkdemo.common.handlers;

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.util.Log;

import com.ubtrobot.commons.ResponseListener;
import com.ubtrobot.express.listeners.AnimationListener;
import com.ubtrobot.led.LedApi;
import com.ubtrobot.lib.mouthledapi.MouthLedApi;
import com.ubtrobot.mini.sdkdemo.custom.tts.TTSCallback;
import com.ubtrobot.mini.sdkdemo.models.response.OsmoCardAction;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Queue;

public class CodingBlockHandler {
    private static final String TAG = "CodingBlockHandler";

    private final ActionHandler actionHandler;
    private final ExpressionHandler expressionHandler;
    private final TTSHandler ttsHandler;
    private final ExtendedActionHandler extendedActionHandler;

    private final Queue<JSONObject> blockQueue = new LinkedList<>();
    private CodingBlockCallback currentCallback;
    private String currentLang;
    private boolean isExecuting = false;

    public interface CodingBlockCallback {
        void onBlockCodingStart();

        void onBlockCodingEnd();
    }

    public CodingBlockHandler() {
        this.actionHandler = new ActionHandler();
        this.expressionHandler = new ExpressionHandler();
        this.ttsHandler = TTSHandler.getInstance();
        this.extendedActionHandler = new ExtendedActionHandler();
    }

    /**
     * Enqueues a single coding block for execution
     */
    public void enqueueBlock(JSONObject block, String lang, CodingBlockCallback callback) {
        if (block == null) {
            Log.w(TAG, "Attempted to enqueue null block");
            return;
        }

        this.currentLang = lang;
        this.currentCallback = callback;

        blockQueue.offer(block);
        Log.i(TAG, "Block enqueued, queue size: " + blockQueue.size());

        // Start execution if not already running
        if (!isExecuting) {
            executeNextBlock();
        }
    }

    /**
     * Enqueues multiple coding blocks for execution
     */
    public void enqueueBlocks(JSONArray blocks, String lang, CodingBlockCallback callback) {
        if (blocks == null || blocks.length() == 0) {
            Log.w(TAG, "Attempted to enqueue empty blocks array");
            return;
        }

        this.currentLang = lang;
        this.currentCallback = callback;

        // Add all blocks to queue
        for (int i = 0; i < blocks.length(); i++) {
            JSONObject block = blocks.optJSONObject(i);
            if (block != null) {
                blockQueue.offer(block);
            }
        }

        Log.i(TAG, blocks.length() + " blocks enqueued, queue size: " + blockQueue.size());

        // Start execution if not already running
        if (!isExecuting) {
            executeNextBlock();
        }
    }

    /**
     * Executes the next block in the queue
     */
    private void executeNextBlock() {
        if (blockQueue.isEmpty()) {
            // Queue is empty, stop execution
            isExecuting = false;
            if (currentCallback != null) {
                currentCallback.onBlockCodingEnd();
            }
            return;
        }

        isExecuting = true;
        JSONObject block = blockQueue.poll();

        if (block == null) {
            // Skip null blocks and try next
            executeNextBlock();
            return;
        }

        // Notify start on first block only
        if (blockQueue.isEmpty()) { // This was the last block before we started
            currentCallback.onBlockCodingStart();
        }

        String type = block.optString("type");
        String code = block.optString("code");
        String text = block.optString("text");
        String lang = block.optString("lang", currentLang);

        Log.i(TAG, "Executing block type: " + type + ", remaining: " + blockQueue.size());

        switch (type) {
            case "tts":
                handleTTSBlock(text, lang, this::executeNextBlock);
                break;

            case "expression":
                handleExpressionBlock(code, this::executeNextBlock);
                break;

            case "action":
                handleActionBlock(code, this::executeNextBlock);
                break;

            case "extended_action":
                handleExtendedActionBlock(block, this::executeNextBlock);
                break;

            case "led":
                handleLedBlock(block, this::executeNextBlock);
                break;

            default:
                Log.w(TAG, "Unknown coding block type: " + type);
                executeNextBlock(); // Skip unknown blocks
                break;
        }
    }

    private void handleLedBlock(JSONObject data, Runnable onComplete) {
        // Placeholder for LED handling logic
        Log.i(TAG, "Handling LED block: " + data);
        JSONObject c = data.optJSONObject("color");
        int r = c.optInt("r", 255);
        int g = c.optInt("g", 255);
        int b = c.optInt("b", 255);
        int duration = data.optInt("duration", 0);
        MouthLedApi.get().startNormalModel(Color.argb(0, r, g, b), duration * 1000, com.ubtrobot.commons.Priority.HIGH,
                new ResponseListener() {
                    @Override
                    public void onResponseSuccess(Object o) {
                        onComplete.run();
                    }

                    @Override
                    public void onFailure(int i, @NonNull String s) {
                        onComplete.run();
                        Log.e(TAG, "LED block failed: " + s);
                    }
                });
    }

    private void handleTTSBlock(String text, String lang, Runnable onComplete) {
        ttsHandler.doTTS(text, lang, new TTSCallback() {
            @Override
            public void onStart() {
            }

            @Override
            public void onDone() {
                Log.i(TAG, "TTS completed: " + text);
                onComplete.run();
            }

            @Override
            public void onError() {
                Log.e(TAG, "TTS failed: " + text);
                onComplete.run(); // Continue execution even on error
            }
        });
    }

    private void handleExpressionBlock(String code, Runnable onComplete) {
        expressionHandler.handleExpression(code, new AnimationListener() {
            @Override
            public void onAnimationStart() {
                Log.i(TAG, "Expression started: " + code);
            }

            @Override
            public void onAnimationEnd(int i) {
                Log.i(TAG, "Expression completed: " + code);
                onComplete.run();
            }

            @Override
            public void onAnimationRepeat(int i) {
                // Ignore repeats
            }
        });
    }

    private void handleActionBlock(String code, Runnable onComplete) {
        actionHandler.handleAction(code, new ResponseListener<Void>() {
            @Override
            public void onResponseSuccess(Void aVoid) {
                Log.i(TAG, "Action completed: " + code);
                onComplete.run();
            }

            @Override
            public void onFailure(int i, @NonNull String s) {
                Log.e(TAG, "Action failed: " + code + " - " + s);
                onComplete.run(); // Continue execution even on error
            }
        });
    }

    private void handleExtendedActionBlock(JSONObject block, Runnable onComplete) {
        JSONObject extData = block.optJSONObject("data");
        if (extData != null) {
            extendedActionHandler.handleExtendedActionWithCallback(extData, onComplete);
        } else {
            // If no data or callback support, continue immediately
            Log.w(TAG, "Extended action has no data");
            onComplete.run();
        }
    }

    /**
     * Clears all pending blocks from the queue
     */
    public void clearQueue() {
        int clearedCount = blockQueue.size();
        blockQueue.clear();
        isExecuting = false;
        Log.i(TAG, "Queue cleared, removed " + clearedCount + " blocks");
    }

    /**
     * Gets the number of pending blocks in the queue
     */
    public int getQueueSize() {
        return blockQueue.size();
    }

    /**
     * Checks if blocks are currently executing
     */
    public boolean isExecuting() {
        return isExecuting;
    }

    /**
     * Checks if the queue is empty
     */
    public boolean isQueueEmpty() {
        return blockQueue.isEmpty();
    }

    /**
     * Stops current execution but keeps remaining blocks in queue
     */
    public void pauseExecution() {
        isExecuting = false;
        Log.i(TAG, "Execution paused, " + blockQueue.size() + " blocks remain in queue");
    }

    /**
     * Resumes execution if there are blocks in queue
     */
    public void resumeExecution() {
        if (!blockQueue.isEmpty() && !isExecuting) {
            executeNextBlock();
        }
    }
}