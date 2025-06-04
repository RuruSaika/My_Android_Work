package com.inf.myjavavideo.ui.player;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.PlaylistDao;
import com.inf.myjavavideo.data.dao.PlaylistVideoDao;
import com.inf.myjavavideo.data.model.Playlist;
import com.inf.myjavavideo.data.model.PlaylistVideo;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.utils.SessionManager;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaylistDialogFragment extends DialogFragment {
    
    private static final String ARG_VIDEO_ID = "video_id";
    
    private int videoId;
    private ExecutorService executorService;
    private PlaylistDao playlistDao;
    private PlaylistVideoDao playlistVideoDao;
    private SessionManager sessionManager;
    private List<Playlist> playlists;
    private PlaylistAdapter adapter;
    private ListView listView;
    private TextView emptyTextView;
    
    public static PlaylistDialogFragment newInstance(int videoId) {
        PlaylistDialogFragment fragment = new PlaylistDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_VIDEO_ID, videoId);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            videoId = getArguments().getInt(ARG_VIDEO_ID);
        }
        
        executorService = Executors.newSingleThreadExecutor();
        AppDatabase db = AppDatabase.getInstance(requireContext());
        playlistDao = db.playlistDao();
        playlistVideoDao = db.playlistVideoDao();
        sessionManager = new SessionManager(requireContext());
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_playlist, null);
        
        listView = view.findViewById(R.id.list_playlists);
        emptyTextView = view.findViewById(R.id.text_empty_playlists);
        Button createButton = view.findViewById(R.id.button_create_playlist);
        
        // 加载播放列表
        loadPlaylists();
        
        // 创建新播放列表按钮
        createButton.setOnClickListener(v -> showCreatePlaylistDialog());
        
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.add_to_playlist)
                .setView(view)
                .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss())
                .create();
    }
    
    private void loadPlaylists() {
        int userId = sessionManager.getUserId();
        if (userId == -1) {
            Toast.makeText(requireContext(), "请先登录", Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }
        
        executorService.execute(() -> {
            playlists = playlistDao.getPlaylistsByUserId(userId);
            
            requireActivity().runOnUiThread(() -> {
                if (playlists.isEmpty()) {
                    listView.setVisibility(View.GONE);
                    emptyTextView.setVisibility(View.VISIBLE);
                } else {
                    listView.setVisibility(View.VISIBLE);
                    emptyTextView.setVisibility(View.GONE);
                    
                    adapter = new PlaylistAdapter(requireContext(), playlists);
                    listView.setAdapter(adapter);
                    
                    listView.setOnItemClickListener((parent, view, position, id) -> {
                        Playlist selectedPlaylist = playlists.get(position);
                        addVideoToPlaylist(selectedPlaylist.getId());
                    });
                }
            });
        });
    }
    
    private void showCreatePlaylistDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_create_playlist, null);
        EditText nameEditText = view.findViewById(R.id.edit_playlist_name);
        
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.create_playlist)
                .setView(view)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = nameEditText.getText().toString().trim();
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
        executorService.execute(() -> {
            Playlist playlist = new Playlist(name, userId);
            long playlistId = playlistDao.insert(playlist);
            
            if (playlistId > 0) {
                // 创建成功后刷新列表
                playlists = playlistDao.getPlaylistsByUserId(userId);
                
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), R.string.playlist_created, Toast.LENGTH_SHORT).show();
                    
                    // 更新ListView
                    if (playlists.isEmpty()) {
                        listView.setVisibility(View.GONE);
                        emptyTextView.setVisibility(View.VISIBLE);
                    } else {
                        listView.setVisibility(View.VISIBLE);
                        emptyTextView.setVisibility(View.GONE);
                        
                        adapter = new PlaylistAdapter(requireContext(), playlists);
                        listView.setAdapter(adapter);
                    }
                    
                    // 添加视频到新创建的播放列表
                    addVideoToPlaylist((int) playlistId);
                });
            } else {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "创建播放列表失败", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    private void addVideoToPlaylist(int playlistId) {
        executorService.execute(() -> {
            // 检查视频是否已在播放列表中
            List<PlaylistVideo> existingItems = playlistVideoDao.getPlaylistVideos(playlistId);
            boolean alreadyExists = false;
            
            for (PlaylistVideo item : existingItems) {
                if (item.getVideoId() == videoId) {
                    alreadyExists = true;
                    break;
                }
            }
            
            if (!alreadyExists) {
                // 获取播放列表中的最大位置
                int position = playlistVideoDao.getMaxPositionForPlaylist(playlistId);
                
                // 添加视频到播放列表
                PlaylistVideo playlistVideo = new PlaylistVideo(playlistId, videoId, position + 1);
                long result = playlistVideoDao.insert(playlistVideo);
                
                requireActivity().runOnUiThread(() -> {
                    if (result > 0) {
                        Toast.makeText(requireContext(), R.string.video_added_to_playlist, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "添加失败", Toast.LENGTH_SHORT).show();
                    }
                    dismiss();
                });
            } else {
                // 如果视频已存在，提示是否移除
                requireActivity().runOnUiThread(() -> {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.video_already_in_playlist)
                            .setMessage(R.string.confirm_remove_video)
                            .setPositiveButton(R.string.delete, (dialog, which) -> {
                                removeVideoFromPlaylist(playlistId);
                            })
                            .setNegativeButton(R.string.cancel, (dialog, which) -> dismiss())
                            .show();
                });
            }
        });
    }
    
    private void removeVideoFromPlaylist(int playlistId) {
        executorService.execute(() -> {
            playlistVideoDao.deleteByPlaylistAndVideo(playlistId, videoId);
            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(), R.string.video_removed_from_playlist, Toast.LENGTH_SHORT).show();
                dismiss();
            });
        });
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
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
                    
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        requireActivity().runOnUiThread(() -> {
                            countTextView.setText(videoCount + " 个视频");
                        });
                    }
                    
                    // 获取播放列表中的第一个视频作为缩略图
                    if (videoCount > 0 && (playlist.getThumbnailPath() == null || playlist.getThumbnailPath().isEmpty())) {
                        List<Video> playlistVideos = playlistVideoDao.getVideosForPlaylist(playlist.getId());
                        if (!playlistVideos.isEmpty()) {
                            Video firstVideo = playlistVideos.get(0);
                            if (firstVideo.getThumbnailPath() != null && !firstVideo.getThumbnailPath().isEmpty()) {
                                // 使用第一个视频的缩略图
                                String thumbnailPath = firstVideo.getThumbnailPath();
                                // 更新播放列表的缩略图路径
                                playlist.setThumbnailPath(thumbnailPath);
                                playlistDao.update(playlist);
                                
                                if (getActivity() != null && !getActivity().isFinishing()) {
                                    requireActivity().runOnUiThread(() -> {
                                        // 加载缩略图
                                        Glide.with(getContext())
                                                .load(new File(thumbnailPath))
                                                .placeholder(R.drawable.ic_video_placeholder)
                                                .error(R.drawable.ic_video_placeholder)
                                                .centerCrop()
                                                .into(thumbnailImageView);
                                    });
                                }
                            } else {
                                // 尝试使用视频路径加载缩略图
                                String videoPath = firstVideo.getPath();
                                if (getActivity() != null && !getActivity().isFinishing()) {
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
                        }
                    } else if (playlist.getThumbnailPath() != null && !playlist.getThumbnailPath().isEmpty()) {
                        // 已有缩略图，直接加载
                        if (getActivity() != null && !getActivity().isFinishing()) {
                            requireActivity().runOnUiThread(() -> {
                                Glide.with(getContext())
                                        .load(new File(playlist.getThumbnailPath()))
                                        .placeholder(R.drawable.ic_video_placeholder)
                                        .error(R.drawable.ic_video_placeholder)
                                        .centerCrop()
                                        .into(thumbnailImageView);
                            });
                        }
                    }
                });
            }
            
            return convertView;
        }
    }
} 