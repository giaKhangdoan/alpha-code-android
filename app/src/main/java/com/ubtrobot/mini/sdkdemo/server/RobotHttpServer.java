package com.ubtrobot.mini.sdkdemo.server;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ubtrobot.mini.sdkdemo.common.handlers.DanceHandler;
import com.ubtrobot.mini.sdkdemo.common.handlers.TTSHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fi.iki.elonen.NanoHTTPD;

/**
 * HTTP Server chạy trên Robot để nhận lệnh từ Flutter App qua mạng LAN.
 *
 * Endpoints:
 * - GET /ping - Kiểm tra kết nối
 * - GET /status - Lấy trạng thái robot (pin, kết nối,...)
 * - GET /actions - Lấy danh sách action có sẵn
 * - POST /tts - Text to Speech
 * - POST /move - Điều khiển di chuyển (joystick)
 * - POST /action - Chạy một action cụ thể
 * - POST /dance_with_music - Nhảy theo nhạc
 * - POST /stop - Dừng tất cả
 */
public class RobotHttpServer extends NanoHTTPD {
    private static final String TAG = "RobotHttpServer";
    private static final int DEFAULT_PORT = 8080;

    private final Context context;
    private final Gson gson;
    private final RobotCommandHandler commandHandler;

    public RobotHttpServer(Context context) {
        super(DEFAULT_PORT);
        this.context = context;
        this.gson = new Gson();
        this.commandHandler = new RobotCommandHandler(context);
        Log.i(TAG, "RobotHttpServer initialized on port " + DEFAULT_PORT);
    }

    public RobotHttpServer(Context context, int port) {
        super(port);
        this.context = context;
        this.gson = new Gson();
        this.commandHandler = new RobotCommandHandler(context);
        Log.i(TAG, "RobotHttpServer initialized on port " + port);
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();

        Log.d(TAG, "Request: " + method + " " + uri);

        // Add CORS headers
        Response response;

        try {
            // Handle OPTIONS for CORS preflight
            if (Method.OPTIONS.equals(method)) {
                response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "");
                addCorsHeaders(response);
                return response;
            }

            // Route requests
            switch (uri) {
                case "/ping":
                    response = handlePing();
                    break;
                case "/status":
                    response = handleGetStatus();
                    break;
                case "/actions":
                    response = handleGetActions();
                    break;
                case "/tts":
                    response = handleTTS(session);
                    break;
                case "/move":
                    response = handleMove(session);
                    break;
                case "/action":
                    response = handlePlayAction(session);
                    break;
                case "/dance_with_music":
                    response = handleDanceWithMusic(session);
                    break;
                case "/stop":
                    response = handleStop();
                    break;
                case "/expression":
                    response = handleExpression(session);
                    break;
                default:
                    response = createErrorResponse(Response.Status.NOT_FOUND, "Endpoint not found: " + uri);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling request", e);
            response = createErrorResponse(Response.Status.INTERNAL_ERROR, "Server error: " + e.getMessage());
        }

        addCorsHeaders(response);
        return response;
    }

    // ==================== ENDPOINT HANDLERS ====================

