package com.inf.myjavavideo.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 会话管理器，用于管理用户登录状态
 */
public class SessionManager {
    private static final String PREF_NAME = "VideoPlayerPref";
    private static final String KEY_IS_LOGGED_IN = "isLoggedIn";
    private static final String KEY_USER_ID = "userId";
    private static final String PREF_DARK_MODE_ENABLED = "dark_mode_enabled";
    private static final String PREF_AUTO_PLAY_ENABLED = "auto_play_enabled";
    private static final String PREF_NOTIFICATIONS_ENABLED = "notifications_enabled";
    
    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;
    
    public SessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }
    
    /**
     * 设置用户登录状态
     */
    public void setLogin(boolean isLoggedIn) {
        editor.putBoolean(KEY_IS_LOGGED_IN, isLoggedIn);
        editor.apply();
    }
    
    /**
     * 设置用户ID
     */
    public void setUserId(int userId) {
        editor.putInt(KEY_USER_ID, userId);
        editor.apply();
    }
    
    /**
     * 获取用户ID
     */
    public int getUserId() {
        return sharedPreferences.getInt(KEY_USER_ID, -1);
    }
    
    /**
     * 检查用户是否已登录
     */
    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGGED_IN, false);
    }
    
    /**
     * 注销用户，清除所有会话数据
     */
    public void logout() {
        editor.clear();
        editor.apply();
    }
    
    public void setDarkModeEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_DARK_MODE_ENABLED, enabled);
        editor.apply();
    }
    
    public boolean isDarkModeEnabled() {
        return sharedPreferences.getBoolean(PREF_DARK_MODE_ENABLED, false);
    }
    
    public void setAutoPlayEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_AUTO_PLAY_ENABLED, enabled);
        editor.apply();
    }
    
    public boolean isAutoPlayEnabled() {
        return sharedPreferences.getBoolean(PREF_AUTO_PLAY_ENABLED, true);
    }
    
    public void setNotificationsEnabled(boolean enabled) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(PREF_NOTIFICATIONS_ENABLED, enabled);
        editor.apply();
    }
    
    public boolean isNotificationsEnabled() {
        return sharedPreferences.getBoolean(PREF_NOTIFICATIONS_ENABLED, true);
    }
} 