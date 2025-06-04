package com.inf.myjavavideo.ui.adapters;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.utils.MediaStoreHelper;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VideoGridAdapter extends RecyclerView.Adapter<VideoGridAdapter.VideoViewHolder> {
    private static final String TAG = "VideoGridAdapter";
    private final Context context;
    private final List<Video> videos;
    private final OnVideoClickListener listener;
    private final ExecutorService executorService;

    public VideoGridAdapter(Context context, List<Video> videos, OnVideoClickListener listener) {
        this.context = context;
        this.videos = videos;
        this.listener = listener;
        this.executorService = Executors.newFixedThreadPool(3); // 使用线程池处理缩略图生成
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video_grid, parent, false);
        return new VideoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
        Video video = videos.get(position);
        holder.bind(video);
    }

    @Override
    public int getItemCount() {
        return videos.size();
    }

    public class VideoViewHolder extends RecyclerView.ViewHolder {
        private final ImageView thumbnailImageView;
        private final TextView titleTextView;
        private final TextView durationTextView;

        public VideoViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnailImageView = itemView.findViewById(R.id.image_thumbnail);
            titleTextView = itemView.findViewById(R.id.text_title);
            durationTextView = itemView.findViewById(R.id.text_duration);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onVideoClick(videos.get(position));
                }
            });
        }

        public void bind(Video video) {
            titleTextView.setText(video.getTitle());
            
            // 设置时长
            String duration = MediaStoreHelper.formatDuration(video.getDuration());
            durationTextView.setText(duration);
            
            // 加载缩略图
            loadThumbnail(video);
        }
        
        // 加载视频缩略图
        private void loadThumbnail(Video video) {
            // 显示占位图
            thumbnailImageView.setImageResource(R.drawable.ic_video_placeholder);
            
            // 先尝试从缩略图路径加载
            if (video.getThumbnailPath() != null && !video.getThumbnailPath().isEmpty()) {
                File thumbnailFile = new File(video.getThumbnailPath());
                if (thumbnailFile.exists() && thumbnailFile.length() > 0) {
                    // 缩略图文件存在
                    Log.d(TAG, "使用已存在的缩略图: " + video.getThumbnailPath());
                    Glide.with(context)
                            .load(thumbnailFile)
                            .placeholder(R.drawable.ic_video_placeholder)
                            .diskCacheStrategy(DiskCacheStrategy.NONE) // 不使用Glide缓存
                            .skipMemoryCache(false) // 使用内存缓存提高性能
                            .centerCrop()
                            .into(thumbnailImageView);
                    return;
                }
            }
            
            // 没有有效的缩略图，尝试从视频路径加载
            String videoPath = video.getPath();
            if (videoPath != null && !videoPath.isEmpty()) {
                if (videoPath.startsWith("content://")) {
                    // 是内容URI
                    Uri videoUri = Uri.parse(videoPath);
                    Log.d(TAG, "从内容URI加载预览: " + videoUri);
                    
                    // 先用Glide直接尝试加载
                    Glide.with(context)
                            .load(videoUri)
                            .placeholder(R.drawable.ic_video_placeholder)
                            .error(R.drawable.ic_video_placeholder)
                            .centerCrop()
                            .into(thumbnailImageView);
                    
                    // 后台生成缩略图
                    generateThumbnailInBackground(video);
                } else {
                    // 是文件路径
                    File videoFile = new File(videoPath);
                    if (videoFile.exists()) {
                        Log.d(TAG, "从文件路径加载预览: " + videoPath);
                        Glide.with(context)
                                .load(videoFile)
                                .placeholder(R.drawable.ic_video_placeholder)
                                .error(R.drawable.ic_video_placeholder)
                                .centerCrop()
                                .into(thumbnailImageView);
                        
                        // 后台生成缩略图
                        generateThumbnailInBackground(video);
                    } else {
                        Log.d(TAG, "视频文件不存在: " + videoPath);
                        // 视频文件不存在，显示占位图
                        thumbnailImageView.setImageResource(R.drawable.ic_video_placeholder);
                    }
                }
            } else {
                Log.d(TAG, "视频路径为空");
                // 视频路径为空，显示占位图
                thumbnailImageView.setImageResource(R.drawable.ic_video_placeholder);
            }
        }
        
        // 在后台线程中生成缩略图
        private void generateThumbnailInBackground(Video video) {
            String videoId = String.valueOf(video.getId());
            String videoPath = video.getPath();
            
            // 检查是否已经有缩略图，只在没有有效缩略图时才生成
            if (video.getThumbnailPath() == null || video.getThumbnailPath().isEmpty() ||
                    !new File(video.getThumbnailPath()).exists()) {
                // 提交给线程池执行
                executorService.submit(() -> {
                    try {
                        Log.d(TAG, "开始为视频生成缩略图: " + videoId);
                        // 生成缩略图
                        String thumbnailPath = MediaStoreHelper.generateThumbnail(context, videoPath, videoId);
                        
                        if (thumbnailPath != null && !thumbnailPath.isEmpty() && new File(thumbnailPath).exists()) {
                            // 更新视频对象
                            video.setThumbnailPath(thumbnailPath);
                            // 更新数据库
                            AppDatabase.getInstance(context).videoDao().update(video);
                            Log.d(TAG, "缩略图生成成功并已更新到数据库: " + thumbnailPath);
                            
                            // 在主线程更新UI
                            thumbnailImageView.post(() -> {
                                // 确保View还在屏幕上显示
                                if (thumbnailImageView.getWindowToken() != null) {
                                    Glide.with(context)
                                            .load(new File(thumbnailPath))
                                            .placeholder(R.drawable.ic_video_placeholder)
                                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                                            .skipMemoryCache(false)
                                            .centerCrop()
                                            .into(thumbnailImageView);
                                }
                            });
                        } else {
                            Log.e(TAG, "生成缩略图失败或缩略图文件不存在");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "生成缩略图过程中出错: " + e.getMessage());
                    }
                });
            }
        }
    }

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }
    
    // 释放资源
    public void release() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }
} 