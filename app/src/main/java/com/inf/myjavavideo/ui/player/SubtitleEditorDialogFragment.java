package com.inf.myjavavideo.ui.player;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.inf.myjavavideo.R;
import com.inf.myjavavideo.data.model.Subtitle;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SubtitleEditorDialogFragment extends DialogFragment {
    
    private static final String ARG_SUBTITLE = "subtitle";
    
    private Subtitle subtitle;
    private SubtitleEditorListener listener;
    
    // 视图
    private EditText startTimeEditText;
    private EditText endTimeEditText;
    private EditText subtitleEditText;
    
    public interface SubtitleEditorListener {
        void onSubtitleSaved(Subtitle subtitle);
        void onSubtitleDeleted(Subtitle subtitle);
    }
    
    public static SubtitleEditorDialogFragment newInstance(Subtitle subtitle) {
        SubtitleEditorDialogFragment fragment = new SubtitleEditorDialogFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_SUBTITLE, subtitle);
        fragment.setArguments(args);
        return fragment;
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            subtitle = (Subtitle) getArguments().getSerializable(ARG_SUBTITLE);
        }
    }
    
    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity());
        
        // 设置标题
        builder.setTitle(subtitle.getId() == 0 ? R.string.add_subtitle : R.string.edit_subtitle);
        
        // 加载布局
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_subtitle_editor, null);
        
        // 初始化视图
        startTimeEditText = view.findViewById(R.id.edit_start_time);
        endTimeEditText = view.findViewById(R.id.edit_end_time);
        subtitleEditText = view.findViewById(R.id.edit_subtitle_text);
        
        // 设置初始数据
        startTimeEditText.setText(formatTime(subtitle.getStartTime()));
        endTimeEditText.setText(formatTime(subtitle.getEndTime()));
        subtitleEditText.setText(subtitle.getText());
        
        // 设置布局
        builder.setView(view);
        
        // 设置保存按钮
        builder.setPositiveButton(R.string.save, (dialog, which) -> {
            // 收集数据
            String startTimeStr = startTimeEditText.getText().toString();
            String endTimeStr = endTimeEditText.getText().toString();
            String subtitleText = subtitleEditText.getText().toString();
            
            // 验证数据
            if (TextUtils.isEmpty(startTimeStr) || TextUtils.isEmpty(endTimeStr)) {
                Toast.makeText(getContext(), R.string.please_fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (TextUtils.isEmpty(subtitleText)) {
                Toast.makeText(getContext(), R.string.subtitle_text_cannot_be_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            
            try {
                // 解析时间
                long startTime = parseTime(startTimeStr);
                long endTime = parseTime(endTimeStr);
                
                if (endTime <= startTime) {
                    Toast.makeText(getContext(), R.string.end_time_must_be_after_start_time, Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 更新字幕对象
                subtitle.setStartTime(startTime);
                subtitle.setEndTime(endTime);
                subtitle.setText(subtitleText);
                
                // 保存字幕
                if (listener != null) {
                    listener.onSubtitleSaved(subtitle);
                }
                
            } catch (Exception e) {
                Toast.makeText(getContext(), R.string.invalid_time_format, Toast.LENGTH_SHORT).show();
            }
        });
        
        // 设置取消按钮
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dismiss());
        
        // 如果是编辑现有字幕，添加删除按钮
        if (subtitle.getId() != 0) {
            builder.setNeutralButton(R.string.delete, (dialog, which) -> {
                if (listener != null) {
                    listener.onSubtitleDeleted(subtitle);
                }
            });
        }
        
        return builder.create();
    }
    
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof SubtitleEditorListener) {
            listener = (SubtitleEditorListener) context;
        } else {
            throw new RuntimeException(context + " must implement SubtitleEditorListener");
        }
    }
    
    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }
    
    /**
     * 将毫秒时间格式化为 MM:SS.mmm 格式
     */
    private String formatTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("mm:ss.SSS", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
    
    /**
     * 将 MM:SS.mmm 格式的时间解析为毫秒
     */
    private long parseTime(String timeStr) throws Exception {
        // 支持多种格式：MM:SS.mmm、MM:SS、SS.mmm
        String[] parts;
        long minutes = 0, seconds = 0, milliseconds = 0;
        
        if (timeStr.contains(":")) {
            // 包含分钟
            parts = timeStr.split(":");
            minutes = Long.parseLong(parts[0]);
            
            if (parts[1].contains(".")) {
                // 包含毫秒
                String[] secondParts = parts[1].split("\\.");
                seconds = Long.parseLong(secondParts[0]);
                milliseconds = Long.parseLong(secondParts[1]);
            } else {
                seconds = Long.parseLong(parts[1]);
            }
        } else if (timeStr.contains(".")) {
            // 只有秒和毫秒
            parts = timeStr.split("\\.");
            seconds = Long.parseLong(parts[0]);
            milliseconds = Long.parseLong(parts[1]);
        } else {
            // 只有秒
            seconds = Long.parseLong(timeStr);
        }
        
        // 计算总毫秒数
        return minutes * 60 * 1000 + seconds * 1000 + milliseconds;
    }
} 