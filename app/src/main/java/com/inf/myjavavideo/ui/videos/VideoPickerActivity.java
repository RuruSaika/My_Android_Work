package com.inf.myjavavideo.ui.videos;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.VideoDao;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.databinding.ActivityVideoPickerBinding;
import com.inf.myjavavideo.ui.adapters.VideoCardAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoPickerActivity extends AppCompatActivity implements VideoCardAdapter.OnVideoClickListener {

    private ActivityVideoPickerBinding binding;
    private ExecutorService executorService;
    private VideoDao videoDao;
    private List<Video> videoList = new ArrayList<>();
    private VideoCardAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPickerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // 设置标题栏
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.select_video);
        }
        
        // 初始化
        executorService = Executors.newSingleThreadExecutor();
        videoDao = AppDatabase.getInstance(this).videoDao();
        
        // 设置RecyclerView
        binding.recyclerDeviceVideos.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerDeviceVideos.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        
        adapter = new VideoCardAdapter(this, videoList, this);
        binding.recyclerDeviceVideos.setAdapter(adapter);
        
        // 加载设备上的视频
        loadDeviceVideos();
    }
    
    private void loadDeviceVideos() {
        binding.progressBar.setVisibility(View.VISIBLE);
        
        executorService.execute(() -> {
            List<Video> deviceVideos = new ArrayList<>();
            
            // 从媒体库加载视频
            String[] projection = {
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.TITLE,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.SIZE
            };
            
            Uri uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
            
            try (Cursor cursor = getContentResolver().query(uri, projection, null, null, sortOrder)) {
                if (cursor != null) {
                    int pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                    int titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE);
                    int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                    int dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED);
                    int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                    
                    while (cursor.moveToNext()) {
                        String path = cursor.getString(pathColumn);
                        String title = cursor.getString(titleColumn);
                        long duration = cursor.getLong(durationColumn);
                        long dateAdded = cursor.getLong(dateAddedColumn);
                        long size = cursor.getLong(sizeColumn);
                        
                        Video video = new Video();
                        video.setTitle(title);
                        video.setPath(path);
                        video.setDuration(duration);
                        video.setDateAdded(dateAdded);
                        video.setSize(size);
                        
                        deviceVideos.add(video);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // 更新UI
            runOnUiThread(() -> {
                binding.progressBar.setVisibility(View.GONE);
                
                if (deviceVideos.isEmpty()) {
                    binding.textNoVideos.setVisibility(View.VISIBLE);
                } else {
                    binding.textNoVideos.setVisibility(View.GONE);
                    videoList.clear();
                    videoList.addAll(deviceVideos);
                    adapter.notifyDataSetChanged();
                }
            });
        });
    }
    
    @Override
    public void onVideoClick(Video video) {
        // 将选择的视频添加到库中
        executorService.execute(() -> {
            // 检查视频是否已经存在
            Video existingVideo = videoDao.getVideoByPath(video.getPath());
            
            if (existingVideo == null) {
                try {
                    // 将文件路径转换为content URI
                    String filePath = video.getPath();
                    Uri contentUri = getContentUriFromPath(filePath);
                    
                    if (contentUri != null) {
                        // 使用content URI替换文件路径
                        video.setPath(contentUri.toString());
                        video.setSourceType("content");
                        Log.d("VideoPickerActivity", "使用content URI代替文件路径: " + contentUri);
                        
                        // 尝试获取持久权限
                        try {
                            int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            getContentResolver().takePersistableUriPermission(contentUri, flags);
                            Log.d("VideoPickerActivity", "已获取持久性权限: " + contentUri);
                        } catch (SecurityException e) {
                            Log.e("VideoPickerActivity", "获取持久性权限失败: " + e.getMessage());
                        }
                    } else {
                        // 无法获取content URI，使用文件路径
                        video.setSourceType("file");
                    }
                    
                    // 保存到数据库
                    long videoId = videoDao.insert(video);
                    
                    runOnUiThread(() -> {
                        Toast.makeText(this, "视频已添加到库中", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                } catch (Exception e) {
                    Log.e("VideoPickerActivity", "添加视频失败: " + e.getMessage());
                    runOnUiThread(() -> {
                        Toast.makeText(this, "添加视频失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
                }
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "该视频已存在于库中", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
        });
    }
    
    // 尝试将文件路径转换为content URI
    private Uri getContentUriFromPath(String filePath) {
        Uri contentUri = null;
        
        try {
            // 查询MediaStore以获取content URI
            String[] projection = { MediaStore.Video.Media._ID };
            String selection = MediaStore.Video.Media.DATA + "=?";
            String[] selectionArgs = { filePath };
            
            Cursor cursor = getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                long id = cursor.getLong(idColumn);
                contentUri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                cursor.close();
                
                Log.d("VideoPickerActivity", "文件路径转换为content URI成功: " + contentUri);
            } else if (cursor != null) {
                cursor.close();
                Log.d("VideoPickerActivity", "未找到文件路径对应的content URI: " + filePath);
            }
        } catch (Exception e) {
            Log.e("VideoPickerActivity", "转换文件路径到content URI失败: " + e.getMessage());
        }
        
        return contentUri;
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