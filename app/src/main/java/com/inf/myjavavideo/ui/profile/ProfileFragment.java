package com.inf.myjavavideo.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.UserDao;
import com.inf.myjavavideo.data.model.User;
import com.inf.myjavavideo.databinding.FragmentProfileBinding;
import com.inf.myjavavideo.ui.auth.LoginActivity;
import com.inf.myjavavideo.ui.profile.EditProfileActivity;
import com.inf.myjavavideo.ui.profile.SettingsActivity;
import com.inf.myjavavideo.utils.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ExecutorService executorService;
    private UserDao userDao;
    private SessionManager sessionManager;
    private User currentUser;
    private static final int REQUEST_EDIT_PROFILE = 1001;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        userDao = AppDatabase.getInstance(requireContext()).userDao();
        sessionManager = new SessionManager(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        loadUserData();
        verifyDatabaseUser();
        
        // 设置点击事件
        binding.layoutLogout.setOnClickListener(v -> logout());
        binding.layoutEditProfile.setOnClickListener(v -> editProfile());
        binding.layoutSettings.setOnClickListener(v -> openSettings());
    }
    
    @Override
    public void onResume() {
        super.onResume();
        // 不要在每次onResume时都加载数据，这会导致闪烁
        // 只在收到结果或第一次创建时加载数据
    }
    
    private void loadUserData() {
        int userId = sessionManager.getUserId();
        if (userId == -1) {
            logout();
            return;
        }
        
        System.out.println("ProfileFragment - 开始加载用户数据，用户ID: " + userId);
        
        executorService.execute(() -> {
            currentUser = userDao.getUserById(userId);
            
            if (currentUser != null) {
                System.out.println("ProfileFragment - 用户数据加载成功: " + currentUser.getUsername());
                System.out.println("ProfileFragment - 头像数据: " + currentUser.getAvatar());
                
                requireActivity().runOnUiThread(() -> {
                    binding.textUsername.setText(currentUser.getUsername());
                    binding.textPhone.setText(currentUser.getPhone());
                    
                    // 加载用户头像
                    if (currentUser.getAvatar() != null && !currentUser.getAvatar().isEmpty()) {
                        try {
                            // 尝试将头像字符串转换为资源ID
                            int resourceId = Integer.parseInt(currentUser.getAvatar());
                            System.out.println("ProfileFragment - 加载头像资源ID: " + resourceId);
                            
                            // 加载新头像
                            Glide.with(requireContext())
                                    .load(resourceId)
                                    .placeholder(R.drawable.ic_profile)
                                    .error(R.drawable.ic_profile)
                                    .thumbnail(0.5f)  // 添加缩略图预加载
                                    .transition(DrawableTransitionOptions.withCrossFade())  // 添加淡入淡出过渡
                                    .into(binding.imageAvatar);
                        } catch (NumberFormatException e) {
                            System.out.println("ProfileFragment - 头像数据格式不正确: " + currentUser.getAvatar());
                            // 如果不是资源ID（可能是旧版本的URI字符串），尝试直接加载URI
                            try {
                                Uri uri = Uri.parse(currentUser.getAvatar());
                                Glide.with(requireContext())
                                        .load(uri)
                                        .placeholder(R.drawable.ic_profile)
                                        .error(R.drawable.ic_profile)
                                        .thumbnail(0.5f)
                                        .transition(DrawableTransitionOptions.withCrossFade())
                                        .into(binding.imageAvatar);
                            } catch (Exception ex) {
                                // 加载失败，显示默认头像
                                binding.imageAvatar.setImageResource(R.drawable.ic_profile);
                            }
                        }
                    } else {
                        System.out.println("ProfileFragment - 没有头像数据");
                        // 如果没有头像，显示默认头像
                        binding.imageAvatar.setImageResource(R.drawable.ic_profile);
                    }
                });
            } else {
                System.out.println("ProfileFragment - 无法加载用户数据");
                logout();
            }
        });
    }
    
    private void logout() {
        sessionManager.logout();
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
    
    private void editProfile() {
        // 打开编辑个人资料界面
        Intent intent = new Intent(requireContext(), EditProfileActivity.class);
        startActivityForResult(intent, REQUEST_EDIT_PROFILE);
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == Activity.RESULT_OK && data != null) {
            boolean avatarUpdated = data.getBooleanExtra("AVATAR_UPDATED", false);
            if (avatarUpdated) {
                String avatarResource = data.getStringExtra("AVATAR_RESOURCE");
                System.out.println("ProfileFragment - 收到更新的头像资源: " + avatarResource);
                
                // 立即重新加载用户数据以显示新头像
                loadUserData();
            }
        }
    }
    
    private void openSettings() {
        // 打开设置界面
        Intent intent = new Intent(requireContext(), SettingsActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // 添加一个验证方法来检查数据库中的用户数据
    private void verifyDatabaseUser() {
        int userId = sessionManager.getUserId();
        if (userId != -1) {
            executorService.execute(() -> {
                // 直接从数据库查询最新数据
                User dbUser = userDao.getUserById(userId);
                if (dbUser != null) {
                    System.out.println("数据库验证 - 用户ID: " + dbUser.getId());
                    System.out.println("数据库验证 - 用户名: " + dbUser.getUsername());
                    System.out.println("数据库验证 - 头像: " + dbUser.getAvatar());
                } else {
                    System.out.println("数据库验证 - 无法找到用户ID: " + userId);
                }
            });
        }
    }
} 