    /**
     * GET /ping - Kiểm tra kết nối
     */
    private Response handlePing() {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "Alpha Mini Robot is online!");
        result.put("timestamp", System.currentTimeMillis());
        return createJsonResponse(result);
    }

    /**
     * GET /status - Lấy trạng thái robot
     */
    private Response handleGetStatus() {
        try {
            Map<String, Object> status = commandHandler.getRobotStatus();
            return createJsonResponse(status);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.INTERNAL_ERROR, "Failed to get status: " + e.getMessage());
        }
    }

    /**
     * GET /actions - Lấy danh sách actions
     */
    private Response handleGetActions() {
        try {
            Map<String, Object> result = new HashMap<>();
            result.put("actions", commandHandler.getActionList());
            result.put("custom_actions", commandHandler.getCustomActionList());
            return createJsonResponse(result);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.INTERNAL_ERROR, "Failed to get actions: " + e.getMessage());
        }
    }

    /**
     * POST /tts - Text to Speech
     * Body: { "text": "Hello", "lang": "vi" }
     */
    private Response handleTTS(IHTTPSession session) {
        try {
            JsonObject body = parseRequestBody(session);
            String text = body.get("text").getAsString();
            String lang = body.has("lang") ? body.get("lang").getAsString() : "vi";

            commandHandler.speak(text, lang);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("message", "Speaking: " + text);
            return createJsonResponse(result);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.BAD_REQUEST, "Invalid request: " + e.getMessage());
        }
    }

    /**
     * POST /move - Điều khiển di chuyển
     * Body: { "x": 0.5, "y": -0.3, "head_angle": 0 }
     */
    private Response handleMove(IHTTPSession session) {
        try {
            JsonObject body = parseRequestBody(session);
            double x = body.get("x").getAsDouble();
            double y = body.get("y").getAsDouble();
            double headAngle = body.has("head_angle") ? body.get("head_angle").getAsDouble() : 0;

            commandHandler.move(x, y, headAngle);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            return createJsonResponse(result);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.BAD_REQUEST, "Invalid request: " + e.getMessage());
        }
    }

    /**
     * POST /action - Chạy một action
     * Body: { "action_id": "dance_0001", "is_custom": false }
     */
    private Response handlePlayAction(IHTTPSession session) {
        try {
            JsonObject body = parseRequestBody(session);
            String actionId = body.get("action_id").getAsString();
            boolean isCustom = body.has("is_custom") && body.get("is_custom").getAsBoolean();

            commandHandler.playAction(actionId, isCustom);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("message", "Playing action: " + actionId);
            return createJsonResponse(result);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.BAD_REQUEST, "Invalid request: " + e.getMessage());
        }
    }

    /**
     * POST /dance_with_music - Nhảy theo nhạc
     * Body: {
     * "music_info": { "music_file_url": "https://..." },
     * "activity": { "actions": [...] }
     * }
     */
    private Response handleDanceWithMusic(IHTTPSession session) {
        try {
            JsonObject body = parseRequestBody(session);

            commandHandler.danceWithMusic(body);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("message", "Dance started!");
            return createJsonResponse(result);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.BAD_REQUEST, "Invalid request: " + e.getMessage());
        }
    }

    /**
     * POST /stop - Dừng tất cả
     */
    private Response handleStop() {
        try {
            commandHandler.stopAll();

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("message", "All actions stopped");
            return createJsonResponse(result);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.INTERNAL_ERROR, "Failed to stop: " + e.getMessage());
        }
    }

    /**
     * POST /expression - Hiển thị biểu cảm
     * Body: { "expression": "happy" }
     */
    private Response handleExpression(IHTTPSession session) {
        try {
            JsonObject body = parseRequestBody(session);
            String expression = body.get("expression").getAsString();

            commandHandler.showExpression(expression);

            Map<String, Object> result = new HashMap<>();
            result.put("status", "ok");
            result.put("message", "Expression: " + expression);
            return createJsonResponse(result);
        } catch (Exception e) {
            return createErrorResponse(Response.Status.BAD_REQUEST, "Invalid request: " + e.getMessage());
        }
    }

    // ==================== HELPER METHODS ====================

    private JsonObject parseRequestBody(IHTTPSession session) throws IOException, ResponseException {
        Map<String, String> files = new HashMap<>();
        session.parseBody(files);
        String body = files.get("postData");
        if (body == null || body.isEmpty()) {
            body = "{}";
        }
        return new JsonParser().parse(body).getAsJsonObject();
    }

    private Response createJsonResponse(Object data) {
        String json = gson.toJson(data);
        return newFixedLengthResponse(Response.Status.OK, "application/json", json);
    }

    private Response createErrorResponse(Response.Status status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        String json = gson.toJson(error);
        return newFixedLengthResponse(status, "application/json", json);
    }

    private void addCorsHeaders(Response response) {
        response.addHeader("Access-Control-Allow-Origin", "*");
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    /**
     * Start the HTTP server
     */
    public void startServer() {
        try {
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            Log.i(TAG, "HTTP Server started on port " + getListeningPort());
        } catch (IOException e) {
            Log.e(TAG, "Failed to start HTTP server", e);
        }
    }

    /**
     * Stop the HTTP server
     */
    public void stopServer() {
        stop();
        Log.i(TAG, "HTTP Server stopped");
    }
}
