package com.inf.myjavavideo.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.UserDao;
import com.inf.myjavavideo.data.model.User;
import com.inf.myjavavideo.databinding.ActivityForgotPasswordBinding;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForgotPasswordActivity extends AppCompatActivity {
    
    private ActivityForgotPasswordBinding binding;
    private ExecutorService executorService;
    private UserDao userDao;
    private User currentUser = null;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 初始化
        executorService = Executors.newSingleThreadExecutor();
        userDao = AppDatabase.getInstance(this).userDao();
        
        // 初始状态下隐藏密码修改区域
        binding.passwordResetLayout.setVisibility(View.GONE);
        
        // 设置点击事件
        binding.buttonFindAccount.setOnClickListener(v -> findAccount());
        binding.buttonResetPassword.setOnClickListener(v -> resetPassword());
        binding.textViewBackToLogin.setOnClickListener(v -> finish());
    }
    
    private void findAccount() {
        String phone = binding.editTextPhone.getText().toString().trim();
        
        if (phone.isEmpty()) {
            Toast.makeText(this, R.string.enter_phone_to_find, Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.buttonFindAccount.setEnabled(false);
        
        executorService.execute(() -> {
            // 检查手机号是否存在
            User user = userDao.getUserByPhone(phone);
            currentUser = user;
            
            runOnUiThread(() -> {
                binding.buttonFindAccount.setEnabled(true);
                
                if (user != null) {
                    // 显示找到的用户名和密码重置区域
                    binding.textFoundUsername.setText(getString(R.string.username_found, user.getUsername()));
                    binding.passwordResetLayout.setVisibility(View.VISIBLE);
                    binding.textResetInstructions.setText(getString(R.string.set_new_password_for, user.getUsername()));
                } else {
                    Toast.makeText(this, R.string.account_not_found, Toast.LENGTH_SHORT).show();
                    binding.passwordResetLayout.setVisibility(View.GONE);
                }
            });
        });
    }
    
    private void resetPassword() {
        if (currentUser == null) {
            Toast.makeText(this, R.string.please_find_account_first, Toast.LENGTH_SHORT).show();
            return;
        }
        
        String newPassword = binding.editTextNewPassword.getText().toString().trim();
        String confirmPassword = binding.editTextConfirmPassword.getText().toString().trim();
        
        if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, R.string.enter_new_passwords, Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            Toast.makeText(this, R.string.passwords_mismatch, Toast.LENGTH_SHORT).show();
            return;
        }
        
        binding.buttonResetPassword.setEnabled(false);
        
        executorService.execute(() -> {
            // 更新密码
            currentUser.setPassword(newPassword);
            userDao.update(currentUser);
            
            runOnUiThread(() -> {
                binding.buttonResetPassword.setEnabled(true);
                Toast.makeText(this, R.string.password_reset_success, Toast.LENGTH_SHORT).show();
                finish();
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