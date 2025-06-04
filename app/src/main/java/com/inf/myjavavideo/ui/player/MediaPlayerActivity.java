package com.inf.myjavavideo.ui.player;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.PlaylistDao;
import com.inf.myjavavideo.data.dao.PlaylistVideoDao;
import com.inf.myjavavideo.data.dao.SubtitleDao;
import com.inf.myjavavideo.data.dao.VideoDao;
import com.inf.myjavavideo.data.model.Playlist;
import com.inf.myjavavideo.data.model.Subtitle;
import com.inf.myjavavideo.data.model.Video;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MediaPlayerActivity extends AppCompatActivity
        implements PlaylistVideosDialogFragment.PlaylistVideoListener,
                SubtitleEditorDialogFragment.SubtitleEditorListener,
                SubtitleListDialogFragment.SubtitleListListener,
                SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener, 
                MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnErrorListener {

    private static final String TAG = "MediaPlayerActivity";
    
    // 视图
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private TextView currentPositionText;
    private TextView durationText;
    private SeekBar seekBar;
    private ImageButton playButton;
    private ImageButton pauseButton;
    private ProgressBar progressBar;
    private LinearLayout mediaController;
    private FrameLayout playerSettingsLayout;
    private TextView subtitleDisplayView;
    
    // MediaPlayer 
    private MediaPlayer mediaPlayer;
    private boolean isPrepared = false;
    private boolean isPlaying = false;
    private boolean isDragging = false;
    
    // 数据
    private ExecutorService executorService;
    private VideoDao videoDao;
    private SubtitleDao subtitleDao;
    private Video currentVideo;
    private boolean isFullscreen = false;
    private boolean isSettingsVisible = false;
    private int currentPlaylistId = -1;
    private List<Video> currentPlaylistVideos;
    
    // 字幕
    private Handler subtitleHandler;
    private Runnable subtitleRunnable;
    private boolean subtitlesEnabled = true;
    
    // 控制器自动隐藏
    private static final int CONTROLLER_TIMEOUT = 3000; // 3秒后自动隐藏
    private Handler controllerHandler = new Handler(Looper.getMainLooper());
    private Runnable hideControllerRunnable = this::hideController;

    // 在onStart中调用的方法
    private void startPositionUpdater() {
        if (positionHandler == null) {
            positionHandler = new Handler(Looper.getMainLooper());
        }
        
        positionRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    try {
                        if (mediaPlayer.isPlaying()) {
                            int currentPosition = mediaPlayer.getCurrentPosition();
                            seekBar.setProgress(currentPosition);
                            currentPositionText.setText(formatDuration(currentPosition));
                            
                            // 如果总时长不正确，尝试再次获取
                            if (seekBar.getMax() <= 0 || "00:00".equals(durationText.getText().toString())) {
                                int duration = mediaPlayer.getDuration();
                                if (duration > 0) {
                                    seekBar.setMax(duration);
                                    durationText.setText(formatDuration(duration));
                                    Log.d(TAG, "更新视频总时长: " + duration + "ms");
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "更新进度时出错: " + e.getMessage());
                    }
                }
                
                positionHandler.postDelayed(this, 1000);
            }
        };
        
        positionHandler.post(positionRunnable);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_player);
        
        // 全屏播放
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        
        // 设置屏幕方向传感器，允许根据设备方向自动旋转
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        
        // 初始化视图
        initViews();
        
        // 初始化数据
        executorService = Executors.newSingleThreadExecutor();
        videoDao = AppDatabase.getInstance(this).videoDao();
        subtitleDao = AppDatabase.getInstance(this).subtitleDao();
        
        // 初始化字幕处理
        subtitleHandler = new Handler(Looper.getMainLooper());
        
        // 获取视频ID和播放列表ID
        int videoId = getIntent().getIntExtra("video_id", -1);
        currentPlaylistId = getIntent().getIntExtra("playlist_id", -1);
        
        if (videoId == -1) {
            Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 加载视频
        loadVideo(videoId);
        
        // 如果有播放列表ID，加载播放列表视频
        if (currentPlaylistId != -1) {
            loadPlaylistVideos(currentPlaylistId);
        }
        
        // 设置播放速度选择
        setupSpeedControl();
    }
    
    private void initViews() {
        surfaceView = findViewById(R.id.surface_view);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        
        currentPositionText = findViewById(R.id.text_current_position);
        durationText = findViewById(R.id.text_duration);
        seekBar = findViewById(R.id.seek_bar);
        playButton = findViewById(R.id.btn_play);
        pauseButton = findViewById(R.id.btn_pause);
        progressBar = findViewById(R.id.progress_bar);
        mediaController = findViewById(R.id.media_controller);
        playerSettingsLayout = findViewById(R.id.layout_player_settings);
        subtitleDisplayView = findViewById(R.id.text_subtitle_display);
        
        // 设置字幕按钮的初始状态
        ImageButton subtitleButton = findViewById(R.id.btn_subtitle);
        if (subtitleButton != null) {
            // 确保字幕按钮在暗背景上清晰可见
            subtitleButton.setAlpha(subtitlesEnabled ? 1.0f : 0.5f);
        }
        
        // 设置点击事件
        playButton.setOnClickListener(v -> startPlayback());
        pauseButton.setOnClickListener(v -> pausePlayback());
        findViewById(R.id.btn_prev).setOnClickListener(v -> playPreviousVideo());
        findViewById(R.id.btn_next).setOnClickListener(v -> playNextVideo());
        findViewById(R.id.btn_fullscreen).setOnClickListener(v -> toggleFullscreen());
        findViewById(R.id.btn_settings).setOnClickListener(v -> toggleSettings());
        findViewById(R.id.btn_close_settings).setOnClickListener(v -> toggleSettings());
        findViewById(R.id.btn_playlist).setOnClickListener(v -> showPlaylistDialog());
        findViewById(R.id.btn_subtitle).setOnClickListener(v -> showSubtitleListDialog());
        // 使用同一个按钮来切换字幕显示/隐藏
        findViewById(R.id.btn_subtitle).setOnLongClickListener(v -> {
            toggleSubtitles();
            return true;
        });
        
        // 设置进度条
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null && isPrepared) {
                    currentPositionText.setText(formatDuration(progress));
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isDragging = true;
                stopHideControllerTimer();
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mediaPlayer != null && isPrepared) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                    // 如果之前是播放状态，拖动后继续播放
                    if (isPlaying) {
                        startPlayback();
                    }
                }
                isDragging = false;
                startHideControllerTimer();
            }
        });
        
        // 设置整个播放器区域的触摸监听
        surfaceView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                toggleController();
                return true;
            }
            return false;
        });
    }
    
    private void loadVideo(int videoId) {
        progressBar.setVisibility(View.VISIBLE);
        executorService.execute(() -> {
            currentVideo = videoDao.getVideoById(videoId);
            
            if (currentVideo != null) {
                runOnUiThread(() -> initializePlayer(currentVideo));
            } else {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }
    
    private void initializePlayer(Video video) {
        // 处理前先释放资源
        releaseMediaPlayer();
        
        // 检查路径是否有效
        if (video.getPath() == null || video.getPath().isEmpty()) {
            Toast.makeText(this, "视频路径无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // 对于内容URI，首先检查我们是否真的能访问这个URI
        if (video.getPath().startsWith("content://")) {
            Uri contentUri = Uri.parse(video.getPath());
            boolean canAccess = false;
            
            try {
                // 尝试打开输入流
                InputStream inputStream = getContentResolver().openInputStream(contentUri);
                if (inputStream != null) {
                    inputStream.close();
                    canAccess = true;
                }
            } catch (Exception e) {
                Log.e(TAG, "无法访问内容URI: " + e.getMessage());
                // 如果无法访问，显示错误并返回
                Toast.makeText(this, "无法访问此视频，可能是权限已过期", Toast.LENGTH_LONG).show();
                
                // 标记此视频需要重新导入
                executorService.execute(() -> {
                    try {
                        // 从数据库中删除
                        videoDao.delete(video);
                        
                        runOnUiThread(() -> {
                            Toast.makeText(this, "已从库中移除无效视频", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    } catch (Exception ex) {
                        Log.e(TAG, "删除无效视频失败: " + ex.getMessage());
                    }
                });
                
                return;
            }
        }
        
        mediaPlayer = new MediaPlayer();
        
        // 设置监听器
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnErrorListener(this);
        
        try {
            // 设置播放源
            String path = video.getPath();
            Log.d(TAG, "视频路径: " + path);
            
            if (path.startsWith("content://")) {
                // 从内容提供者获取视频
                Uri uri = Uri.parse(path);
                
                // 尝试获取持久权限
                try {
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION 
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION;
                    
                    getContentResolver().takePersistableUriPermission(uri, 
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    Log.d(TAG, "已获取持久性权限: " + uri);
                } catch (SecurityException e) {
                    // 可能已经有权限或无法获取持久权限
                    Log.w(TAG, "无法获取持久性权限: " + e.getMessage());
                }
                
                // 尝试几种不同的方式设置数据源
                try {
                    // 方法1：直接使用ContentResolver
                    mediaPlayer.setDataSource(getApplicationContext(), uri);
                    Log.d(TAG, "成功使用ContentResolver设置数据源");
                } catch (Exception e1) {
                    Log.e(TAG, "使用ContentResolver设置数据源失败: " + e1.getMessage());
                    
                    try {
                        // 方法2：使用AssetFileDescriptor
                        AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(uri, "r");
                        if (afd != null) {
                            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                            afd.close();
                            Log.d(TAG, "成功使用AssetFileDescriptor设置数据源");
                        } else {
                            throw new IOException("无法打开AssetFileDescriptor");
                        }
                    } catch (Exception e2) {
                        Log.e(TAG, "使用AssetFileDescriptor设置数据源失败: " + e2.getMessage());
                        
                        try {
                            // 方法3：使用FileDescriptor
                            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
                            if (pfd != null) {
                                mediaPlayer.setDataSource(pfd.getFileDescriptor());
                                pfd.close();
                                Log.d(TAG, "成功使用FileDescriptor设置数据源");
                            } else {
                                throw new IOException("无法打开ParcelFileDescriptor");
                            }
                        } catch (Exception e3) {
                            Log.e(TAG, "使用FileDescriptor设置数据源失败: " + e3.getMessage());
                            throw new IOException("无法播放内容URI视频: " + e3.getMessage());
                        }
                    }
                }
            } else if (path.startsWith("/content:")) {
                // 处理错误格式的content URI
                String correctedPath = "content:" + path.substring("/content:".length());
                Uri uri = Uri.parse(correctedPath);
                
                try {
                    // 尝试获取权限
                    int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                    getContentResolver().takePersistableUriPermission(uri, flags);
                    Log.d(TAG, "已获取持久性权限: " + uri);
                } catch (SecurityException e) {
                    Log.w(TAG, "无法获取持久性权限: " + e.getMessage());
                }
                
                mediaPlayer.setDataSource(getApplicationContext(), uri);
                Log.d(TAG, "修正并使用ContentResolver打开内容URI: " + uri);
            } else {
                // 普通文件路径
                File videoFile = new File(path);
                if (videoFile.exists()) {
                    mediaPlayer.setDataSource(getApplicationContext(), Uri.fromFile(videoFile));
                    Log.d(TAG, "使用文件路径: " + videoFile.getAbsolutePath());
                } else {
                    // 尝试将路径作为Uri字符串解析
                    try {
                        Uri uri = Uri.parse(path);
                        mediaPlayer.setDataSource(getApplicationContext(), uri);
                        Log.d(TAG, "使用Uri打开不存在的文件路径: " + uri);
                    } catch (Exception e) {
                        throw new IOException("无法解析视频路径: " + path);
                    }
                }
            }
            
            // 设置SurfaceHolder
            mediaPlayer.setDisplay(surfaceHolder);
            
            // 准备播放
            mediaPlayer.prepareAsync();
            progressBar.setVisibility(View.VISIBLE);
            
        } catch (IOException e) {
            Log.e(TAG, "Error setting data source", e);
            Toast.makeText(this, "无法播放视频: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            progressBar.setVisibility(View.GONE);
            finish();
        }
    }
    
    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        progressBar.setVisibility(View.GONE);
        
        // 获取视频总时长
        int duration = mp.getDuration();
        if (duration > 0) {
            // 更新进度条最大值
            seekBar.setMax(duration);
            
            // 更新总时长显示
            durationText.setText(formatDuration(duration));
            Log.d(TAG, "视频总时长: " + duration + "ms (" + formatDuration(duration) + ")");
        } else {
            Log.w(TAG, "无法获取视频时长，返回值为: " + duration);
        }
        
        // 恢复上次播放位置
        if (currentVideo.getLastPlayedPosition() > 0) {
            mp.seekTo((int) currentVideo.getLastPlayedPosition());
        }
        
        // 开始播放
        startPlayback();
        
        // 显示控制器
        showController();
        
        // 启动字幕显示
        startSubtitleTracking();
        
        // 启动进度更新
        startPositionUpdater();
        
        // 在视频准备好后调整视频播放器大小
        // 延迟调整，确保视频信息已获取并且布局已完成
        new Handler().postDelayed(this::adjustVideoSize, 300);
    }
    
    private void startPlayback() {
        if (mediaPlayer != null && isPrepared) {
            mediaPlayer.start();
            isPlaying = true;
            updatePlayPauseButton();
            startSubtitleTracking();
        }
    }
    
    private void pausePlayback() {
        if (mediaPlayer != null && isPrepared && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            updatePlayPauseButton();
        }
    }
    
    private void updatePlayPauseButton() {
        if (isPlaying) {
            playButton.setVisibility(View.GONE);
            pauseButton.setVisibility(View.VISIBLE);
        } else {
            playButton.setVisibility(View.VISIBLE);
            pauseButton.setVisibility(View.GONE);
        }
    }
    
    private void toggleController() {
        if (mediaController.getVisibility() == View.VISIBLE) {
            hideController();
        } else {
            showController();
        }
    }
    
    private void showController() {
        mediaController.setVisibility(View.VISIBLE);
        mediaController.setAlpha(0f);
        mediaController.animate()
                .alpha(1f)
                .setDuration(300)
                .start();
        
        startHideControllerTimer();
    }
    
    private void hideController() {
        mediaController.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction(() -> mediaController.setVisibility(View.GONE))
                .start();
    }
    
    private void startHideControllerTimer() {
        stopHideControllerTimer(); // 先停止之前的计时器
        if (isPlaying) {
            controllerHandler.postDelayed(hideControllerRunnable, CONTROLLER_TIMEOUT);
        }
    }
    
    private void stopHideControllerTimer() {
        controllerHandler.removeCallbacks(hideControllerRunnable);
    }
    
    private Handler positionHandler = new Handler(Looper.getMainLooper());
    private Runnable positionRunnable;
    
    @Override
    public void onCompletion(MediaPlayer mp) {
        // 播放完成，更新最后播放位置为0
        updatePlaybackPosition(0);
        
        // 重置播放状态
        isPlaying = false;
        updatePlayPauseButton();
        
        // 检查是否有下一个视频可播放
        if (currentPlaylistId != -1 && currentPlaylistVideos != null && !currentPlaylistVideos.isEmpty()) {
            int currentIndex = -1;
            
            // 查找当前视频在播放列表中的位置
            for (int i = 0; i < currentPlaylistVideos.size(); i++) {
                if (currentPlaylistVideos.get(i).getId() == currentVideo.getId()) {
                    currentIndex = i;
                    break;
                }
            }
            
            // 如果找到当前视频并且不是最后一个，自动播放下一个
            if (currentIndex != -1 && currentIndex < currentPlaylistVideos.size() - 1) {
                Video nextVideo = currentPlaylistVideos.get(currentIndex + 1);
                playVideo(nextVideo);
                return;
            } else if (currentIndex != -1) {
                // 是播放列表的最后一个视频
                Toast.makeText(this, "播放列表播放完成", Toast.LENGTH_SHORT).show();
                // 重置到视频开始位置，但不自动播放
                if (mediaPlayer != null && isPrepared) {
                    mediaPlayer.seekTo(0);
                    currentPositionText.setText(formatDuration(0));
                    seekBar.setProgress(0);
                }
                return;
            }
        }
        
        // 尝试播放下一个视频
        playNextVideo();
    }
    
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        // 更新缓冲进度，如果需要显示
    }
    
    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.e(TAG, "播放器错误: " + what + ", " + extra);
        progressBar.setVisibility(View.GONE);
        
        String errorMessage;
        if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            errorMessage = "媒体服务器故障";
        } else if (what == MediaPlayer.MEDIA_ERROR_IO) {
            errorMessage = "输入/输出错误，可能是权限问题或文件不存在";
        } else if (what == MediaPlayer.MEDIA_ERROR_MALFORMED) {
            errorMessage = "媒体格式错误";
        } else if (what == MediaPlayer.MEDIA_ERROR_UNSUPPORTED) {
            errorMessage = "不支持的媒体格式";
        } else if (what == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
            errorMessage = "操作超时";
        } else if (extra == -2) { // MediaPlayer.MEDIA_ERROR_SYSTEM 的值是 -2
            errorMessage = "系统错误，错误码: " + extra;
        } else {
            errorMessage = "未知播放器错误";
        }
        
        Toast.makeText(this, "播放错误: " + errorMessage, Toast.LENGTH_LONG).show();
        
        // 如果是内容URI，尝试重新获取权限
        if (currentVideo != null && currentVideo.getPath() != null && 
                currentVideo.getPath().startsWith("content://")) {
            Log.d(TAG, "尝试重新获取内容URI权限");
            
            try {
                Uri uri = Uri.parse(currentVideo.getPath());
                // 仅申请临时权限
                getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                
                // 尝试重新加载
                initializePlayer(currentVideo);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "重新获取权限失败: " + e.getMessage());
                Toast.makeText(this, "无法访问该视频，可能是权限问题", Toast.LENGTH_LONG).show();
            }
        }
        
        return true; // 表示我们已处理错误
    }
    
    private void updatePlaybackPosition(long position) {
        if (currentVideo != null) {
            executorService.execute(() -> {
                videoDao.updateLastPlayedPosition(currentVideo.getId(), position);
            });
        }
    }
    
    // SurfaceHolder.Callback 方法实现
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Surface创建后，如果已有MediaPlayer和视频，则设置Surface
        if (mediaPlayer != null && currentVideo != null) {
            mediaPlayer.setDisplay(holder);
            if (!isPrepared) {
                try {
                    mediaPlayer.prepareAsync();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Error preparing MediaPlayer", e);
                }
            }
        }
    }
    
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        // 可以在这里处理Surface尺寸变化
        Log.d(TAG, "Surface changed: " + width + "x" + height);
    }
    
    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        // Surface销毁时，需要保存播放位置并释放MediaPlayer
        if (mediaPlayer != null && isPrepared) {
            int position = mediaPlayer.getCurrentPosition();
            updatePlaybackPosition(position);
            pausePlayback();
        }
    }
    
    // 播放控制方法
    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        
        if (isFullscreen) {
            // 横屏全屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            // 隐藏状态栏和导航栏
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
            
            // 切换到全屏后，延迟调整视频尺寸，确保布局已完成测量
            new Handler().postDelayed(this::adjustVideoSize, 300);
        } else {
            // 竖屏模式
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            // 恢复状态栏和导航栏
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            
            // 延迟调整视频尺寸
            new Handler().postDelayed(this::adjustVideoSize, 300);
        }
        
        // 更新界面元素，如全屏按钮图标等
        ImageButton fullscreenButton = findViewById(R.id.btn_fullscreen);
        if (fullscreenButton != null) {
            fullscreenButton.setImageResource(isFullscreen ? 
                    R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        }
    }
    
    // 根据视频尺寸调整SurfaceView大小
    private void adjustVideoSize() {
        if (mediaPlayer == null || surfaceView == null) return;
        
        try {
            // 获取视频宽高
            int videoWidth = mediaPlayer.getVideoWidth();
            int videoHeight = mediaPlayer.getVideoHeight();
            
            if (videoWidth <= 0 || videoHeight <= 0) return;
            
            Log.d(TAG, "原始视频尺寸: " + videoWidth + "x" + videoHeight);
            
            // 获取SurfaceView的父容器尺寸
            View parentView = (View) surfaceView.getParent();
            int containerWidth = parentView.getWidth();
            int containerHeight = parentView.getHeight();
            
            Log.d(TAG, "容器尺寸: " + containerWidth + "x" + containerHeight);
            
            // 计算视频的宽高比
            float videoAspectRatio = (float) videoWidth / videoHeight;
            // 计算容器的宽高比
            float containerAspectRatio = (float) containerWidth / containerHeight;
            
            int newWidth, newHeight;
            
            if (videoAspectRatio > containerAspectRatio) {
                // 视频比容器更宽，以宽度为基准
                newWidth = containerWidth;
                newHeight = (int) (containerWidth / videoAspectRatio);
                Log.d(TAG, "视频更宽，以宽度为基准");
            } else {
                // 视频比容器更高，以高度为基准
                newHeight = containerHeight;
                newWidth = (int) (containerHeight * videoAspectRatio);
                Log.d(TAG, "视频更高，以高度为基准");
            }
            
            // 设置SurfaceView布局参数
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(newWidth, newHeight);
            layoutParams.gravity = Gravity.CENTER;
            
            // 应用布局参数
            surfaceView.setLayoutParams(layoutParams);
            
            Log.d(TAG, "调整后的视频尺寸: " + newWidth + "x" + newHeight);
        } catch (Exception e) {
            Log.e(TAG, "调整视频尺寸失败: " + e.getMessage());
        }
    }
    
    private void toggleSettings() {
        isSettingsVisible = !isSettingsVisible;
        
        if (isSettingsVisible) {
            // 显示设置面板
            playerSettingsLayout.setVisibility(View.VISIBLE);
            playerSettingsLayout.setAlpha(0f);
            playerSettingsLayout.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            
            // 隐藏播放器控制器
            hideController();
        } else {
            // 隐藏设置面板
            playerSettingsLayout.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        playerSettingsLayout.setVisibility(View.GONE);
                        // 显示播放器控制器
                        showController();
                    })
                    .start();
        }
    }
    
    private void setupSpeedControl() {
        RadioGroup speedGroup = findViewById(R.id.radio_group_speed);
        speedGroup.setOnCheckedChangeListener((group, checkedId) -> {
            float speed = 1.0f;
            if (checkedId == R.id.radio_speed_0_5) {
                speed = 0.5f;
            } else if (checkedId == R.id.radio_speed_0_75) {
                speed = 0.75f;
            } else if (checkedId == R.id.radio_speed_normal) {
                speed = 1.0f;
            } else if (checkedId == R.id.radio_speed_1_25) {
                speed = 1.25f;
            } else if (checkedId == R.id.radio_speed_1_5) {
                speed = 1.5f;
            } else if (checkedId == R.id.radio_speed_2) {
                speed = 2.0f;
            }
            
            if (mediaPlayer != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // MediaPlayer API 23+ (Android 6.0+) 支持播放速度控制
                mediaPlayer.setPlaybackParams(mediaPlayer.getPlaybackParams().setSpeed(speed));
                
                // 提示当前速度
                Toast.makeText(this, String.format("播放速度: %.1fx", speed), Toast.LENGTH_SHORT).show();
                
                // 关闭设置面板
                isSettingsVisible = true; // 确保关闭操作生效
                toggleSettings();
            } else {
                Toast.makeText(this, "您的设备不支持调整播放速度", Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void playNextVideo() {
        // 实现播放下一个视频的逻辑
        if (currentVideo == null) return;
        
        // 如果在播放列表中，优先播放列表中的下一个视频
        if (currentPlaylistId != -1 && currentPlaylistVideos != null && !currentPlaylistVideos.isEmpty()) {
            int currentIndex = -1;
            
            // 查找当前视频在播放列表中的位置
            for (int i = 0; i < currentPlaylistVideos.size(); i++) {
                if (currentPlaylistVideos.get(i).getId() == currentVideo.getId()) {
                    currentIndex = i;
                    break;
                }
            }
            
            // 如果找到当前视频并且不是最后一个
            if (currentIndex != -1 && currentIndex < currentPlaylistVideos.size() - 1) {
                Video nextVideo = currentPlaylistVideos.get(currentIndex + 1);
                playVideo(nextVideo);
                return;
            }
        }
        
        // 如果不在播放列表中或者已经是播放列表中的最后一个视频，按照原来的方式查找下一个视频
        executorService.execute(() -> {
            // 获取当前视频在列表中的位置
            Video nextVideo = videoDao.getNextVideoAfter(currentVideo.getId());
            
            if (nextVideo != null) {
                runOnUiThread(() -> playVideo(nextVideo));
            } else {
                runOnUiThread(() -> Toast.makeText(this, "没有更多视频", Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    private void playPreviousVideo() {
        // 实现播放上一个视频的逻辑
        if (currentVideo == null) return;
        
        // 如果在播放列表中，优先播放列表中的上一个视频
        if (currentPlaylistId != -1 && currentPlaylistVideos != null && !currentPlaylistVideos.isEmpty()) {
            int currentIndex = -1;
            
            // 查找当前视频在播放列表中的位置
            for (int i = 0; i < currentPlaylistVideos.size(); i++) {
                if (currentPlaylistVideos.get(i).getId() == currentVideo.getId()) {
                    currentIndex = i;
                    break;
                }
            }
            
            // 如果找到当前视频并且不是第一个
            if (currentIndex > 0) {
                Video prevVideo = currentPlaylistVideos.get(currentIndex - 1);
                playVideo(prevVideo);
                return;
            }
        }
        
        // 如果不在播放列表中或者已经是播放列表中的第一个视频，按照原来的方式查找上一个视频
        executorService.execute(() -> {
            // 获取当前视频在列表中的位置
            Video prevVideo = videoDao.getPreviousVideoBefore(currentVideo.getId());
            
            if (prevVideo != null) {
                runOnUiThread(() -> playVideo(prevVideo));
            } else {
                runOnUiThread(() -> Toast.makeText(this, "这是第一个视频", Toast.LENGTH_SHORT).show());
            }
        });
    }
    
    private void playVideo(Video video) {
        // 更新当前播放位置为0（结束播放）
        if (currentVideo != null) {
            updatePlaybackPosition(0);
        }
        
        // 切换到新视频
        currentVideo = video;
        
        // 初始化新的播放器
        initializePlayer(video);
        
        // 提示信息
        Toast.makeText(this, "正在播放: " + video.getTitle(), Toast.LENGTH_SHORT).show();
    }
    
    private void loadPlaylistVideos(int playlistId) {
        executorService.execute(() -> {
            PlaylistVideoDao playlistVideoDao = AppDatabase.getInstance(this).playlistVideoDao();
            currentPlaylistVideos = playlistVideoDao.getVideosForPlaylist(playlistId);
            
            if (currentPlaylistId != -1 && currentPlaylistVideos != null && !currentPlaylistVideos.isEmpty()) {
                // 获取播放列表名称以显示在UI上
                PlaylistDao playlistDao = AppDatabase.getInstance(this).playlistDao();
                Playlist playlist = playlistDao.getPlaylistById(playlistId);
                
                if (playlist != null) {
                    final String playlistName = playlist.getName();
                    runOnUiThread(() -> {
                        Toast.makeText(this, 
                                String.format(getString(R.string.current_playlist), playlistName), 
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }
        });
    }
    
    private void showPlaylistDialog() {
        if (currentVideo == null) return;
        
        if (currentPlaylistId != -1) {
            // 如果已经在播放列表中，显示播放列表的视频列表
            PlaylistVideosDialogFragment dialogFragment = PlaylistVideosDialogFragment.newInstance(
                    currentPlaylistId, currentVideo.getId());
            dialogFragment.show(getSupportFragmentManager(), "playlist_videos_dialog");
        } else {
            // 否则显示添加到播放列表对话框
            PlaylistDialogFragment dialogFragment = PlaylistDialogFragment.newInstance(currentVideo.getId());
            dialogFragment.show(getSupportFragmentManager(), "playlist_dialog");
        }
    }
    
    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }
    
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            if (isPrepared) {
                updatePlaybackPosition(mediaPlayer.getCurrentPosition());
            }
            mediaPlayer.release();
            mediaPlayer = null;
            isPrepared = false;
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        if (mediaPlayer != null && isPrepared) {
            // 保存当前播放位置
            updatePlaybackPosition(mediaPlayer.getCurrentPosition());
            
            // 暂停播放
            pausePlayback();
        }
        
        // 停止字幕跟踪
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
        
        // 停止进度更新
        stopPositionUpdater();
        
        // 停止控制器隐藏计时器
        stopHideControllerTimer();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (mediaPlayer != null && isPrepared && !isPlaying) {
            // 可以选择自动恢复播放
            // startPlayback();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseMediaPlayer();
        
        if (executorService != null) {
            executorService.shutdown();
        }
        
        // 停止所有Handler任务
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
        stopPositionUpdater();
        stopHideControllerTimer();
    }
    
    // PlaylistVideosDialogFragment.PlaylistVideoListener 实现
    @Override
    public void onVideoSelected(Video video, int playlistId) {
        if (video != null) {
            currentPlaylistId = playlistId;
            playVideo(video);
        }
    }
    
    // 字幕相关功能
    private void startSubtitleTracking() {
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
        
        subtitleRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && isPrepared && subtitlesEnabled) {
                    long currentPosition = mediaPlayer.getCurrentPosition();
                    checkAndDisplaySubtitles(currentPosition);
                }
                subtitleHandler.postDelayed(this, 100); // 每100毫秒检查一次
            }
        };
        
        subtitleHandler.post(subtitleRunnable);
    }
    
    private void stopSubtitleTracking() {
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
    }
    
    private void checkAndDisplaySubtitles(long currentPosition) {
        executorService.execute(() -> {
            if (currentVideo != null) {
                // 查找当前时间点的字幕
                Subtitle subtitle = subtitleDao.getSubtitleForTime(
                        currentVideo.getId(), currentPosition);
                
                runOnUiThread(() -> {
                    if (subtitle != null && subtitlesEnabled) {
                        // 显示字幕
                        subtitleDisplayView.setText(subtitle.getText());
                        subtitleDisplayView.setVisibility(View.VISIBLE);
                    } else {
                        // 隐藏字幕
                        subtitleDisplayView.setText("");
                        subtitleDisplayView.setVisibility(View.GONE);
                    }
                });
            }
        });
    }
    
    private void toggleSubtitles() {
        subtitlesEnabled = !subtitlesEnabled;
        
        // 获取字幕按钮并更新其状态
        ImageButton subtitleButton = findViewById(R.id.btn_subtitle);
        
        if (subtitlesEnabled) {
            Toast.makeText(this, "字幕已启用", Toast.LENGTH_SHORT).show();
            startSubtitleTracking();
            // 启用字幕时，按钮显示为全不透明
            if (subtitleButton != null) {
                subtitleButton.setAlpha(1.0f);
            }
        } else {
            Toast.makeText(this, "字幕已禁用", Toast.LENGTH_SHORT).show();
            subtitleDisplayView.setVisibility(View.GONE);
            // 禁用字幕时，按钮显示为半透明
            if (subtitleButton != null) {
                subtitleButton.setAlpha(0.5f);
            }
        }
    }
    
    private void showSubtitleListDialog() {
        if (currentVideo == null) return;
        
        SubtitleListDialogFragment dialogFragment = SubtitleListDialogFragment.newInstance(currentVideo.getId());
        dialogFragment.show(getSupportFragmentManager(), "subtitle_list_dialog");
    }
    
    // SubtitleEditorDialogFragment.SubtitleEditorListener 方法实现
    @Override
    public void onSubtitleSaved(Subtitle subtitle) {
        if (subtitle != null) {
            executorService.execute(() -> {
                // 添加或更新字幕
                if (subtitle.getId() == 0) {
                    // 新增字幕
                    long subtitleId = subtitleDao.insert(subtitle);
                    subtitle.setId((int) subtitleId);
                } else {
                    // 更新字幕
                    subtitleDao.update(subtitle);
                }
                
                runOnUiThread(() -> {
                    Toast.makeText(this, "字幕已保存", Toast.LENGTH_SHORT).show();
                    // 确保字幕显示已启用
                    if (!subtitlesEnabled) {
                        toggleSubtitles();
                    }
                });
            });
        }
    }
    
    @Override
    public void onSubtitleDeleted(Subtitle subtitle) {
        if (subtitle != null && subtitle.getId() != 0) {
            executorService.execute(() -> {
                subtitleDao.delete(subtitle);
                
                runOnUiThread(() -> 
                    Toast.makeText(this, "字幕已删除", Toast.LENGTH_SHORT).show());
            });
        }
    }
    
    // SubtitleListDialogFragment.SubtitleListListener 方法实现
    @Override
    public void onAddSubtitle() {
        if (currentVideo == null) return;
        
        Subtitle newSubtitle = new Subtitle();
        newSubtitle.setVideoId(currentVideo.getId());
        
        // 设置默认时间范围为当前播放位置前后5秒
        long currentPosition = mediaPlayer.getCurrentPosition();
        long startTime = Math.max(0, currentPosition - 2000); // 当前时间往前2秒
        long endTime = currentPosition + 3000; // 当前时间往后3秒
        
        newSubtitle.setStartTime(startTime);
        newSubtitle.setEndTime(endTime);
        newSubtitle.setText("");
        
        // 显示编辑对话框
        SubtitleEditorDialogFragment dialogFragment = 
                SubtitleEditorDialogFragment.newInstance(newSubtitle);
        dialogFragment.show(getSupportFragmentManager(), "subtitle_editor_dialog");
    }
    
    @Override
    public void onEditSubtitle(Subtitle subtitle) {
        if (subtitle != null) {
            SubtitleEditorDialogFragment dialogFragment = 
                    SubtitleEditorDialogFragment.newInstance(subtitle);
            dialogFragment.show(getSupportFragmentManager(), "subtitle_editor_dialog");
        }
    }
    
    @Override
    public void onJumpToSubtitle(Subtitle subtitle) {
        if (mediaPlayer != null && isPrepared && subtitle != null) {
            // 跳转到字幕开始时间
            mediaPlayer.seekTo((int) subtitle.getStartTime());
            
            // 确保字幕显示已启用
            if (!subtitlesEnabled) {
                toggleSubtitles();
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // 处理屏幕旋转
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏模式
            isFullscreen = true;
            // 隐藏状态栏和导航栏
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            );
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            // 竖屏模式
            isFullscreen = false;
            // 恢复状态栏和导航栏
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
        
        // 更新全屏按钮图标
        ImageButton fullscreenButton = findViewById(R.id.btn_fullscreen);
        if (fullscreenButton != null) {
            fullscreenButton.setImageResource(isFullscreen ? 
                    R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
        }
        
        // 延迟调整视频尺寸，确保布局已经完成
        new Handler().postDelayed(this::adjustVideoSize, 300);
    }

    private void stopPositionUpdater() {
        if (positionHandler != null) {
            positionHandler.removeCallbacks(positionRunnable);
        }
    }
} 