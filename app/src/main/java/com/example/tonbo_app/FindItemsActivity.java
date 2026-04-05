package com.example.tonbo_app;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FindItemsActivity extends BaseAccessibleActivity {
    private static final String ZHIPU_API_KEY = "76cb4d9ddeaa48ed8ada4b66594c0e8d.8FI0uXvkdEMimRb8";
    private static final String API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    private PreviewView cameraPreview;
    private ImageView capturedImage;
    private TextView statusText;
    private Button btnCapture, btnRetake, btnVoiceAsk, btnMemory;
    private LinearLayout layoutActionButtons;
    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private Bitmap currentBitmap;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private final OkHttpClient httpClient = new OkHttpClient();
    private boolean isAILoading = false;
    private boolean isMemoryMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_find_items);
        cameraExecutor = Executors.newSingleThreadExecutor();
        initViews();
        initSpeechEngine();
        checkPermissions();
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.camera_preview);
        capturedImage = findViewById(R.id.captured_image);
        statusText = findViewById(R.id.status_text);
        btnCapture = findViewById(R.id.btn_capture);
        btnVoiceAsk = findViewById(R.id.btn_voice_ask);
        btnRetake = findViewById(R.id.btn_retake);
        btnMemory = findViewById(R.id.btn_memory); // 現在這裡不會報錯了
        layoutActionButtons = findViewById(R.id.layout_action_buttons);

        btnCapture.setOnClickListener(v -> takePhoto());
        btnRetake.setOnClickListener(v -> resetToCamera());
        btnVoiceAsk.setOnClickListener(v -> { isMemoryMode = false; startVoiceInteraction(); });
        btnMemory.setOnClickListener(v -> { isMemoryMode = true; startVoiceInteraction(); });

        if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
    }

    // --- 智能保存：自動提取關鍵詞當標題 ---
    private void saveWithAutoTitle(String content) {
        // 發送一個超短請求給 AI 提取標題
        JSONObject json = new JSONObject();
        try {
            json.put("model", "glm-4");
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", "將以下描述提取為 5 個字以內的標題： " + content);
            messages.put(msg);
            json.put("messages", messages);

            RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL)
                    .header("Authorization", "Bearer " + ZHIPU_API_KEY)
                    .post(body)
                    .build();

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                    if (response.isSuccessful()) {
                        try {
                            JSONObject resp = new JSONObject(response.body().string());
                            String title = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            actualSave(title, content); // 真正寫入本地
                        } catch (Exception e) { actualSave("未知物品", content); }
                    }
                }
                @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { actualSave("識別歷史", content); }
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void actualSave(String title, String content) {
        SharedPreferences prefs = getSharedPreferences("AppMemory", MODE_PRIVATE);
        String history = prefs.getString("history", "[]");
        try {
            JSONArray array = new JSONArray(history);
            JSONObject entry = new JSONObject();
            entry.put("title", title.replace("\"", "")); // 清理引號
            entry.put("time", new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            entry.put("content", content);
            array.put(entry);
            if (array.length() > 20) array.remove(0);
            prefs.edit().putString("history", array.toString()).apply();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- 記憶對話邏輯 ---
    private void runMemoryChat(String query) {
        statusText.setText("檢索記憶中...");
        SharedPreferences prefs = getSharedPreferences("AppMemory", MODE_PRIVATE);
        String history = prefs.getString("history", "[]");

        try {
            JSONObject json = new JSONObject();
            json.put("model", "glm-4");
            JSONArray messages = new JSONArray();
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你是盲人助手的記憶模塊。歷史紀錄(標題與內容)：" + history + "。請回答用戶的問題。");

            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", query);

            messages.put(sys);
            messages.put(user);
            json.put("messages", messages);

            sendZhipuRequest(json, true);
        } catch (Exception e) { isAILoading = false; }
    }

    // --- 物品識別邏輯 ---
    private void runZhipuAIDetection() {
        if (currentBitmap == null) { isAILoading = false; return; }
        statusText.setText("正在分析畫面...");

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        currentBitmap.compress(Bitmap.CompressFormat.JPEG, 60, os);
        String base64Image = Base64.encodeToString(os.toByteArray(), Base64.NO_WRAP);

        try {
            JSONObject json = new JSONObject();
            json.put("model", "glm-4v-plus");
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            JSONArray content = new JSONArray();

            JSONObject tPart = new JSONObject();
            tPart.put("type", "text");
            tPart.put("text", "請簡潔描述正前方的物品。");

            JSONObject iPart = new JSONObject();
            iPart.put("type", "image_url");
            JSONObject iUrl = new JSONObject();
            iUrl.put("url", base64Image);
            iPart.put("image_url", iUrl);

            content.put(tPart); content.put(iPart);
            msg.put("content", content);
            messages.put(msg);
            json.put("messages", messages);

            sendZhipuRequest(json, false);
        } catch (Exception e) { isAILoading = false; }
    }

    private void sendZhipuRequest(JSONObject json, boolean isChat) {
        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(API_URL).header("Authorization", "Bearer " + ZHIPU_API_KEY).post(body).build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                final String res = response.isSuccessful() ? response.body().string() : null;
                runOnUiThread(() -> {
                    try {
                        if (res != null) {
                            JSONObject jobj = new JSONObject(res);
                            String text = jobj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            statusText.setText(text);
                            ttsManager.speak(text, null, true);
                            if (!isChat) saveWithAutoTitle(text); // 識別模式下提取標題並保存
                        }
                    } catch (Exception e) { e.printStackTrace(); }
                    finally { isAILoading = false; }
                });
            }
            @Override public void onFailure(@NonNull Call call, @NonNull IOException e) { runOnUiThread(() -> isAILoading = false); }
        });
    }

    // --- 語音、相機等基礎方法（與之前版本一致） ---
    private void initSpeechEngine() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new CustomRecognitionListener());
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
    }

    private void startVoiceInteraction() {
        if (isAILoading) return;
        vibrationManager.vibrateClick();
        speechRecognizer.cancel();
        new Handler().postDelayed(() -> speechRecognizer.startListening(speechIntent), 100);
    }

    private class CustomRecognitionListener implements RecognitionListener {
        @Override public void onReadyForSpeech(Bundle params) { statusText.setText("請提問..."); ttsManager.speak("請說話", null, true); }
        @Override public void onResults(Bundle results) {
            ArrayList<String> m = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (m != null && !m.isEmpty()) {
                isAILoading = true;
                if (isMemoryMode) runMemoryChat(m.get(0)); else runZhipuAIDetection();
            }
        }
        @Override public void onError(int error) { isAILoading = false; }
        @Override public void onBeginningOfSpeech() {}
        @Override public void onEndOfSpeech() {}
        @Override public void onRmsChanged(float rmsdB) {}
        @Override public void onBufferReceived(byte[] buffer) {}
        @Override public void onPartialResults(Bundle partialResults) {}
        @Override public void onEvent(int eventType, Bundle params) {}
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        imageCapture.takePicture(cameraExecutor, new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                ByteBuffer b = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[b.remaining()];
                b.get(bytes);
                currentBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                image.close();
                runOnUiThread(() -> {
                    capturedImage.setImageBitmap(currentBitmap);
                    cameraPreview.setVisibility(View.GONE);
                    capturedImage.setVisibility(View.VISIBLE);
                    btnCapture.setVisibility(View.GONE);
                    if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void resetToCamera() {
        currentBitmap = null;
        capturedImage.setVisibility(View.GONE);
        cameraPreview.setVisibility(View.VISIBLE);
        btnCapture.setVisibility(View.VISIBLE);
        if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
    }

    private void setupCamera() {
        ListenableFuture<ProcessCameraProvider> pf = ProcessCameraProvider.getInstance(this);
        pf.addListener(() -> {
            try {
                ProcessCameraProvider p = pf.get();
                Preview pr = new Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();
                pr.setSurfaceProvider(cameraPreview.getSurfaceProvider());
                imageCapture = new ImageCapture.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).build();
                p.unbindAll();
                p.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, pr, imageCapture);
            } catch (Exception e) {}
        }, ContextCompat.getMainExecutor(this));
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 101);
        } else { setupCamera(); }
    }

    @Override protected void announcePageTitle() { ttsManager.speak("記憶助理", null, true); }
}