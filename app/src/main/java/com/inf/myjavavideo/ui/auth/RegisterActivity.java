package com.inf.myjavavideo.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.UserDao;
import com.inf.myjavavideo.data.model.User;
import com.inf.myjavavideo.databinding.ActivityRegisterBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {
    
    private ActivityRegisterBinding binding;
    private ExecutorService executorService;
    private UserDao userDao;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 初始化
        executorService = Executors.newSingleThreadExecutor();
        userDao = AppDatabase.getInstance(this).userDao();
        
        // 设置点击事件
        binding.buttonRegister.setOnClickListener(v -> attemptRegister());
        binding.textViewLogin.setOnClickListener(v -> finish());
    }
    
    private void attemptRegister() {
        String username = binding.editTextUsername.getText().toString().trim();
        String phone = binding.editTextPhone.getText().toString().trim();
        String password = binding.editTextPassword.getText().toString().trim();
        String confirmPassword = binding.editTextConfirmPassword.getText().toString().trim();
        
        // 验证输入
        if (username.isEmpty() || phone.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, R.string.passwords_not_match, Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.buttonRegister.setEnabled(false);
        
        executorService.execute(() -> {
            // 检查用户名或手机号是否已存在
            User existingUserByUsername = userDao.getUserByUsername(username);
            User existingUserByPhone = userDao.getUserByPhone(phone);
            
            if (existingUserByUsername != null || existingUserByPhone != null) {
                runOnUiThread(() -> {
                    binding.buttonRegister.setEnabled(true);
                    if (existingUserByUsername != null) {
                        Toast.makeText(this, "用户名已存在", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "手机号已注册", Toast.LENGTH_SHORT).show();
                    }
                });
                return;
            }
            
            // 创建新用户
            User newUser = new User(username, password, phone);
            long userId = userDao.insert(newUser);
            
            runOnUiThread(() -> {
                binding.buttonRegister.setEnabled(true);
                if (userId > 0) {
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show();
                    // 注册成功，返回登录页面
                    finish();
                } else {
                    Toast.makeText(this, R.string.register_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
} 