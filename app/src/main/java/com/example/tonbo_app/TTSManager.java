package com.example.tonbo_app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TTSManager {
    private static final String TAG = "TTSManager";
    private static TTSManager instance;

    /** 四參 speak 使用的固定 id，播報完成時觸發 OnSpeechFinishedListener（DocumentCurrency 等） */
    private static final String UTTERANCE_TASK_ID = "TTS_TASK_ID";
    /** 普通三參 speak，不觸發 OnSpeechFinishedListener */
    private static final String UTTERANCE_DEFAULT_ID = "TTS_DEFAULT";

    private TextToSpeech textToSpeech;
    private Context context;
    private String currentLanguage = "english";
    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private boolean isSpeaking = false;

    private String lastSetLanguage = null;
    private static final Locale cantoneseLocale = new Locale("zh", "HK");

    public interface OnSpeechFinishedListener {
        void onFinished();
    }

    private OnSpeechFinishedListener mListener;

    /** 帶 utteranceId 的播報完成（如 NavigationActivity 路線播報鏈） */
    public interface OnSpeechCompleteListener {
        void onSpeechComplete(String utteranceId);
    }

    private OnSpeechCompleteListener speechCompleteListener;

    private final ConcurrentLinkedQueue<String> speechQueue = new ConcurrentLinkedQueue<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    public void setOnSpeechCompleteListener(OnSpeechCompleteListener listener) {
        this.speechCompleteListener = listener;
    }

    private TTSManager(Context context) {
        this.context = context.getApplicationContext();
        initTTS();
    }

    public static synchronized TTSManager getInstance(Context context) {
        if (instance == null) {
            instance = new TTSManager(context);
        }
        return instance;
    }

    private void initTTS() {
        if (isInitializing) return;
        isInitializing = true;
        textToSpeech = new TextToSpeech(context, status -> {
            isInitializing = false;
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true;
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        isSpeaking = true;
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        isSpeaking = false;
                        if (speechCompleteListener != null && utteranceId != null) {
                            new Handler(Looper.getMainLooper()).post(() ->
                                    speechCompleteListener.onSpeechComplete(utteranceId));
                        }
                        if (UTTERANCE_TASK_ID.equals(utteranceId) && mListener != null) {
                            new Handler(Looper.getMainLooper()).post(() -> mListener.onFinished());
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        isSpeaking = false;
                    }
                });
                setLanguage(currentLanguage);
                Log.d(TAG, "TTS initialized, language: " + currentLanguage);
            } else {
                isInitialized = false;
                Log.e(TAG, "TTS init failed: " + status);
            }
        });
    }

    /**
     * 四參 speak：與 origin/main 一致，完成時回調 OnSpeechFinishedListener。
     */
    public void speak(String cantonese, String english, boolean priority, OnSpeechFinishedListener listener) {
        this.mListener = listener;
        String textToSpeak = "english".equals(currentLanguage)
                ? (english != null ? english : cantonese)
                : (cantonese != null ? cantonese : english);
        if (textToSpeak == null || textToSpeak.trim().isEmpty()) return;

        if (textToSpeech != null && isInitialized) {
            setLanguage(currentLanguage);
            int mode = priority ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            textToSpeech.speak(textToSpeak, mode, null, UTTERANCE_TASK_ID);
        } else {
            if (textToSpeech == null && !isInitializing) {
                initTTS();
            }
            handler.postDelayed(() -> speak(cantonese, english, priority, listener), 1000);
        }
    }

    public void speak(String cantonese, String english) {
        speak(cantonese, english, false);
    }

    public void speak(String cantonese, String english, boolean priority) {
        String textToSpeak = "english".equals(currentLanguage)
                ? (english != null ? english : cantonese)
                : (cantonese != null ? cantonese : english);
        if (textToSpeak == null || textToSpeak.trim().isEmpty()) return;

        if (textToSpeech != null && isInitialized) {
            setLanguage(currentLanguage);
            int mode = priority ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            textToSpeech.speak(textToSpeak, mode, null, UTTERANCE_DEFAULT_ID);
        } else {
            if (textToSpeech == null && !isInitializing) {
                initTTS();
            }
            handler.postDelayed(() -> speak(cantonese, english, priority), 1000);
        }
    }

    /**
     * 帶 utteranceId 的播報，完成後回調 OnSpeechCompleteListener（出行導航等）。
     */
    public void speakWithId(String cantoneseText, String englishText, boolean priority, String utteranceId) {
        if (textToSpeech == null && !isInitializing) {
            initTTS();
        }
        if (!isInitialized || textToSpeech == null) {
            handler.postDelayed(() -> speakWithId(cantoneseText, englishText, priority, utteranceId), 1000);
            return;
        }

        String textToSpeak = "english".equals(currentLanguage)
                ? (englishText != null ? englishText : cantoneseText)
                : (cantoneseText != null ? cantoneseText : englishText);
        if (textToSpeak == null || textToSpeak.trim().isEmpty()) return;

        setLanguage(currentLanguage);
        if (priority) {
            textToSpeech.stop();
            speechQueue.clear();
        }
        String id = utteranceId != null ? utteranceId : UTTERANCE_DEFAULT_ID;
        textToSpeech.speak(textToSpeak,
                priority ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD,
                null,
                id);
    }

    private void setLanguage(String language) {
        if (language.equals(lastSetLanguage)) return;
        if (textToSpeech == null) {
            Log.w(TAG, "textToSpeech null, cannot set language");
            return;
        }

        int result = TextToSpeech.LANG_MISSING_DATA;
        switch (language) {
            case "cantonese":
                result = textToSpeech.setLanguage(cantoneseLocale);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = textToSpeech.setLanguage(Locale.TAIWAN);
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        result = textToSpeech.setLanguage(Locale.TRADITIONAL_CHINESE);
                    }
                }
                break;
            case "english":
                result = textToSpeech.setLanguage(Locale.ENGLISH);
                break;
            case "mandarin":
            default:
                result = textToSpeech.setLanguage(Locale.SIMPLIFIED_CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = textToSpeech.setLanguage(Locale.TRADITIONAL_CHINESE);
                }
                break;
        }

        if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
            lastSetLanguage = language;
        } else {
            Log.e(TAG, "Language not supported: " + language);
        }
    }

    public void setLanguageSilently(String language) {
        currentLanguage = language;
        setLanguage(language);
        Log.d(TAG, "Language silently set to: " + language);
    }

    public void changeLanguage(String lang) {
        this.currentLanguage = lang;
        setLanguage(lang);
    }

    public String getCurrentLanguage() {
        return currentLanguage;
    }

    public void setSpeechRate(float rate) {
        if (textToSpeech != null) {
            textToSpeech.setSpeechRate(rate);
        }
    }

    public void setSpeechPitch(float pitch) {
        if (textToSpeech != null) {
            textToSpeech.setPitch(pitch);
        }
    }

    public void setSpeechVolume(float volume) {
        Log.d(TAG, "Speech volume note: " + volume + " (use system media volume)");
    }

    public void stop() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    public void stopSpeaking() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        speechQueue.clear();
        isSpeaking = false;
    }

    public void forceInitialize() {
        if (textToSpeech == null) {
            initTTS();
        }
    }

    public boolean isSpeaking() {
        return isSpeaking || (textToSpeech != null && textToSpeech.isSpeaking());
    }

    public void pauseSpeaking() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    public void shutdown() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
    }

    public void speakPageTitle(String name) {
        speak("當前頁面：" + name, "Current page: " + name, true);
    }

    public void speakSuccess(String msg) {
        speak("成功：" + msg, "Success: " + msg, true);
    }

    public void speakError(String err) {
        speak("錯誤：" + err, "Error: " + err, true);
    }

    public void speakNavigationHint(String hint) {
        speak("提示：" + hint, "Hint: " + hint, false);
    }

    public void speakWelcomeMessage() {
        String welcomeText = "瞳伴應用已啟動。歡迎使用智能視覺助手。" +
                "當前有四個主要功能：環境識別、閱讀助手、尋找物品、即時協助。" +
                "請點擊或滑動選擇功能。底部有緊急求助按鈕，長按三秒可發送求助信息。" +
                "如需切換語言，請點擊右上角的語言按鈕。";

        String englishText = "Tonbo application started. Welcome to the smart vision assistant. " +
                "Four main functions available: Environment Recognition, Document Assistant, Find Items, Live Assistance. " +
                "Tap or swipe to select function. Emergency help button at bottom, long press for 3 seconds to send help request. " +
                "To switch language, tap the language button on top right.";

        speak(welcomeText, englishText, true);
    }
}
