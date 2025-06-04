package com.inf.myjavavideo.ui.profile;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.UserDao;
import com.inf.myjavavideo.data.model.User;
import com.inf.myjavavideo.databinding.ActivityEditProfileBinding;
import com.inf.myjavavideo.databinding.DialogAvatarPickerBinding;
import com.inf.myjavavideo.utils.SessionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditProfileActivity extends AppCompatActivity {

    private static final int REQUEST_IMAGE_PICK = 100;
    
    private ActivityEditProfileBinding binding;
    private ExecutorService executorService;
    private UserDao userDao;
    private SessionManager sessionManager;
    private User currentUser;
    private String selectedAvatarResource; // 存储选择的头像资源ID
    
    // 头像资源映射
    private Map<Integer, Integer> avatarResources;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 设置标题栏
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.edit_profile);
        }
        
        // 初始化
        executorService = Executors.newSingleThreadExecutor();
        userDao = AppDatabase.getInstance(this).userDao();
        sessionManager = new SessionManager(this);
        
        // 初始化头像资源映射
        initAvatarResources();
        
        // 加载用户数据
        loadUserData();
        
        // 设置点击事件
        binding.imageAvatar.setOnClickListener(v -> showAvatarPickerDialog());
        binding.buttonSave.setOnClickListener(v -> saveUserData());
    }
    
    private void initAvatarResources() {
        avatarResources = new HashMap<>();
        avatarResources.put(R.id.avatar_frame_1, R.drawable.avatar_graduate_male);
        avatarResources.put(R.id.avatar_frame_2, R.drawable.avatar_graduate_male_2);
        avatarResources.put(R.id.avatar_frame_3, R.drawable.avatar_developer_male);
        avatarResources.put(R.id.avatar_frame_4, R.drawable.avatar_developer_female);
        avatarResources.put(R.id.avatar_frame_5, R.drawable.avatar_doctor_male);
        avatarResources.put(R.id.avatar_frame_6, R.drawable.avatar_doctor_female);
        avatarResources.put(R.id.avatar_frame_7, R.drawable.avatar_teacher_male);
        avatarResources.put(R.id.avatar_frame_8, R.drawable.avatar_teacher_female);
    }
    
    private void loadUserData() {
        int userId = sessionManager.getUserId();
        if (userId == -1) {
            finish();
            return;
        }
        
        System.out.println("开始加载用户数据，用户ID: " + userId);
        
        executorService.execute(() -> {
            currentUser = userDao.getUserById(userId);
            
            if (currentUser != null) {
                System.out.println("用户数据加载成功: " + currentUser.getUsername());
                System.out.println("头像数据: " + currentUser.getAvatar());
                
                runOnUiThread(() -> {
                    binding.editUsername.setText(currentUser.getUsername());
                    binding.editPhone.setText(currentUser.getPhone());
                    binding.editEmail.setText(currentUser.getEmail());
                    
                    // 加载头像
                    if (currentUser.getAvatar() != null && !currentUser.getAvatar().isEmpty()) {
                        try {
                            // 尝试将头像字符串转换为资源ID
                            int resourceId = Integer.parseInt(currentUser.getAvatar());
                            System.out.println("加载头像资源ID: " + resourceId);
                            
                            // 加载新头像
                            Glide.with(this)
                                    .load(resourceId)
                                    .transition(DrawableTransitionOptions.withCrossFade())
                                    .placeholder(R.drawable.ic_profile)
                                    .into(binding.imageAvatar);
                                    
                            selectedAvatarResource = currentUser.getAvatar();
                        } catch (NumberFormatException e) {
                            System.out.println("头像数据格式不正确: " + currentUser.getAvatar());
                            // 如果不是资源ID（可能是旧版本的URI字符串），使用默认头像
                            binding.imageAvatar.setImageResource(R.drawable.ic_profile);
                        }
                    } else {
                        System.out.println("没有头像数据");
                        binding.imageAvatar.setImageResource(R.drawable.ic_profile);
                    }
                });
            } else {
                System.out.println("无法加载用户数据");
                runOnUiThread(() -> {
                    Toast.makeText(this, "无法加载用户数据", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }
    
    private void showAvatarPickerDialog() {
        // 使用ViewBinding加载对话框布局
        DialogAvatarPickerBinding dialogBinding = DialogAvatarPickerBinding.inflate(getLayoutInflater());
        View dialogView = dialogBinding.getRoot();
        
        // 创建对话框
        Dialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setTitle("选择头像")
                .setNegativeButton("取消", null)
                .create();
        
        // 设置头像点击事件
        for (Map.Entry<Integer, Integer> entry : avatarResources.entrySet()) {
            FrameLayout frameLayout = dialogView.findViewById(entry.getKey());
            if (frameLayout != null) {
                // 设置当前选中状态的视觉反馈
                int resourceId = entry.getValue();
                if (selectedAvatarResource != null && 
                    selectedAvatarResource.equals(String.valueOf(resourceId))) {
                    frameLayout.setBackgroundResource(R.drawable.selected_avatar_background);
                }
                
                frameLayout.setOnClickListener(v -> {
                    int resId = entry.getValue();
                    selectedAvatarResource = String.valueOf(resId);
                    
                    // 更新头像显示
                    Glide.with(this)
                            .load(resId)
                            .transition(DrawableTransitionOptions.withCrossFade())
                            .into(binding.imageAvatar);
                    
                    // 显示选择成功提示
                    Toast.makeText(this, "已选择新头像", Toast.LENGTH_SHORT).show();
                    
                    // 关闭对话框
                    dialog.dismiss();
                });
            }
        }
        
        dialog.show();
    }
    
    private void saveUserData() {
        if (currentUser == null) {
            return;
        }
        
        String username = binding.editUsername.getText().toString().trim();
        String phone = binding.editPhone.getText().toString().trim();
        String email = binding.editEmail.getText().toString().trim();
        
        if (username.isEmpty()) {
            binding.editUsername.setError("请输入用户名");
            return;
        }
        
        // 更新用户数据
        currentUser.setUsername(username);
        currentUser.setPhone(phone);
        currentUser.setEmail(email);
        if (selectedAvatarResource != null) {
            currentUser.setAvatar(selectedAvatarResource);
            // 添加日志输出，方便调试
            System.out.println("保存头像资源ID: " + selectedAvatarResource);
        }
        
        // 显示保存中的提示
        Toast.makeText(this, "正在保存数据...", Toast.LENGTH_SHORT).show();
        
        executorService.execute(() -> {
            try {
                // 确保操作完成
                userDao.update(currentUser);
                
                // 验证更新是否成功
                User updatedUser = userDao.getUserById(currentUser.getId());
                System.out.println("验证更新 - 用户ID: " + updatedUser.getId());
                System.out.println("验证更新 - 用户名: " + updatedUser.getUsername());
                System.out.println("验证更新 - 头像: " + updatedUser.getAvatar());
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "个人资料已更新", Toast.LENGTH_SHORT).show();
                    
                    // 设置结果，通知主界面更新头像
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("AVATAR_UPDATED", true);
                    resultIntent.putExtra("AVATAR_RESOURCE", selectedAvatarResource);
                    setResult(RESULT_OK, resultIntent);
                    
                    finish();
                });
            } catch (Exception e) {
                System.out.println("保存用户数据失败: " + e.getMessage());
                e.printStackTrace();
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
} 