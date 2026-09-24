package com.ubtrobot.mini.sdkdemo.common.handlers;

import android.util.Log;

import com.ubtrobot.mini.sdkdemo.log.LogManager;

import org.json.JSONObject;

public class SubmissionHandler {
    private static final String TAG = "SubmissionHandler";

    private TTSHandler ttsHandler;

    public SubmissionHandler() {
        this.ttsHandler = TTSHandler.getInstance();
    }

    /**
     * Xử lý submission_start
     * Data chứa: { "account_lesson_id": "uuid-string" }
     */
    public void handleSubmissionStart(JSONObject data, String lang) {
        try {
            String accountLessonId = data.optString("account_lesson_id");

            if (accountLessonId == null || accountLessonId.isEmpty()) {
                Log.e(TAG, "account_lesson_id is missing in submission_start");
                ttsHandler.doTTS(
                        lang.equals("vi") ? "Lỗi: Không tìm thấy ID bài học" : "Error: Account lesson ID is required",
                        lang);
                return;
            }

            // Lưu accountLessonId tạm thời
            LogManager.startSubmission(accountLessonId);

            Log.i(TAG, "Submission started for accountLessonId: " + accountLessonId);

            // Thông báo cho người dùng
            ttsHandler.doTTS(
                    lang.equals("vi") ? "Bắt đầu ghi nhận bài làm. Tôi đã sẵn sàng."
                            : "Submission started. I'm ready to record your actions.",
                    lang);

        } catch (Exception e) {
            Log.e(TAG, "Error handling submission_start", e);
        }
    }

    /**
     * Xử lý submission_end
     * Không cần accountLessonId trong data - dùng từ LogManager
     */
    public void handleSubmissionEnd(JSONObject data, String lang) {
        try {
            if (!LogManager.isSubmissionActive()) {
                Log.w(TAG, "No active submission to end");
                ttsHandler.doTTS(
                        lang.equals("vi") ? "Không tìm thấy bài làm đang hoạt động" : "No active submission found",
                        lang);
                return;
            }

            String accountLessonId = LogManager.getCurrentAccountLessonId();
            Log.i(TAG, "Ending submission for accountLessonId: " + accountLessonId);

            // Xóa accountLessonId và gửi log submission_end
            LogManager.endSubmission();

            // Thông báo hoàn tất
            ttsHandler.doTTS(
                    lang.equals("vi") ? "Bài làm của bạn đã được gửi!" : "Your work has been submitted successfully!",
                    lang);

        } catch (Exception e) {
            Log.e(TAG, "Error handling submission_end", e);
        }
    }
}
