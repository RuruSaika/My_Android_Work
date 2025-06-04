package com.inf.myjavavideo.ui.player;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.AppDatabase;
import com.inf.myjavavideo.data.dao.SubtitleDao;
import com.inf.myjavavideo.data.model.Subtitle;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SubtitleListDialogFragment extends DialogFragment {
    
    private static final String ARG_VIDEO_ID = "video_id";
    
    private int videoId;
    private SubtitleListListener listener;
    private SubtitleAdapter adapter;
    private List<Subtitle> subtitles = new ArrayList<>();
    private ExecutorService executorService;
    private SubtitleDao subtitleDao;
    
    public interface SubtitleListListener {
        void onAddSubtitle();
        void onEditSubtitle(Subtitle subtitle);
        void onJumpToSubtitle(Subtitle subtitle);
    }
    
    public static SubtitleListDialogFragment newInstance(int videoId) {
        SubtitleListDialogFragment fragment = new SubtitleListDialogFragment();
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
        subtitleDao = AppDatabase.getInstance(requireContext()).subtitleDao();
        
        // 初始化适配器
        adapter = new SubtitleAdapter(subtitles, new SubtitleAdapter.SubtitleClickListener() {
            @Override
            public void onSubtitleClick(Subtitle subtitle) {
                if (listener != null) {
                    listener.onJumpToSubtitle(subtitle);
                    dismiss();
                }
            }
            
            @Override
            public void onSubtitleLongClick(Subtitle subtitle) {
                if (listener != null) {
                    listener.onEditSubtitle(subtitle);
                    dismiss();
                }
            }
        });
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        
        // 设置标题
        builder.setTitle(R.string.subtitles);
        
        // 加载布局
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_subtitle_list, null);
        
        // 初始化RecyclerView
        RecyclerView recyclerView = view.findViewById(R.id.recycler_subtitles);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);
        
        // 初始化添加按钮
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_subtitle);
        fabAdd.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAddSubtitle();
                dismiss();
            }
        });
        
        // 加载字幕
        loadSubtitles();
        
        // 设置布局
        builder.setView(view);
        
        // 添加关闭按钮
        builder.setNegativeButton(R.string.close, (dialog, which) -> dismiss());
        
        return builder.create();
    }
    
    private void loadSubtitles() {
        executorService.execute(() -> {
            List<Subtitle> loadedSubtitles = subtitleDao.getSubtitlesForVideo(videoId);
            requireActivity().runOnUiThread(() -> {
                subtitles.clear();
                subtitles.addAll(loadedSubtitles);
                adapter.notifyDataSetChanged();
            });
        });
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof SubtitleListListener) {
            listener = (SubtitleListListener) context;
        } else {
            throw new RuntimeException(context + " must implement SubtitleListListener");
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
    
    // 字幕适配器
    public static class SubtitleAdapter extends RecyclerView.Adapter<SubtitleAdapter.SubtitleViewHolder> {
        
        private final List<Subtitle> subtitles;
        private final SubtitleClickListener listener;
        
        public interface SubtitleClickListener {
            void onSubtitleClick(Subtitle subtitle);
            void onSubtitleLongClick(Subtitle subtitle);
        }
        
        public SubtitleAdapter(List<Subtitle> subtitles, SubtitleClickListener listener) {
            this.subtitles = subtitles;
            this.listener = listener;
        }
        
        @NonNull
        @Override
        public SubtitleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_subtitle, parent, false);
            return new SubtitleViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull SubtitleViewHolder holder, int position) {
            Subtitle subtitle = subtitles.get(position);
            
            // 设置字幕文本
            holder.textSubtitle.setText(subtitle.getText());
            
            // 设置字幕时间范围
            SimpleDateFormat sdf = new SimpleDateFormat("mm:ss", Locale.getDefault());
            String timeRange = sdf.format(new Date(subtitle.getStartTime())) + " - " + 
                    sdf.format(new Date(subtitle.getEndTime()));
            holder.textTimeRange.setText(timeRange);
            
            // 设置点击事件
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onSubtitleClick(subtitle);
                }
            });
            
            // 设置长按事件
            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onSubtitleLongClick(subtitle);
                    return true;
                }
                return false;
            });
        }
        
        @Override
        public int getItemCount() {
            return subtitles.size();
        }
        
        public static class SubtitleViewHolder extends RecyclerView.ViewHolder {
            public TextView textSubtitle;
            public TextView textTimeRange;
            
            public SubtitleViewHolder(@NonNull View itemView) {
                super(itemView);
                textSubtitle = itemView.findViewById(R.id.text_subtitle);
                textTimeRange = itemView.findViewById(R.id.text_time_range);
            }
        }
    }
}