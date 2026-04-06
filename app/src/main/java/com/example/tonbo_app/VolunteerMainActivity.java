package com.example.tonbo_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

/**
 * 志願者入口頁（簡化主頁）。
 */
public class VolunteerMainActivity extends BaseAccessibleActivity {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_volunteer_main);

        Button langBtn = findViewById(R.id.volunteerLanguageButton);
        if (langBtn != null) {
            updateLanguageButtonLabel(langBtn);
            String languageDesc = getLanguageDescription(currentLanguage);
            langBtn.setContentDescription(
                    getString(R.string.language_button_desc_prefix)
                            + languageDesc
                            + getString(R.string.language_button_desc_suffix));
            langBtn.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                toggleLanguage(langBtn);
            });
        }

        Button instantBtn = findViewById(R.id.btnVolunteerInstantAssist);
        if (instantBtn != null) {
            instantBtn.setOnClickListener(v -> {
                vibrationManager.vibrateClick();
                Intent intent = new Intent(this, InstantAssistanceActivity.class);
                intent.putExtra("language", currentLanguage);
                startActivity(intent);
            });
        }
    }

    private void toggleLanguage(Button languageButton) {
        currentLanguage = nextLanguage(currentLanguage);
        localeManager.setLanguage(this, currentLanguage);
        ttsManager.changeLanguage(currentLanguage);
        speakLanguageSwitchedTo(currentLanguage);
        updateLanguageButtonLabel(languageButton);
        String languageDesc = getLanguageDescription(currentLanguage);
        languageButton.setContentDescription(
                getString(R.string.language_button_desc_prefix)
                        + languageDesc
                        + getString(R.string.language_button_desc_suffix));
        mainHandler.postDelayed(() -> {
            if (!isFinishing() && !isDestroyed()) {
                recreate();
            }
        }, 300);
    }

    private static String nextLanguage(String current) {
        switch (current) {
            case AppConstants.LANGUAGE_CANTONESE:
                return AppConstants.LANGUAGE_ENGLISH;
            case AppConstants.LANGUAGE_ENGLISH:
                return AppConstants.LANGUAGE_MANDARIN;
            case AppConstants.LANGUAGE_MANDARIN:
            default:
                return AppConstants.LANGUAGE_CANTONESE;
        }
    }

    private void updateLanguageButtonLabel(Button languageButton) {
        int res;
        switch (currentLanguage) {
            case AppConstants.LANGUAGE_ENGLISH:
                res = R.string.language_button_english;
                break;
            case AppConstants.LANGUAGE_MANDARIN:
                res = R.string.language_button_mandarin;
                break;
            case AppConstants.LANGUAGE_CANTONESE:
            default:
                res = R.string.language_button_cantonese;
                break;
        }
        languageButton.setText(res);
    }

    private void speakLanguageSwitchedTo(String language) {
        String cantonese;
        String english;
        String mandarin;
        switch (language) {
            case AppConstants.LANGUAGE_CANTONESE:
                cantonese = "已切換到廣東話";
                english = "Switched to Cantonese";
                mandarin = "已切换到粤语";
                break;
            case AppConstants.LANGUAGE_ENGLISH:
                cantonese = "已切換到英文";
                english = "Switched to English";
                mandarin = "已切换到英文";
                break;
            case AppConstants.LANGUAGE_MANDARIN:
                cantonese = "已切換到普通話";
                english = "Switched to Mandarin";
                mandarin = "已切换到普通话";
                break;
            default:
                cantonese = "語言切換完成";
                english = "Language switched";
                mandarin = "语言切换完成";
                break;
        }
        if (AppConstants.LANGUAGE_ENGLISH.equals(language)) {
            ttsManager.speak(english, english, true);
        } else if (AppConstants.LANGUAGE_MANDARIN.equals(language)) {
            ttsManager.speak(mandarin, mandarin, true);
        } else {
            ttsManager.speak(cantonese, english, true);
        }
    }

    private String getLanguageDescription(String language) {
        switch (language) {
            case AppConstants.LANGUAGE_CANTONESE:
                return getString(R.string.language_cantonese_desc);
            case AppConstants.LANGUAGE_ENGLISH:
                return getString(R.string.language_english_desc);
            case AppConstants.LANGUAGE_MANDARIN:
                return getString(R.string.language_mandarin_desc);
            default:
                return getString(R.string.language_english_desc);
        }
    }

    @Override
    protected void announcePageTitle() {
        String msg = getString(R.string.volunteer_main_announce);
        ttsManager.speak(msg, msg, true);
    }
}
