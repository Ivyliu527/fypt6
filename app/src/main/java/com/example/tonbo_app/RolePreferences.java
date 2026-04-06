package com.example.tonbo_app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 啟動身份（視障用戶 / 志願者），與 {@link LocaleManager} 分開存儲。
 */
public final class RolePreferences {

    private static final String PREF_NAME = "TonboAppRole";
    private static final String KEY_ROLE = "user_role";

    public static final String ROLE_USER = "user";
    public static final String ROLE_VOLUNTEER = "volunteer";

    private RolePreferences() {}

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** @return 已保存角色，未選擇時為 null */
    public static String getRole(Context context) {
        return prefs(context).getString(KEY_ROLE, null);
    }

    public static void saveRole(Context context, String role) {
        prefs(context).edit().putString(KEY_ROLE, role).apply();
    }
}
