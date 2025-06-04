package com.inf.myjavavideo.ui.videos;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.VideoDao;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.databinding.FragmentVideosBinding;
import com.inf.myjavavideo.ui.adapters.VideoGridAdapter;
import com.inf.myjavavideo.ui.player.MediaPlayerActivity;
import com.inf.myjavavideo.utils.MediaStoreHelper;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideosFragment extends Fragment implements VideoGridAdapter.OnVideoClickListener {

    private static final int REQUEST_VIDEO_PICK = 100;
    private static final int REQUEST_FILE_PICK = 101;
    private static final int REQUEST_STORAGE_PERMISSION = 102;
    private static final int REQUEST_MANAGE_STORAGE = 103;
    
    private FragmentVideosBinding binding;
    private ExecutorService executorService;
    private VideoDao videoDao;
    private VideoGridAdapter videosAdapter;
    private List<Video> videoList = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        videoDao = AppDatabase.getInstance(requireContext()).videoDao();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentVideosBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        setupSearchView();
        
        // 设置添加视频按钮
        binding.fabAddVideo.setOnClickListener(v -> checkPermissionsAndShowOptions());
        
        loadVideos();
    }

    private void checkPermissionsAndShowOptions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上，请求管理所有文件的权限
            if (!Environment.isExternalStorageManager()) {
                showAllFilesAccessPermissionDialog();
            } else {
                showVideoImportOptions();
            }
        } else {
            // Android 10及以下，请求常规存储权限
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
                    != PackageManager.PERMISSION_GRANTED) {
                requestStoragePermission();
            } else {
                showVideoImportOptions();
            }
        }
    }
    
    private void showAllFilesAccessPermissionDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("需要访问所有文件的权限")
                .setMessage("为了访问SD卡上的视频文件，需要授予应用管理所有文件的权限。")
                .setPositiveButton("授权", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    Uri uri = Uri.fromParts("package", requireContext().getPackageName(), null);
                    intent.setData(uri);
                    startActivityForResult(intent, REQUEST_MANAGE_STORAGE);
                })
                .setNegativeButton("取消", (dialog, which) -> dialog.dismiss())
                .show();
    }
    
    private void requestStoragePermission() {
        requestPermissions(
                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                REQUEST_STORAGE_PERMISSION
        );
    }

    private void setupRecyclerView() {
        videosAdapter = new VideoGridAdapter(requireContext(), videoList, this);
        binding.recyclerVideos.setAdapter(videosAdapter);
    }
    
    private void setupSearchView() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchVideos(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText.isEmpty()) {
                    loadVideos();
                }
                return true;
            }
        });
        
        // 添加长按搜索框清除所有content URI视频的功能
        binding.searchView.setOnLongClickListener(v -> {
            showClearContentUrisDialog();
            return true;
        });
    }

    private void loadVideos() {
        executorService.execute(() -> {
            // 只加载数据库中已有的视频，不再自动导入设备中的所有视频
            final List<Video> videos = videoDao.getAllVideos();
            
            // 检查数据库中的content URI视频是否可访问，移除不可访问的视频
            checkAndCleanContentUris(videos);
            
            requireActivity().runOnUiThread(() -> {
                updateVideos(videos);
                
                // 检查是否有视频
                binding.textNoVideos.setVisibility(
                        videos.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
    
    // 检查并清理不可访问的content URI
    private void checkAndCleanContentUris(List<Video> videos) {
        List<Video> invalidVideos = new ArrayList<>();
        
        for (Video video : videos) {
            if (video.getPath() != null && video.getPath().startsWith("content://") 
                    && "content".equals(video.getSourceType())) {
                try {
                    Uri uri = Uri.parse(video.getPath());
                    
                    // 尝试获取持久权限
                    try {
                        requireContext().getContentResolver().takePersistableUriPermission(
                                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (SecurityException e) {
                        Log.w("VideosFragment", "无法获取持久权限: " + e.getMessage());
                    }
                    
                    // 尝试检查URI是否可访问
                    boolean isAccessible = false;
                    try {
                        // 使用openInputStream检查URI是否可访问
                        InputStream stream = requireContext().getContentResolver().openInputStream(uri);
                        if (stream != null) {
                            stream.close();
                            isAccessible = true;
                        }
                    } catch (Exception e) {
                        Log.e("VideosFragment", "URI不可访问: " + uri + ", 错误: " + e.getMessage());
                    }
                    
                    if (!isAccessible) {
                        invalidVideos.add(video);
                        Log.d("VideosFragment", "标记无效URI: " + video.getPath());
                    }
                } catch (Exception e) {
                    invalidVideos.add(video);
                    Log.e("VideosFragment", "处理URI出错: " + video.getPath() + ", 错误: " + e.getMessage());
                }
            }
        }
        
        // 如果有无效的视频，显示提示并提供重新导入选项
        if (!invalidVideos.isEmpty()) {
            for (Video video : invalidVideos) {
                videos.remove(video);
                
                // 从数据库中删除
                try {
                    videoDao.delete(video);
                    Log.d("VideosFragment", "已删除无效视频: " + video.getTitle());
                } catch (Exception e) {
                    Log.e("VideosFragment", "删除视频失败: " + e.getMessage());
                }
            }
            
            // 在UI线程显示提示
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), 
                        "已移除" + invalidVideos.size() + "个无法访问的视频", 
                        Toast.LENGTH_LONG).show();
            });
        }
    }
    
    private void searchVideos(String query) {
        executorService.execute(() -> {
            List<Video> searchResults = videoDao.searchVideos(query);
            
            requireActivity().runOnUiThread(() -> {
                updateVideos(searchResults);
                
                // 检查是否有搜索结果
                binding.textNoVideos.setVisibility(
                        searchResults.isEmpty() ? View.VISIBLE : View.GONE);
                
                if (searchResults.isEmpty()) {
                    binding.textNoVideos.setText("没有找到匹配的视频");
                } else {
                    binding.textNoVideos.setText(R.string.no_videos_found);
                }
            });
        });
    }

    private void updateVideos(List<Video> videos) {
        videoList.clear();
        videoList.addAll(videos);
        videosAdapter.notifyDataSetChanged();
    }
    
    private void showVideoImportOptions() {
        String[] options = {
            getString(R.string.from_device),
            getString(R.string.from_file_manager),
            getString(R.string.from_sd_card)
        };
        
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.import_video)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openVideoPickerActivity();
                    } else if (which == 1) {
                        openFileChooser();
                    } else {
                        openSdCardBrowser();
                    }
                })
                .show();
    }
    
    private void openVideoPickerActivity() {
        Intent intent = new Intent(requireContext(), VideoPickerActivity.class);
        startActivityForResult(intent, REQUEST_VIDEO_PICK);
    }
    
    private void openFileChooser() {
        // 使用ACTION_OPEN_DOCUMENT而不是ACTION_GET_CONTENT，以获得持久权限
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        
        // 增加额外的mime类型支持
        String[] mimeTypes = {"video/*", "video/mp4", "video/avi", "video/mkv", "video/mov", "video/3gp", "video/flv"};
        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        
        // 请求持久权限
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION 
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION 
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        
        try {
            startActivityForResult(intent, REQUEST_FILE_PICK);
        } catch (Exception e) {
            Log.e("VideosFragment", "无法启动文件选择器: " + e.getMessage());
            Toast.makeText(requireContext(), "无法打开文件选择器: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            
            // 如果ACTION_OPEN_DOCUMENT失败，尝试使用ACTION_GET_CONTENT作为备选
            try {
                Intent fallbackIntent = new Intent(Intent.ACTION_GET_CONTENT);
                fallbackIntent.setType("video/*");
                fallbackIntent.addCategory(Intent.CATEGORY_OPENABLE);
                fallbackIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
                fallbackIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                startActivityForResult(Intent.createChooser(fallbackIntent, "选择视频"), REQUEST_FILE_PICK);
            } catch (Exception e2) {
                Log.e("VideosFragment", "无法启动备选文件选择器: " + e2.getMessage());
                Toast.makeText(requireContext(), "无法打开任何文件选择器，请检查权限", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void openSdCardBrowser() {
        // 检查SD卡是否可用
        String sdCardPath = getExternalSdCardPath();
        if (sdCardPath != null) {
            // 创建一个Intent，打开一个自定义的文件浏览器Activity
            Intent intent = new Intent(requireContext(), FileBrowserActivity.class);
            intent.putExtra("ROOT_PATH", sdCardPath);
            startActivityForResult(intent, REQUEST_FILE_PICK);
        } else {
            Toast.makeText(requireContext(), "未检测到SD卡", Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getExternalSdCardPath() {
        File[] externalStorageFiles = ContextCompat.getExternalFilesDirs(requireContext(), null);
        if (externalStorageFiles.length > 1 && externalStorageFiles[1] != null) {
            // 第二个路径通常是SD卡路径（如果有SD卡的话）
            String path = externalStorageFiles[1].getAbsolutePath();
            // 去掉Android/data/包名/files部分，得到SD卡根目录
            int index = path.indexOf("Android/data");
            if (index > 0) {
                return path.substring(0, index);
            }
        }
        return null;
    }
    
    private void importVideoFromUri(Uri uri, String title, String filePath) {
        try {
            // 获取视频时长
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            
            try {
                if (uri.toString().startsWith("content:")) {
                    retriever.setDataSource(requireContext(), uri);
                } else {
                    retriever.setDataSource(filePath);
                }
                
                // 获取视频时长（毫秒）
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                long durationMs = durationStr != null ? Long.parseLong(durationStr) : 0;
                
                // 如果首次尝试获取时长失败，尝试使用MediaPlayer
                if (durationMs == 0) {
                    Log.d("VideosFragment", "通过MediaMetadataRetriever获取时长失败，尝试使用MediaPlayer");
                    MediaPlayer mp = new MediaPlayer();
                    try {
                        if (uri.toString().startsWith("content:")) {
                            mp.setDataSource(requireContext(), uri);
                        } else {
                            mp.setDataSource(filePath);
                        }
                        mp.prepare();
                        durationMs = mp.getDuration();
                    } catch (Exception e) {
                        Log.e("VideosFragment", "通过MediaPlayer获取时长失败: " + e.getMessage());
                    } finally {
                        mp.release();
                    }
                }
                
                Log.d("VideosFragment", "视频时长: " + durationMs + "ms");
                
                // 创建Video对象
                Video video = new Video();
                video.setTitle(title);
                video.setPath(uri.toString());
                video.setDuration(durationMs); // 以毫秒为单位设置时长
                
                // 为视频生成缩略图
                String videoId = String.valueOf(System.currentTimeMillis()); // 临时ID
                String thumbnailPath = MediaStoreHelper.generateThumbnail(requireContext(), uri.toString(), videoId);
                video.setThumbnailPath(thumbnailPath);
                
                // 保存到数据库
                long insertedId = videoDao.insert(video);
                
                // 如果使用了临时ID生成缩略图，且生成成功，则用正确的ID重命名缩略图
                if (!thumbnailPath.isEmpty() && insertedId > 0) {
                    File oldFile = new File(thumbnailPath);
                    File thumbnailDir = new File(requireContext().getFilesDir(), "thumbnails");
                    File newFile = new File(thumbnailDir, insertedId + ".jpg");
                    
                    if (oldFile.renameTo(newFile)) {
                        // 更新缩略图路径
                        video.setId((int)insertedId);
                        video.setThumbnailPath(newFile.getAbsolutePath());
                        videoDao.update(video);
                        Log.d("VideosFragment", "已更新缩略图路径: " + newFile.getAbsolutePath());
                    }
                }
                
                // 提示成功导入
                Toast.makeText(requireContext(), "已成功导入视频: " + title, Toast.LENGTH_SHORT).show();
                
                // 刷新列表
                loadVideos();
                
            } finally {
                retriever.release();
            }
            
        } catch (Exception e) {
            Log.e("VideosFragment", "导入视频失败: " + e.getMessage());
            Toast.makeText(requireContext(), "导入视频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private String getPathFromUri(Uri uri) {
        String path = null;
        
        try {
            String scheme = uri.getScheme();
            String uriString = uri.toString();
            Log.d("VideosFragment", "处理URI: " + uriString + ", 协议: " + scheme);
            
            // 直接处理file协议
            if ("file".equals(scheme)) {
                path = uri.getPath();
                Log.d("VideosFragment", "文件协议，路径: " + path);
                return path;
            }
            
            if ("content".equals(scheme)) {
                Log.d("VideosFragment", "内容协议，尝试获取真实路径");
                String[] projection = {MediaStore.MediaColumns.DATA};
                Cursor cursor = null;
                try {
                    cursor = requireContext().getContentResolver().query(uri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        try {
                            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
                            path = cursor.getString(columnIndex);
                            Log.d("VideosFragment", "从内容提供者获取路径: " + path);
                        } catch (Exception e) {
                            Log.e("VideosFragment", "获取DATA列失败: " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    Log.e("VideosFragment", "查询URI失败: " + e.getMessage());
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
                
                // 如果获取不到真实路径，直接使用内容URI字符串
                if (path == null) {
                    path = uriString;
                    Log.d("VideosFragment", "无法获取真实路径，使用URI字符串: " + path);
                }
            }
            
            // 如果以上方法都无法获取路径，直接使用URI字符串
            if (path == null) {
                path = uriString;
                Log.d("VideosFragment", "未能识别的协议，使用URI字符串: " + path);
                // 如果是file协议，去掉前缀
                if (path.startsWith("file://")) {
                    path = path.substring(7);
                    Log.d("VideosFragment", "去掉file://前缀，路径: " + path);
                }
            }
        } catch (Exception e) {
            Log.e("VideosFragment", "获取路径异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return path;
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showVideoImportOptions();
            } else {
                Toast.makeText(requireContext(), "需要存储权限才能导入视频", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_VIDEO_PICK && resultCode == Activity.RESULT_OK) {
            // 从VideoPickerActivity返回，重新加载视频列表
            loadVideos();
        } else if (requestCode == REQUEST_FILE_PICK && resultCode == Activity.RESULT_OK && data != null) {
            // 从文件选择器返回
            Uri selectedVideoUri = data.getData();
            if (selectedVideoUri != null) {
                Log.d("VideosFragment", "选择的视频URI: " + selectedVideoUri);
                
                // 检查是否能实际访问这个URI
                boolean canAccess = false;
                try {
                    InputStream inputStream = requireContext().getContentResolver().openInputStream(selectedVideoUri);
                    if (inputStream != null) {
                        inputStream.close();
                        canAccess = true;
                    }
                } catch (Exception e) {
                    Log.e("VideosFragment", "无法访问选择的URI: " + e.getMessage());
                }
                
                if (!canAccess) {
                    Toast.makeText(requireContext(), "无法访问所选视频，请尝试重新选择", Toast.LENGTH_LONG).show();
                    // 如果无法访问，重新打开文件选择器
                    openFileChooser();
                    return;
                }
                
                // 获取持久性权限
                if (selectedVideoUri.toString().startsWith("content:")) {
                    try {
                        // 获取永久读取权限
                        requireContext().getContentResolver().takePersistableUriPermission(
                                selectedVideoUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        );
                        Log.d("VideosFragment", "已获取持久性权限: " + selectedVideoUri);
                    } catch (SecurityException e) {
                        Log.e("VideosFragment", "获取持久性权限失败: " + e.getMessage());
                        // 如果无法获取持久权限，显示警告但继续尝试导入
                        Toast.makeText(requireContext(), "警告：无法获取持久权限，视频可能在应用重启后无法访问", Toast.LENGTH_LONG).show();
                    }
                }
                
                // 检查是否有额外信息（从FileBrowserActivity返回）
                String filePath = data.getStringExtra("FILE_PATH");
                if (filePath != null) {
                    // 我们有直接的文件路径，使用它创建一个视频对象
                    executorService.execute(() -> {
                        try {
                            String fileName = data.getStringExtra("FILE_NAME");
                            long fileSize = data.getLongExtra("FILE_SIZE", 0);
                            long duration = data.getLongExtra("FILE_DURATION", 0);
                            
                            // 检查视频是否已经存在
                            Video existingVideo = null;
                            
                            // 先按照路径查询
                            existingVideo = videoDao.getVideoByPath(filePath);
                            
                            // 如果是content URI，还需要检查原始URI
                            if (existingVideo == null && selectedVideoUri != null) {
                                existingVideo = videoDao.getVideoByPath(selectedVideoUri.toString());
                            }
                            
                            if (existingVideo == null) {
                                // 创建新的视频对象
                                Video video = new Video();
                                video.setTitle(fileName != null ? fileName : "未命名视频");
                                
                                // 对于file://开头的URI，保存原始文件路径
                                // 对于content://开头的URI，保存URI字符串
                                if (selectedVideoUri != null && "content".equals(selectedVideoUri.getScheme())) {
                                    video.setPath(selectedVideoUri.toString());
                                } else {
                                    video.setPath(filePath);
                                }
                                
                                video.setSize(fileSize);
                                // 设置视频时长
                                video.setDuration(duration);
                                video.setDateAdded(System.currentTimeMillis() / 1000);
                                // 设置来源类型为content
                                video.setSourceType("content");
                                
                                // 生成缩略图
                                String videoId = String.valueOf(System.currentTimeMillis());
                                String thumbnailPath = MediaStoreHelper.generateThumbnail(requireContext(), filePath, videoId);
                                if (thumbnailPath != null && !thumbnailPath.isEmpty()) {
                                    video.setThumbnailPath(thumbnailPath);
                                }
                                
                                // 保存到数据库
                                long videoId2 = videoDao.insert(video);
                                
                                // 如果生成了缩略图，使用正确的ID重命名
                                if (!thumbnailPath.isEmpty() && videoId2 > 0) {
                                    File oldFile = new File(thumbnailPath);
                                    File thumbnailDir = new File(requireContext().getFilesDir(), "thumbnails");
                                    File newFile = new File(thumbnailDir, videoId2 + ".jpg");
                                    
                                    if (oldFile.renameTo(newFile)) {
                                        // 更新缩略图路径
                                        video.setId((int)videoId2);
                                        video.setThumbnailPath(newFile.getAbsolutePath());
                                        videoDao.update(video);
                                        Log.d("VideosFragment", "已更新缩略图路径: " + newFile.getAbsolutePath());
                                    }
                                }
                                
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "视频已导入: " + video.getTitle(), Toast.LENGTH_SHORT).show();
                                    loadVideos();
                                });
                            } else {
                                // 创建一个final副本供lambda表达式使用
                                final Video finalExistingVideo = existingVideo;
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireContext(), "该视频已存在: " + (fileName != null ? fileName : finalExistingVideo.getTitle()), Toast.LENGTH_SHORT).show();
                                });
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "导入视频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                } else {
                    // 没有额外信息，直接使用URI添加视频
                    // 不再尝试将URI转换为本地路径
                    importVideoFromUriDirectly(selectedVideoUri);
                }
            }
        } else if (requestCode == REQUEST_MANAGE_STORAGE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                showVideoImportOptions();
            } else {
                Toast.makeText(requireContext(), "需要文件管理权限才能访问SD卡", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onVideoClick(Video video) {
        // 打开视频播放器（使用MediaPlayer）
        Intent intent = new Intent(requireContext(), com.inf.myjavavideo.ui.player.MediaPlayerActivity.class);
        intent.putExtra("video_id", video.getId());
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        
        // 释放适配器资源
        if (videosAdapter != null) {
            videosAdapter.release();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    // 直接使用URI导入视频，不尝试转换为文件路径
    private void importVideoFromUriDirectly(Uri uri) {
        executorService.execute(() -> {
            try {
                // 记录URI信息以便调试
                String uriString = uri.toString();
                String scheme = uri.getScheme();
                
                Log.d("VideosFragment", "直接使用URI导入: " + uriString + ", 协议: " + scheme);
                
                // 获取视频信息
                String displayName = "";
                long size = 0;
                long duration = 0;
                
                // 尝试从内容提供者获取信息
                try {
                    String[] projection = {
                            MediaStore.MediaColumns.DISPLAY_NAME,
                            MediaStore.MediaColumns.SIZE,
                            MediaStore.Video.Media.DURATION
                    };
                    
                    Cursor cursor = requireContext().getContentResolver().query(uri, projection, null, null, null);
                    if (cursor != null && cursor.moveToFirst()) {
                        try {
                            int displayNameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
                            if (displayNameIndex != -1) {
                                displayName = cursor.getString(displayNameIndex);
                            }
                            
                            int sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                            if (sizeIndex != -1) {
                                size = cursor.getLong(sizeIndex);
                            }
                            
                            int durationIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                            if (durationIndex != -1) {
                                duration = cursor.getLong(durationIndex);
                            }
                        } catch (Exception e) {
                            Log.e("VideosFragment", "获取媒体信息失败: " + e.getMessage());
                        }
                        cursor.close();
                    }
                } catch (Exception e) {
                    Log.e("VideosFragment", "查询内容提供者失败: " + e.getMessage());
                }
                
                // 如果没有获取到文件名，尝试从URI中提取
                if (displayName.isEmpty()) {
                    String path = uri.getPath();
                    if (path != null) {
                        int lastSlash = path.lastIndexOf('/');
                        if (lastSlash != -1) {
                            displayName = path.substring(lastSlash + 1);
                            // 移除URL编码
                            displayName = Uri.decode(displayName);
                        } else {
                            displayName = path;
                        }
                    } else {
                        displayName = "未命名视频_" + System.currentTimeMillis();
                    }
                }
                
                // 如果从内容提供者无法获取时长，尝试使用MediaMetadataRetriever和MediaPlayer
                if (duration == 0) {
                    try {
                        android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                        retriever.setDataSource(requireContext(), uri);
                        String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                        if (durationStr != null) {
                            duration = Long.parseLong(durationStr);
                            Log.d("VideosFragment", "通过MediaMetadataRetriever获取时长: " + duration);
                        }
                        retriever.release();
                        
                        // 如果还是获取不到，尝试MediaPlayer
                        if (duration == 0) {
                            android.media.MediaPlayer mp = new android.media.MediaPlayer();
                            mp.setDataSource(requireContext(), uri);
                            mp.prepare();
                            duration = mp.getDuration();
                            Log.d("VideosFragment", "通过MediaPlayer获取时长: " + duration);
                            mp.release();
                        }
                    } catch (Exception e) {
                        Log.e("VideosFragment", "获取视频时长失败: " + e.getMessage());
                    }
                }
                
                // 检查数据库中是否已存在相同URI的视频
                Video existingVideo = videoDao.getVideoByPath(uriString);
                
                if (existingVideo == null) {
                    // 创建新的视频对象
                    Video video = new Video();
                    video.setTitle(displayName);
                    // 直接保存URI字符串
                    video.setPath(uriString);
                    video.setSize(size);
                    video.setDuration(duration);
                    video.setDateAdded(System.currentTimeMillis() / 1000);
                    // 设置来源类型为content
                    video.setSourceType("content");
                    
                    // 保存到数据库
                    final String finalDisplayName = displayName;
                    long videoId = videoDao.insert(video);
                    
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "视频已导入: " + finalDisplayName, Toast.LENGTH_SHORT).show();
                        loadVideos();
                    });
                } else {
                    final String finalDisplayName = displayName;
                    requireActivity().runOnUiThread(() -> {
                        Toast.makeText(requireContext(), "该视频已存在: " + finalDisplayName, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "导入视频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    // 显示确认对话框，询问是否清除所有内容URI视频
    private void showClearContentUrisDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("清理内容URI视频")
                .setMessage("是否清除所有以content://开头的视频？这些视频可能因权限问题无法播放。")
                .setPositiveButton("确定", (dialog, which) -> clearAllContentUriVideos())
                .setNegativeButton("取消", null)
                .show();
    }
    
    // 清除所有内容URI视频
    private void clearAllContentUriVideos() {
        executorService.execute(() -> {
            try {
                // 查询所有content://开头的视频
                List<Video> contentUriVideos = videoDao.getVideosByPathPattern("content://%");
                int count = contentUriVideos.size();
                
                for (Video video : contentUriVideos) {
                    videoDao.delete(video);
                }
                
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), 
                            "已清除" + count + "个内容URI视频", 
                            Toast.LENGTH_SHORT).show();
                    loadVideos(); // 重新加载视频列表
                });
            } catch (Exception e) {
                Log.e("VideosFragment", "清除内容URI视频失败: " + e.getMessage());
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), 
                            "清除内容URI视频失败: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
} 