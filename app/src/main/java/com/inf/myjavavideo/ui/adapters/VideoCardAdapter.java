package com.inf.myjavavideo.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.utils.MediaStoreHelper;

import java.io.File;
import java.util.List;

public class VideoCardAdapter extends RecyclerView.Adapter<VideoCardAdapter.VideoViewHolder> {

    private final Context context;
    private final List<Video> videos;
    private final OnVideoClickListener listener;

    public VideoCardAdapter(Context context, List<Video> videos, OnVideoClickListener listener) {
        this.context = context;
        this.videos = videos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_video_card, parent, false);
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
            if (video.getThumbnailPath() != null && !video.getThumbnailPath().isEmpty()) {
                Glide.with(context)
                        .load(new File(video.getThumbnailPath()))
                        .placeholder(R.color.gray)
                        .error(R.color.gray_dark)
                        .centerCrop()
                        .into(thumbnailImageView);
            } else {
                // 如果没有缩略图，使用视频路径加载第一帧
                Glide.with(context)
                        .load(video.getPath())
                        .placeholder(R.color.gray)
                        .error(R.color.gray_dark)
                        .centerCrop()
                        .into(thumbnailImageView);
            }
        }
    }

    public interface OnVideoClickListener {
        void onVideoClick(Video video);
    }
} 