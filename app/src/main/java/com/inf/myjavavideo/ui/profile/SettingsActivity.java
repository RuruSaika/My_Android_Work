package com.inf.myjavavideo.ui.profile;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.databinding.ActivitySettingsBinding;
import com.inf.myjavavideo.utils.SessionManager;

public class SettingsActivity extends AppCompatActivity {

    private ActivitySettingsBinding binding;
    private SessionManager sessionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 设置标题栏
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.settings);
        }
        
        // 初始化
        sessionManager = new SessionManager(this);
        
        // 加载设置
        setupSettings();
        
        // 设置监听器
        binding.switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            setDarkMode(isChecked);
            sessionManager.setDarkModeEnabled(isChecked);
        });
        
        binding.switchAutoPlay.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setAutoPlayEnabled(isChecked);
            Toast.makeText(this, isChecked ? "自动播放已开启" : "自动播放已关闭", Toast.LENGTH_SHORT).show();
        });
        
        binding.switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sessionManager.setNotificationsEnabled(isChecked);
            Toast.makeText(this, isChecked ? "通知已开启" : "通知已关闭", Toast.LENGTH_SHORT).show();
        });
        
        binding.buttonClearCache.setOnClickListener(v -> {
            // 清除缓存的逻辑
            Toast.makeText(this, "缓存已清除", Toast.LENGTH_SHORT).show();
        });
        
        binding.buttonAbout.setOnClickListener(v -> {
            // 显示关于信息
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.about)
                    .setMessage("MyJavaVideo\n版本 1.0.0\n\n视频播放器应用")
                    .setPositiveButton(R.string.ok, null)
                    .show();
        });
    }
    
    private void setupSettings() {
        // 设置开关状态
        binding.switchDarkMode.setChecked(sessionManager.isDarkModeEnabled());
        binding.switchAutoPlay.setChecked(sessionManager.isAutoPlayEnabled());
        binding.switchNotifications.setChecked(sessionManager.isNotificationsEnabled());
    }
    
    private void setDarkMode(boolean enabled) {
        if (enabled) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
} 