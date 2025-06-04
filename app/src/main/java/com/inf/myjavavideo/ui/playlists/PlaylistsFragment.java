package com.inf.myjavavideo.ui.playlists;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.PlaylistDao;
import com.inf.myjavavideo.data.dao.PlaylistVideoDao;
import com.inf.myjavavideo.data.model.Playlist;
import com.inf.myjavavideo.data.model.PlaylistVideo;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.databinding.FragmentPlaylistsBinding;
import com.inf.myjavavideo.ui.player.PlaylistDialogFragment;
import com.inf.myjavavideo.ui.player.MediaPlayerActivity;
import com.inf.myjavavideo.utils.SessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ImageView;
import com.bumptech.glide.Glide;
import java.io.File;
import android.util.Log;

public class PlaylistsFragment extends Fragment {

    private FragmentPlaylistsBinding binding;
    private ExecutorService executorService;
    private PlaylistDao playlistDao;
    private PlaylistVideoDao playlistVideoDao;
    private SessionManager sessionManager;
    private List<Playlist> playlists = new ArrayList<>();
    private PlaylistAdapter playlistAdapter;
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        playlistDao = AppDatabase.getInstance(requireContext()).playlistDao();
        playlistVideoDao = AppDatabase.getInstance(requireContext()).playlistVideoDao();
        sessionManager = new SessionManager(requireContext());
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentPlaylistsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        // 设置创建播放列表按钮
        binding.fabCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
        
        // 初始化适配器
        playlistAdapter = new PlaylistAdapter(requireContext(), playlists);
        binding.listPlaylists.setAdapter(playlistAdapter);
        
        // 设置播放列表点击事件
        binding.listPlaylists.setOnItemClickListener((parent, v, position, id) -> {
            Playlist playlist = playlists.get(position);
            openPlaylistDetails(playlist);
        });
        
        // 添加长按删除功能
        binding.listPlaylists.setOnItemLongClickListener((parent, v, position, id) -> {
            Playlist playlist = playlists.get(position);
            showDeletePlaylistDialog(playlist);
            return true; // 返回true表示消费了长按事件
        });
        
