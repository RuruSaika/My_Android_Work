package com.inf.myjavavideo.ui.player;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.PlaylistDao;
import com.inf.myjavavideo.data.dao.PlaylistVideoDao;
import com.inf.myjavavideo.data.model.Playlist;
import com.inf.myjavavideo.data.model.Video;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaylistVideosDialogFragment extends DialogFragment {
    
    private static final String ARG_PLAYLIST_ID = "playlist_id";
    private static final String ARG_CURRENT_VIDEO_ID = "current_video_id";
    
    private int playlistId;
    private int currentVideoId;
    private PlaylistVideoListener listener;
    private VideoAdapter adapter;
    private List<Video> videos = new ArrayList<>();
    private ExecutorService executorService;
    private PlaylistVideoDao playlistVideoDao;
    private PlaylistDao playlistDao;
    private String playlistName;
    
    public interface PlaylistVideoListener {
        void onVideoSelected(Video video, int playlistId);
    }
    
    public static PlaylistVideosDialogFragment newInstance(int playlistId, int currentVideoId) {
        PlaylistVideosDialogFragment fragment = new PlaylistVideosDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PLAYLIST_ID, playlistId);
        args.putInt(ARG_CURRENT_VIDEO_ID, currentVideoId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            playlistId = getArguments().getInt(ARG_PLAYLIST_ID);
            currentVideoId = getArguments().getInt(ARG_CURRENT_VIDEO_ID);
        }
        
        executorService = Executors.newSingleThreadExecutor();
        playlistVideoDao = AppDatabase.getInstance(requireContext()).playlistVideoDao();
        playlistDao = AppDatabase.getInstance(requireContext()).playlistDao();
        
        // 初始化适配器
        adapter = new VideoAdapter(videos, video -> {
            if (listener != null) {
                listener.onVideoSelected(video, playlistId);
                dismiss();
            }
        });
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        
        // 加载播放列表名称
        loadPlaylistName();
        
        // 加载布局
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_playlist_videos, null);
        
        // 初始化RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recycler_playlist_videos);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        // 加载视频
        loadVideos();
        
        // 设置布局
        builder.setView(view);
        
        // 设置关闭按钮
        builder.setNegativeButton(R.string.close, (dialog, which) -> dismiss());
        
        return builder.create();
    }
    
    private void loadPlaylistName() {
        executorService.execute(() -> {
            Playlist playlist = playlistDao.getPlaylistById(playlistId);
            if (playlist != null) {
                playlistName = playlist.getName();
                requireActivity().runOnUiThread(() -> {
                    if (getDialog() != null && getDialog().isShowing()) {
                        getDialog().setTitle(playlistName);
                    }
                });
            }
        });
    }
    
    private void loadVideos() {
        executorService.execute(() -> {
            List<Video> loadedVideos = playlistVideoDao.getVideosForPlaylist(playlistId);
            requireActivity().runOnUiThread(() -> {
                videos.clear();
                videos.addAll(loadedVideos);
                adapter.notifyDataSetChanged();
            });
        });
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof PlaylistVideoListener) {
            listener = (PlaylistVideoListener) context;
        } else {
            throw new RuntimeException(context + " must implement PlaylistVideoListener");
        }
    }
    
    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    // 视频适配器
    public static class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.VideoViewHolder> {
        
        private final List<Video> videos;
        private final OnVideoClickListener listener;
        
        public interface OnVideoClickListener {
            void onVideoClick(Video video);
        }
        
        public VideoAdapter(List<Video> videos, OnVideoClickListener listener) {
            this.videos = videos;
            this.listener = listener;
        }
        
        @NonNull
        @Override
        public VideoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_video, parent, false);
            return new VideoViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull VideoViewHolder holder, int position) {
            Video video = videos.get(position);
            
            // 设置视频标题
            holder.videoTitle.setText(video.getTitle());
            
            // 设置视频时长
            long duration = video.getDuration();
            long minutes = (duration / 1000) / 60;
            long seconds = (duration / 1000) % 60;
            holder.videoDuration.setText(String.format("%d:%02d", minutes, seconds));
            
            // 设置视频缩略图
            if (holder.videoThumbnail != null) {
                if (video.getThumbnailPath() != null && !video.getThumbnailPath().isEmpty()) {
                    Glide.with(holder.itemView.getContext())
                            .load(new File(video.getThumbnailPath()))
                            .placeholder(R.drawable.ic_video_placeholder)
                            .error(R.drawable.ic_video_error)
                            .centerCrop()
                            .into(holder.videoThumbnail);
                } else {
                    Glide.with(holder.itemView.getContext())
                            .load(R.drawable.ic_video_placeholder)
                            .centerCrop()
                            .into(holder.videoThumbnail);
                }
            } else {
                System.out.println("警告：videoThumbnail为null，无法加载缩略图");
            }
            
            // 设置点击事件
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVideoClick(video);
                }
            });
        }
        
        @Override
        public int getItemCount() {
            return videos.size();
        }
        
        public static class VideoViewHolder extends RecyclerView.ViewHolder {
            public ImageView videoThumbnail;
            public TextView videoTitle;
            public TextView videoDuration;
            
            public VideoViewHolder(@NonNull View itemView) {
                super(itemView);
                videoThumbnail = itemView.findViewById(R.id.image_thumbnail);
                videoTitle = itemView.findViewById(R.id.text_video_title);
                videoDuration = itemView.findViewById(R.id.text_video_duration);
            }
        }
    }
} 