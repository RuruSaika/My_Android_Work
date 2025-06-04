package com.inf.myjavavideo.ui.player;

import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.PlaylistVideoDao;
import com.inf.myjavavideo.data.dao.SubtitleDao;
import com.inf.myjavavideo.data.dao.VideoDao;
import com.inf.myjavavideo.data.dao.PlaylistDao;
import com.inf.myjavavideo.data.model.Subtitle;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.data.model.Playlist;
import com.inf.myjavavideo.databinding.ActivityVideoPlayerBinding;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

@UnstableApi
public class VideoPlayerActivity extends AppCompatActivity 
        implements PlaylistVideosDialogFragment.PlaylistVideoListener,
                   SubtitleEditorDialogFragment.SubtitleEditorListener,
                   SubtitleListDialogFragment.SubtitleListListener {

    private ActivityVideoPlayerBinding binding;
    private ExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private ExecutorService executorService;
    private VideoDao videoDao;
    private SubtitleDao subtitleDao;
    private Video currentVideo;
    private boolean isFullscreen = false;
    private boolean isSettingsVisible = false;
    private int currentPlaylistId = -1;
    private List<Video> currentPlaylistVideos;
    
    private TextView subtitleDisplayView;
    private Handler subtitleHandler;
    private Runnable subtitleRunnable;
    private boolean subtitlesEnabled = true;
    
    // 添加进度条引用
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityVideoPlayerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 全屏播放
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // 初始化
        executorService = Executors.newSingleThreadExecutor();
        videoDao = AppDatabase.getInstance(this).videoDao();
        subtitleDao = AppDatabase.getInstance(this).subtitleDao();
        
        // 初始化进度条
        progressBar = findViewById(R.id.progress_bar);
        
        // 添加字幕显示视图
        addSubtitleDisplayView();
        
        // 初始化字幕处理
        subtitleHandler = new Handler(Looper.getMainLooper());

        // 设置触摸监听器，用于显示/隐藏控制器
        binding.playerView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                // 点击事件发生时，如果控制器隐藏则显示，如果显示则不做处理
                // 注意：控制器在显示时，点击其他区域会因为 setControllerHideOnTouch 自动隐藏
                if (!binding.playerView.isControllerFullyVisible()) {
                    binding.playerView.showController();
                }
            }
            return false; // 返回false继续传递事件
        });
        
        // 设置设置面板关闭按钮
        ImageButton closeSettingsButton = findViewById(R.id.btn_close_settings);
        if (closeSettingsButton != null) {
            closeSettingsButton.setOnClickListener(v -> toggleSettings());
        }

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

        // 设置自定义控制器按钮
        setupCustomControls();
        
        // 设置播放速度选择
        setupSpeedControl();
        
        // 设置字幕选择
        setupSubtitlesControl();
    }
    
    private void addSubtitleDisplayView() {
        // 加载字幕显示布局
        LayoutInflater inflater = LayoutInflater.from(this);
        View subtitleLayout = inflater.inflate(R.layout.layout_subtitle_display, null);
        subtitleDisplayView = subtitleLayout.findViewById(R.id.text_subtitle_display);
        
        // 添加到播放器视图
        FrameLayout overlayFrame = binding.playerView.getOverlayFrameLayout();
        if (overlayFrame != null) {
            overlayFrame.addView(subtitleLayout);
        }
    }

    private void loadVideo(int videoId) {
        executorService.execute(() -> {
            currentVideo = videoDao.getVideoById(videoId);
            
            if (currentVideo != null) {
                runOnUiThread(() -> initializePlayer(currentVideo));
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(this, "视频不存在", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void initializePlayer(Video video) {
        // 创建轨道选择器
        trackSelector = new DefaultTrackSelector(this);
        trackSelector.setParameters(trackSelector.buildUponParameters().setMaxVideoSizeSd());
        
        // 创建播放器
        player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build();
        
        // 设置播放器视图
        binding.playerView.setPlayer(player);
        
        // 确保PlayerView使用自定义控制器并能够响应交互
        binding.playerView.setControllerOnFullScreenModeChangedListener(null);
        binding.playerView.setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
        binding.playerView.setUseController(true);
        binding.playerView.setControllerAutoShow(true);
        binding.playerView.setControllerHideOnTouch(true);
        binding.playerView.setControllerShowTimeoutMs(4000); // 增加显示时间到4秒
        
        // 创建媒体源
        Uri uri;
        String path = video.getPath();
        Log.d("VideoPlayerActivity", "视频路径: " + path);
        
        // 判断路径类型
        if (path.startsWith("content://")) {
            // content协议直接使用URI
            uri = Uri.parse(path);
            
            // 尝试获取持久权限（如果尚未获取）
            try {
                int flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
                Log.d("VideoPlayerActivity", "已获取持久性权限: " + uri);
            } catch (SecurityException e) {
                // 可能已经有权限或无法获取持久权限
                Log.w("VideoPlayerActivity", "无法获取持久性权限: " + e.getMessage());
            }
        } else if (path.startsWith("/content:")) {
            // 处理错误格式的content URI
            String correctedPath = "content:" + path.substring("/content:".length());
            uri = Uri.parse(correctedPath);
            
            // 尝试获取持久权限
            try {
                int flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(uri, flags);
                Log.d("VideoPlayerActivity", "已获取持久性权限: " + uri);
            } catch (SecurityException e) {
                Log.w("VideoPlayerActivity", "无法获取持久性权限: " + e.getMessage());
            }
        } else {
            // 普通文件路径
            File videoFile = new File(path);
            if (videoFile.exists()) {
                uri = Uri.fromFile(videoFile);
                Log.d("VideoPlayerActivity", "使用文件路径: " + videoFile.getAbsolutePath());
            } else {
                // 尝试将路径作为Uri字符串解析
                try {
                    uri = Uri.parse(path);
                    Log.d("VideoPlayerActivity", "使用Uri解析不存在的文件路径: " + uri);
                } catch (Exception e) {
                    Log.e("VideoPlayerActivity", "无法解析视频路径: " + path);
                    Toast.makeText(this, "无法打开视频: 文件不存在或路径无效", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
        
        try {
            // 创建数据源工厂
            DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
            
            // 创建媒体源
            MediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(uri));
            
            // 设置媒体源
            player.setMediaSource(mediaSource);
            
            // 准备播放器
            player.prepare();
            
            // 设置监听器，用于更新播放位置
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        // 恢复上次播放位置
                        executorService.execute(() -> {
                            Video updatedVideo = videoDao.getVideoById(video.getId());
                            if (updatedVideo != null && updatedVideo.getLastPlayedPosition() > 0) {
                                // 仅当上次位置在有效范围内时恢复
                                long duration = player.getDuration();
                                if (updatedVideo.getLastPlayedPosition() < duration - 10000) { // 距离结束10秒以上
                                    runOnUiThread(() -> {
                                        player.seekTo(updatedVideo.getLastPlayedPosition());
                                    });
                                }
                            }
                        });
                        
                        // 更新视频总时长
                        long duration = player.getDuration();
                        if (duration != C.TIME_UNSET) {
                            executorService.execute(() -> {
                                if (video.getDuration() == 0) {
                                    video.setDuration(duration);
                                    videoDao.update(video);
                                }
                            });
                        }
                        
                        // 更新布局
                        binding.playerView.hideController();
                        progressBar.setVisibility(View.GONE);
                        
                        // 设置初始的播放/暂停按钮状态
                        updatePlayPauseButton(player.isPlaying());
                        
                        // 开始字幕处理
                        startSubtitleTracking();
                    } else if (state == Player.STATE_BUFFERING) {
                        progressBar.setVisibility(View.VISIBLE);
                    } else if (state == Player.STATE_ENDED) {
                        // 播放结束
                        playNextVideo();
                    } else {
                        progressBar.setVisibility(View.GONE);
                    }
                }
                
                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    updatePlayPauseButton(isPlaying);
                }
            });
            
            // 自动播放
            player.setPlayWhenReady(true);
            
        } catch (Exception e) {
            Log.e("VideoPlayerActivity", "播放器初始化错误: " + e.getMessage());
            Toast.makeText(this, "无法播放视频: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        ImageButton playButton = binding.playerView.findViewById(R.id.exo_play);
        ImageButton pauseButton = binding.playerView.findViewById(R.id.exo_pause);
        
        if (playButton != null && pauseButton != null) {
            playButton.setVisibility(isPlaying ? View.GONE : View.VISIBLE);
            pauseButton.setVisibility(isPlaying ? View.VISIBLE : View.GONE);
        }
    }

    private void setupCustomControls() {
        // 先测试控制器是否存在
        View controller = binding.playerView.findViewById(R.id.exo_bottom_bar);
        if (controller == null) {
            // 如果控制器不存在，则等待控制器加载完成后再试
            binding.playerView.post(this::setupCustomControls);
            return;
        }
        
        // 获取自定义控制器的按钮
        ImageButton settingsButton = binding.playerView.findViewById(R.id.btn_settings);
        ImageButton fullscreenButton = binding.playerView.findViewById(R.id.btn_fullscreen);
        ImageButton playlistButton = binding.playerView.findViewById(R.id.btn_playlist);
        ImageButton prevButton = binding.playerView.findViewById(R.id.androidx_media3_ui_prev_button);
        ImageButton nextButton = binding.playerView.findViewById(R.id.androidx_media3_ui_next_button);
        ImageButton playButton = binding.playerView.findViewById(R.id.exo_play);
        ImageButton pauseButton = binding.playerView.findViewById(R.id.exo_pause);
        
        // 设置按钮点击事件
        if (settingsButton != null) settingsButton.setOnClickListener(v -> toggleSettings());
        if (fullscreenButton != null) fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        if (playlistButton != null) playlistButton.setOnClickListener(v -> showPlaylistDialog());
        
        // 设置上一个和下一个视频按钮点击事件
        if (prevButton != null) prevButton.setOnClickListener(v -> playPreviousVideo());
        if (nextButton != null) nextButton.setOnClickListener(v -> playNextVideo());
        
        // 设置播放暂停按钮点击事件
        if (playButton != null) {
            playButton.setOnClickListener(v -> {
                if (player != null) {
                    player.play();
                    updatePlayPauseButton(true);
                }
            });
        }
        
        if (pauseButton != null) {
            pauseButton.setOnClickListener(v -> {
                if (player != null) {
                    player.pause();
                    updatePlayPauseButton(false);
                }
            });
        }
        
        // 查找并配置进度条
        configureTimeBar();
        
        // 添加字幕管理按钮
        ImageButton subtitleButton = binding.playerView.findViewById(R.id.button_subtitle);
        if (subtitleButton != null) {
            subtitleButton.setOnClickListener(v -> showSubtitleListDialog());
        }
        
        // 添加字幕开关按钮
        ImageButton subtitleToggle = binding.playerView.findViewById(R.id.button_subtitle_toggle);
        if (subtitleToggle != null) {
            subtitleToggle.setOnClickListener(v -> toggleSubtitles());
        }
    }
    
    private void configureTimeBar() {
        // 直接从布局中查找时间条并配置
        final androidx.media3.ui.DefaultTimeBar timeBar = binding.playerView.findViewById(R.id.androidx_media3_ui_time_bar);
        if (timeBar == null) {
            // 如果找不到时间条，则尝试重新加载
            Log.e("VideoPlayerActivity", "无法找到时间条，可能是控制器未完全加载");
            binding.playerView.post(this::configureTimeBar);
            return;
        }
        
        // 确保时间条已启用并可见
        timeBar.setEnabled(true);
        timeBar.setVisibility(View.VISIBLE);
        
        // 请求焦点和触摸事件
        timeBar.requestFocus();
        
        // 查找进度条布局相关组件
        final FrameLayout timeBarContainer = (FrameLayout) timeBar.getParent();
        final TextView positionView = binding.playerView.findViewById(R.id.androidx_media3_ui_position_text);
        final TextView durationView = binding.playerView.findViewById(R.id.androidx_media3_ui_duration_text);
        
        if (timeBarContainer != null) {
            timeBarContainer.setClickable(true);
            timeBarContainer.setFocusable(true);
            
            // 清除所有之前的触摸监听器
            timeBarContainer.setOnTouchListener(null);
            
            // 添加触摸监听器处理点击和拖动
            timeBarContainer.setOnTouchListener(new View.OnTouchListener() {
                private boolean isScrubbing = false;
                private long scrubPosition = 0;
                
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (player == null || player.getDuration() <= 0) {
                        return false;
                    }
                    
                    int width = timeBarContainer.getWidth();
                    float x = Math.max(0, Math.min(event.getX(), width));
                    float fraction = x / width;
                    long duration = player.getDuration();
                    long position = (long) (duration * fraction);
                    
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            // 开始拖动
                            isScrubbing = true;
                            scrubPosition = position;
                            player.pause();
                            
                            // 手动触发时间条的拖动开始
                            timeBar.setPosition(position);
                            
                            // 更新时间文本
                            if (positionView != null) {
                                positionView.setText(formatDuration(position));
                            }
                            
                            Log.d("VideoPlayerActivity", "ACTION_DOWN: " + formatDuration(position));
                            break;
                            
                        case MotionEvent.ACTION_MOVE:
                            // 拖动中
                            if (isScrubbing) {
                                scrubPosition = position;
                                
                                // 手动更新时间条位置
                                timeBar.setPosition(position);
                                
                                // 更新时间文本
                                if (positionView != null) {
                                    positionView.setText(formatDuration(position));
                                }
                                
                                Log.d("VideoPlayerActivity", "ACTION_MOVE: " + formatDuration(position));
                            }
                            break;
                            
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            // 拖动结束
                            if (isScrubbing) {
                                Log.d("VideoPlayerActivity", "ACTION_UP: " + formatDuration(position));
                                
                                // 应用最终位置
                                player.seekTo(position);
                                player.play();
                                updatePlayPauseButton(true);
                                
                                isScrubbing = false;
                            }
                            break;
                    }
                    
                    // 消费这个事件
                    return true;
                }
            });
        }
        
        // 设置时间条交互监听器，作为备用(在自定义处理无效的情况下)
        timeBar.addListener(new androidx.media3.ui.TimeBar.OnScrubListener() {
            @Override
            public void onScrubStart(androidx.media3.ui.TimeBar timeBar, long position) {
                // 开始拖动时暂停播放并记录状态
                if (player != null) {
                    player.pause();
                    Log.d("VideoPlayerActivity", "TimeBar开始拖动: " + formatDuration(position));
                }
            }

            @Override
            public void onScrubMove(androidx.media3.ui.TimeBar timeBar, long position) {
                // 拖动过程中更新时间显示
                if (player != null) {
                    if (positionView != null) {
                        positionView.setText(formatDuration(position));
                    }
                    Log.d("VideoPlayerActivity", "TimeBar拖动中: " + formatDuration(position));
                }
            }

            @Override
            public void onScrubStop(androidx.media3.ui.TimeBar timeBar, long position, boolean canceled) {
                if (player == null) return;
                
                Log.d("VideoPlayerActivity", "TimeBar结束拖动: " + formatDuration(position) + ", 取消: " + canceled);
                
                // 无论是否取消，都跳转到指定位置
                if (!canceled) {
                    player.seekTo(position);
                }
                
                // 恢复播放
                player.play();
                updatePlayPauseButton(true);
            }
        });
        
        // 初始设置时间显示
        if (positionView != null && durationView != null && player != null) {
            positionView.setText(formatDuration(player.getCurrentPosition()));
            durationView.setText(formatDuration(player.getDuration()));
            
            // 定期更新时间显示和进度条位置
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable updateProgressAction = new Runnable() {
                @Override
                public void run() {
                    if (player != null && player.isPlaying()) {
                        // 更新时间文本
                        if (positionView != null && durationView != null) {
                            positionView.setText(formatDuration(player.getCurrentPosition()));
                            durationView.setText(formatDuration(player.getDuration()));
                        }
                        
                        // 更新进度条位置(仅当不在拖动状态时)
                        if (timeBar != null && player.getDuration() > 0) {
                            timeBar.setPosition(player.getCurrentPosition());
                            timeBar.setDuration(player.getDuration());
                        }
                    }
                    
                    // 继续定期更新
                    handler.postDelayed(this, 1000);
                }
            };
            
            // 开始定期更新
            handler.post(updateProgressAction);
        }
    }

    private void setupSpeedControl() {
        RadioGroup speedGroup = binding.radioGroupSpeed;
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
            } else if (checkedId == R.id.radio_speed_2_5) {
                speed = 2.5f;
            } else if (checkedId == R.id.radio_speed_3) {
                speed = 3.0f;
            }
            
            if (player != null) {
                PlaybackParameters params = new PlaybackParameters(speed);
                player.setPlaybackParameters(params);
                
                // 提示当前速度
                Toast.makeText(this, String.format("播放速度: %.1fx", speed), Toast.LENGTH_SHORT).show();
                
                // 关闭设置面板
                isSettingsVisible = true; // 确保关闭操作生效
                toggleSettings();
            }
        });
    }

    private void setupSubtitlesControl() {
        // 创建字幕适配器（此处只是示例，实际上需要根据视频文件查找可用字幕）
        String[] subtitles = new String[]{getString(R.string.no_subtitles)};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, subtitles);
        binding.spinnerSubtitles.setAdapter(adapter);
        
        // 设置字幕选择监听器
        binding.spinnerSubtitles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0 && trackSelector != null) {
                    // 关闭字幕
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(C.TRACK_TYPE_TEXT, true));
                } else if (trackSelector != null) {
                    // 使用选定的字幕轨道
                    trackSelector.setParameters(trackSelector.buildUponParameters()
                            .setRendererDisabled(C.TRACK_TYPE_TEXT, false));
                    // 在实际应用中，还需要选择特定的字幕轨道
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // 不做任何操作
            }
        });
    }

    private void toggleSettings() {
        isSettingsVisible = !isSettingsVisible;
        
        // 使用动画效果显示/隐藏设置面板
        if (isSettingsVisible) {
            // 显示设置面板
            binding.layoutPlayerSettings.setVisibility(View.VISIBLE);
            binding.layoutPlayerSettings.setAlpha(0f);
            binding.layoutPlayerSettings.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .start();
            
            // 隐藏播放器控制器以避免冲突
            binding.playerView.hideController();
        } else {
            // 隐藏设置面板
            binding.layoutPlayerSettings.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        binding.layoutPlayerSettings.setVisibility(View.GONE);
                        // 显示播放器控制器
                        binding.playerView.showController();
                    })
                    .start();
        }
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            // 横屏全屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            // 竖屏
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }

    private void showPlaylistDialog() {
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

    private void updatePlaybackPosition(long position) {
        if (currentVideo != null) {
            executorService.execute(() -> {
                videoDao.updateLastPlayedPosition(currentVideo.getId(), position);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            // 保存当前播放位置
            long position = player.getCurrentPosition();
            updatePlaybackPosition(position);
            
            // 暂停播放
            player.pause();
        }
        
        // 停止字幕跟踪
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && player.getPlaybackState() == Player.STATE_READY) {
            player.play();
            
            // 重新启动字幕跟踪
            startSubtitleTracking();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
        if (executorService != null) {
            executorService.shutdown();
        }
        
        // 停止字幕跟踪
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
    }

    private void playNextVideo() {
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
                
                // 更新当前播放位置为0（结束播放）
                updatePlaybackPosition(0);
                
                // 切换到新视频
                currentVideo = nextVideo;
                
                // 在UI线程上更新播放器
                runOnUiThread(() -> {
                    // 释放之前的播放器资源
                    if (player != null) {
                        player.stop();
                    }
                    
                    // 初始化新的播放器
                    initializePlayer(nextVideo);
                    
                    // 更新标题等UI元素
                    Toast.makeText(this, "正在播放: " + nextVideo.getTitle(), Toast.LENGTH_SHORT).show();
                });
                return;
            }
        }
        
        // 如果不在播放列表中或者已经是播放列表中的最后一个视频，按照原来的方式查找下一个视频
        executorService.execute(() -> {
            // 获取当前视频在列表中的位置
            Video nextVideo = videoDao.getNextVideoAfter(currentVideo.getId());
            
            if (nextVideo != null) {
                // 更新当前播放位置为0（结束播放）
                updatePlaybackPosition(0);
                
                // 切换到新视频
                currentVideo = nextVideo;
                
                // 在UI线程上更新播放器
                runOnUiThread(() -> {
                    // 释放之前的播放器资源
                    if (player != null) {
                        player.stop();
                    }
                    
                    // 初始化新的播放器
                    initializePlayer(nextVideo);
                    
                    // 更新标题等UI元素
                    Toast.makeText(this, "正在播放: " + nextVideo.getTitle(), Toast.LENGTH_SHORT).show();
                });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "没有更多视频", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void playPreviousVideo() {
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
                
                // 更新当前播放位置为0（结束播放）
                updatePlaybackPosition(0);
                
                // 切换到新视频
                currentVideo = prevVideo;
                
                // 在UI线程上更新播放器
                runOnUiThread(() -> {
                    // 释放之前的播放器资源
                    if (player != null) {
                        player.stop();
                    }
                    
                    // 初始化新的播放器
                    initializePlayer(prevVideo);
                    
                    // 更新标题等UI元素
                    Toast.makeText(this, "正在播放: " + prevVideo.getTitle(), Toast.LENGTH_SHORT).show();
                });
                return;
            }
        }
        
        // 如果不在播放列表中或者已经是播放列表中的第一个视频，按照原来的方式查找上一个视频
        executorService.execute(() -> {
            // 获取当前视频在列表中的位置
            Video prevVideo = videoDao.getPreviousVideoBefore(currentVideo.getId());
            
            if (prevVideo != null) {
                // 更新当前播放位置为0（结束播放）
                updatePlaybackPosition(0);
                
                // 切换到新视频
                currentVideo = prevVideo;
                
                // 在UI线程上更新播放器
                runOnUiThread(() -> {
                    // 释放之前的播放器资源
                    if (player != null) {
                        player.stop();
                    }
                    
                    // 初始化新的播放器
                    initializePlayer(prevVideo);
                    
                    // 更新标题等UI元素
                    Toast.makeText(this, "正在播放: " + prevVideo.getTitle(), Toast.LENGTH_SHORT).show();
                });
            } else {
                runOnUiThread(() -> Toast.makeText(this, "这是第一个视频", Toast.LENGTH_SHORT).show());
            }
        });
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

    // 格式化时长为 HH:MM:SS 格式
    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    public void onVideoSelected(Video video, int playlistId) {
        if (video != null) {
            // 更新当前播放位置为0（结束当前视频播放）
            if (currentVideo != null) {
                updatePlaybackPosition(0);
            }
            
            // 切换到选中的视频
            currentVideo = video;
            currentPlaylistId = playlistId;
            
            // 释放之前的播放器资源
            if (player != null) {
                player.stop();
            }
            
            // 初始化新的播放器
            initializePlayer(video);
            
            // 提示信息
            Toast.makeText(this, "正在播放: " + video.getTitle(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSubtitleListDialog() {
        if (currentVideo != null) {
            SubtitleListDialogFragment dialogFragment = SubtitleListDialogFragment.newInstance(
                    currentVideo.getId());
            dialogFragment.show(getSupportFragmentManager(), "subtitle_list_dialog");
        }
    }
    
    private void toggleSubtitles() {
        subtitlesEnabled = !subtitlesEnabled;
        if (!subtitlesEnabled) {
            // 隐藏当前字幕
            subtitleDisplayView.setVisibility(View.GONE);
        } else {
            // 重新启动字幕检查
            startSubtitleTracking();
        }
        
        // 提示用户
        Toast.makeText(this, subtitlesEnabled ? "字幕已启用" : "字幕已禁用", 
                Toast.LENGTH_SHORT).show();
    }
    
    private void startSubtitleTracking() {
        if (subtitleRunnable != null) {
            subtitleHandler.removeCallbacks(subtitleRunnable);
        }
        
        subtitleRunnable = new Runnable() {
            @Override
            public void run() {
                if (player != null && player.isPlaying() && currentVideo != null && subtitlesEnabled) {
                    // 获取当前播放位置
                    long currentPosition = player.getCurrentPosition();
                    checkAndDisplaySubtitles(currentPosition);
                }
                
                // 每100毫秒检查一次字幕
                subtitleHandler.postDelayed(this, 100);
            }
        };
        
        subtitleHandler.post(subtitleRunnable);
    }
    
    private void checkAndDisplaySubtitles(long position) {
        if (!subtitlesEnabled || currentVideo == null) {
            displaySubtitle("");
            return;
        }
        
        executorService.execute(() -> {
            Subtitle subtitle = subtitleDao.getSubtitleForTime(currentVideo.getId(), position);
            
            runOnUiThread(() -> {
                if (subtitle != null) {
                    displaySubtitle(subtitle.getText());
                } else {
                    displaySubtitle("");
                }
            });
        });
    }
    
    private void displaySubtitle(String text) {
        if (text != null && !text.isEmpty()) {
            subtitleDisplayView.setText(text);
            subtitleDisplayView.setVisibility(View.VISIBLE);
        } else {
            subtitleDisplayView.setVisibility(View.GONE);
        }
    }

    // SubtitleEditorListener 回调
    @Override
    public void onSubtitleSaved(Subtitle subtitle) {
        executorService.execute(() -> {
            if (subtitle.getId() == 0) {
                // 保存新字幕
                subtitle.setVideoId(currentVideo.getId());
                subtitleDao.insert(subtitle);
            } else {
                // 更新现有字幕
                subtitleDao.update(subtitle);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.subtitle_saved, Toast.LENGTH_SHORT).show();
                startSubtitleTracking();
            });
        });
    }
    
    // SubtitleListListener 回调
    @Override
    public void onAddSubtitle() {
        // 创建新字幕，使用当前播放时间
        long currentPosition = player.getCurrentPosition();
        Subtitle newSubtitle = new Subtitle();
        newSubtitle.setVideoId(currentVideo.getId());
        newSubtitle.setStartTime(currentPosition);
        newSubtitle.setEndTime(currentPosition + 5000); // 默认持续5秒
        newSubtitle.setText("");
        
        // 打开字幕编辑器
        SubtitleEditorDialogFragment dialogFragment = SubtitleEditorDialogFragment.newInstance(newSubtitle);
        dialogFragment.show(getSupportFragmentManager(), "subtitle_editor_dialog");
    }
    
    @Override
    public void onEditSubtitle(Subtitle subtitle) {
        // 打开字幕编辑器
        SubtitleEditorDialogFragment dialogFragment = SubtitleEditorDialogFragment.newInstance(subtitle);
        dialogFragment.show(getSupportFragmentManager(), "subtitle_editor_dialog");
    }
    
    @Override
    public void onJumpToSubtitle(Subtitle subtitle) {
        // 跳转到字幕开始时间
        if (player != null) {
            player.seekTo(subtitle.getStartTime());
            player.play();
        }
    }
    
    @Override
    public void onSubtitleDeleted(Subtitle subtitle) {
        executorService.execute(() -> {
            subtitleDao.delete(subtitle);
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.subtitle_deleted, Toast.LENGTH_SHORT).show();
                startSubtitleTracking();
            });
        });
    }
} 