        // 加载播放列表
        loadPlaylists();
    }
    
    private void loadPlaylists() {
        int userId = sessionManager.getUserId();
        if (userId == -1) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executorService.execute(() -> {
            final List<Playlist> userPlaylists = playlistDao.getPlaylistsByUserId(userId);
            
            requireActivity().runOnUiThread(() -> {
                // 更新UI
                updatePlaylists(userPlaylists);
                
                // 检查是否有播放列表
                binding.textNoPlaylists.setVisibility(
                        userPlaylists.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
    
    private void updatePlaylists(List<Playlist> userPlaylists) {
        playlists.clear();
        playlists.addAll(userPlaylists);
        playlistAdapter.notifyDataSetChanged();
    }
    
    private void showCreatePlaylistDialog() {
        // 显示创建播放列表对话框
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null);
        TextInputEditText editText = view.findViewById(R.id.edit_playlist_name);
        
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(view)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = editText.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createPlaylist(name);
                    } else {
                        Toast.makeText(requireContext(), "请输入播放列表名称", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    private void createPlaylist(String name) {
        int userId = sessionManager.getUserId();
        if (userId == -1) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }
        
        executorService.execute(() -> {
            Playlist playlist = new Playlist(name, userId);
            long playlistId = playlistDao.insert(playlist);
            
            requireActivity().runOnUiThread(() -> {
                if (playlistId > 0) {
                    Toast.makeText(requireContext(), R.string.playlist_created, Toast.LENGTH_SHORT).show();
                    // 重新加载播放列表
                    loadPlaylists();
                } else {
                    Toast.makeText(requireContext(), "创建播放列表失败", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
    
    private void openPlaylistDetails(Playlist playlist) {
        executorService.execute(() -> {
            // 获取播放列表中的第一个视频
            List<PlaylistVideo> playlistVideos = playlistVideoDao.getPlaylistVideos(playlist.getId());
            
            requireActivity().runOnUiThread(() -> {
                if (playlistVideos.isEmpty()) {
                    Toast.makeText(requireContext(), "播放列表为空", Toast.LENGTH_SHORT).show();
                } else {
                    // 播放第一个视频
                    int videoId = playlistVideos.get(0).getVideoId();
                    Intent intent = new Intent(requireContext(), MediaPlayerActivity.class);
                    intent.putExtra("video_id", videoId);
                    intent.putExtra("playlist_id", playlist.getId());
                    startActivity(intent);
                }
            });
        });
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

    /**
     * 显示删除播放列表确认对话框
     */
    private void showDeletePlaylistDialog(Playlist playlist) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("删除播放列表")
                .setMessage("确定要删除播放列表 \"" + playlist.getName() + "\" 吗？此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    deletePlaylist(playlist);
                })
                .setNegativeButton("取消", null)
                .show();
    }
    
    /**
     * 删除播放列表
     */
    private void deletePlaylist(Playlist playlist) {
        executorService.execute(() -> {
            try {
                // 先删除播放列表中的所有视频关联
                playlistVideoDao.deleteByPlaylistId(playlist.getId());
                
                // 然后删除播放列表
                playlistDao.delete(playlist);
                
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "播放列表已删除", Toast.LENGTH_SHORT).show();
                    // 重新加载播放列表
                    loadPlaylists();
                });
            } catch (Exception e) {
                Log.e("PlaylistsFragment", "删除播放列表失败: " + e.getMessage());
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "删除播放列表失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private class PlaylistAdapter extends ArrayAdapter<Playlist> {
        
        public PlaylistAdapter(Context context, List<Playlist> playlists) {
            super(context, R.layout.item_playlist, playlists);
        }
        
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_playlist, parent, false);
            }
            
            TextView nameTextView = convertView.findViewById(R.id.text_playlist_name);
            TextView countTextView = convertView.findViewById(R.id.text_video_count);
            ImageView thumbnailImageView = convertView.findViewById(R.id.image_playlist_thumbnail);
            
            Playlist playlist = getItem(position);
            
            if (playlist != null) {
                nameTextView.setText(playlist.getName());
                
                // 获取播放列表中的视频数量
                executorService.execute(() -> {
                    int videoCount = playlistVideoDao.getPlaylistVideoCount(playlist.getId());
                    
                    requireActivity().runOnUiThread(() -> {
                        countTextView.setText(videoCount + " 个视频");
                    });
                    
                    // 获取播放列表中的第一个视频作为缩略图
                    if (videoCount > 0 && (playlist.getThumbnailPath() == null || playlist.getThumbnailPath().isEmpty())) {
                        try {
                            List<Video> videos = playlistVideoDao.getVideosForPlaylist(playlist.getId());
                            if (videos != null && !videos.isEmpty()) {
                                Video firstVideo = videos.get(0);
                                
                                if (firstVideo.getThumbnailPath() != null && !firstVideo.getThumbnailPath().isEmpty()) {
                                    // 使用第一个视频的缩略图
                                    String thumbnailPath = firstVideo.getThumbnailPath();
                                    
                                    // 更新播放列表的缩略图路径
                                    playlist.setThumbnailPath(thumbnailPath);
                                    playlistDao.update(playlist);
                                    
                                    requireActivity().runOnUiThread(() -> {
                                        // 加载缩略图
                                        Glide.with(getContext())
                                                .load(new File(thumbnailPath))
                                                .placeholder(R.drawable.ic_video_placeholder)
                                                .error(R.drawable.ic_video_placeholder)
                                                .centerCrop()
                                                .into(thumbnailImageView);
                                    });
                                } else {
                                    // 尝试使用视频路径加载缩略图
                                    String videoPath = firstVideo.getPath();
                                    requireActivity().runOnUiThread(() -> {
                                        // 直接尝试加载视频第一帧
                                        Glide.with(getContext())
                                                .load(videoPath)
                                                .placeholder(R.drawable.ic_video_placeholder)
                                                .error(R.drawable.ic_video_placeholder)
                                                .centerCrop()
                                                .into(thumbnailImageView);
                                    });
                                }
                            }
                        } catch (Exception e) {
                            Log.e("PlaylistsFragment", "加载播放列表缩略图失败: " + e.getMessage());
                        }
                    } else if (playlist.getThumbnailPath() != null && !playlist.getThumbnailPath().isEmpty()) {
                        // 已有缩略图，直接加载
                        requireActivity().runOnUiThread(() -> {
                            Glide.with(getContext())
                                    .load(new File(playlist.getThumbnailPath()))
                                    .placeholder(R.drawable.ic_video_placeholder)
                                    .error(R.drawable.ic_video_placeholder)
                                    .centerCrop()
                                    .into(thumbnailImageView);
                        });
                    }
                });
            }
            
            return convertView;
        }
    }
} 