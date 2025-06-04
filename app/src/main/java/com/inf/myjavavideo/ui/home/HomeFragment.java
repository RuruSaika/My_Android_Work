package com.inf.myjavavideo.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.VideoDao;
import com.inf.myjavavideo.data.model.Video;
import com.inf.myjavavideo.databinding.FragmentHomeBinding;
import com.inf.myjavavideo.ui.adapters.VideoCardAdapter;
import com.inf.myjavavideo.ui.adapters.VideoGridAdapter;
import com.inf.myjavavideo.ui.player.MediaPlayerActivity;
import com.inf.myjavavideo.utils.MediaStoreHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeFragment extends Fragment implements VideoCardAdapter.OnVideoClickListener, VideoGridAdapter.OnVideoClickListener {

    private FragmentHomeBinding binding;
    private ExecutorService executorService;
    private VideoDao videoDao;
    private VideoCardAdapter recentVideosAdapter;
    private VideoGridAdapter favoritesAdapter;
    private List<Video> recentVideos = new ArrayList<>();
    private List<Video> favoriteVideos = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        executorService = Executors.newSingleThreadExecutor();
        videoDao = AppDatabase.getInstance(requireContext()).videoDao();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerViews();
        setupSearchView();
        loadVideos();
    }

    private void setupRecyclerViews() {
        // 设置最近视频列表
        recentVideosAdapter = new VideoCardAdapter(requireContext(), recentVideos, this);
        binding.recyclerRecentVideos.setAdapter(recentVideosAdapter);

        // 设置收藏视频网格
        favoritesAdapter = new VideoGridAdapter(requireContext(), favoriteVideos, this);
        binding.recyclerFavorites.setAdapter(favoritesAdapter);
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
    }

    private void loadVideos() {
        executorService.execute(() -> {
            // 读取最近添加的视频
            final List<Video> recentList = videoDao.getVideosByDateAddedDesc();
            
            // 如果数据库中没有视频，则从媒体库中读取
            if (recentList.isEmpty()) {
                List<Video> mediaStoreVideos = MediaStoreHelper.loadVideosFromMediaStore(requireContext());
                
                // 保存到数据库
                for (Video video : mediaStoreVideos) {
                    videoDao.insert(video);
                }
                
                // 重新获取视频列表
                recentList.addAll(mediaStoreVideos);
            }
            
            // 获取收藏的视频
            final List<Video> favoriteList = videoDao.getFavoriteVideos();
            
            requireActivity().runOnUiThread(() -> {
                updateRecentVideos(recentList);
                updateFavoriteVideos(favoriteList);
                
                // 检查是否有视频
                binding.textNoVideos.setVisibility(
                        (recentList.isEmpty() && favoriteList.isEmpty()) ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void searchVideos(String query) {
        executorService.execute(() -> {
            List<Video> searchResults = videoDao.searchVideos(query);
            
            requireActivity().runOnUiThread(() -> {
                updateRecentVideos(searchResults);
                binding.textRecentVideos.setText("搜索结果");
                binding.textFavorites.setVisibility(View.GONE);
                binding.recyclerFavorites.setVisibility(View.GONE);
                
                // 检查是否有搜索结果
                binding.textNoVideos.setVisibility(
                        searchResults.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void updateRecentVideos(List<Video> videos) {
        recentVideos.clear();
        if (videos.size() > 10) {
            recentVideos.addAll(videos.subList(0, 10));
        } else {
            recentVideos.addAll(videos);
        }
        recentVideosAdapter.notifyDataSetChanged();
    }

    private void updateFavoriteVideos(List<Video> videos) {
        favoriteVideos.clear();
        favoriteVideos.addAll(videos);
        favoritesAdapter.notifyDataSetChanged();
        
        // 如果没有收藏，隐藏收藏区域
        binding.textFavorites.setVisibility(videos.isEmpty() ? View.GONE : View.VISIBLE);
        binding.recyclerFavorites.setVisibility(videos.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onVideoClick(Video video) {
        // 打开视频播放器
        Intent intent = new Intent(requireContext(), MediaPlayerActivity.class);
        intent.putExtra("video_id", video.getId());
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
}