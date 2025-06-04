package com.inf.myjavavideo.ui.auth;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.inf.myjavavideo.MainActivity;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.UserDao;
import com.inf.myjavavideo.data.model.User;
import com.inf.myjavavideo.databinding.ActivityLoginBinding;
import com.inf.myjavavideo.utils.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {
    
    private ActivityLoginBinding binding;
    private ExecutorService executorService;
    private UserDao userDao;
    private SessionManager sessionManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 初始化
        executorService = Executors.newSingleThreadExecutor();
        userDao = AppDatabase.getInstance(this).userDao();
        sessionManager = new SessionManager(this);
        
        // 检查用户是否已登录
        if (sessionManager.isLoggedIn()) {
            navigateToMain();
            return;
        }
        
        // 设置点击事件
        binding.buttonLogin.setOnClickListener(v -> attemptLogin());
        binding.textViewRegister.setOnClickListener(v -> navigateToRegister());
        binding.textViewForgotPassword.setOnClickListener(v -> navigateToForgotPassword());
        
        // 检查记住密码
        SharedPreferences prefs = getSharedPreferences("login_prefs", MODE_PRIVATE);
        if (prefs.getBoolean("remember_me", false)) {
            binding.editTextUsername.setText(prefs.getString("username", ""));
            binding.editTextPassword.setText(prefs.getString("password", ""));
            binding.checkBoxRemember.setChecked(true);
        }
    }
    
    private void attemptLogin() {
        String usernameOrPhone = binding.editTextUsername.getText().toString().trim();
        String password = binding.editTextPassword.getText().toString().trim();
        
        if (usernameOrPhone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.buttonLogin.setEnabled(false);
        
        executorService.execute(() -> {
            User user = userDao.getUserByUsernameOrPhone(usernameOrPhone);
            
            runOnUiThread(() -> {
                binding.buttonLogin.setEnabled(true);
                
                if (user != null && user.getPassword().equals(password)) {
                    // 保存登录状态
                    sessionManager.setLogin(true);
                    sessionManager.setUserId(user.getId());
                    
                    // 保存记住我的状态
                    if (binding.checkBoxRemember.isChecked()) {
                        SharedPreferences.Editor editor = getSharedPreferences("login_prefs", MODE_PRIVATE).edit();
                        editor.putBoolean("remember_me", true);
                        editor.putString("username", usernameOrPhone);
                        editor.putString("password", password);
                        editor.apply();
                    } else {
                        // 清除保存的信息
                        SharedPreferences.Editor editor = getSharedPreferences("login_prefs", MODE_PRIVATE).edit();
                        editor.clear();
                        editor.apply();
                    }
                    
                    Toast.makeText(this, R.string.login_success, Toast.LENGTH_SHORT).show();
                    navigateToMain();
                } else {
                    Toast.makeText(this, R.string.login_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    
    private void navigateToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }
    
    private void navigateToForgotPassword() {
        Intent intent = new Intent(this, ForgotPasswordActivity.class);
        startActivity(intent);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
} 