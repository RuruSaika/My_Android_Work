package com.inf.myjavavideo.ui.videos;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.inf.myjavavideo.R;
import com.inf.myjavavideo.databinding.ActivityFileBrowserBinding;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FileBrowserActivity extends AppCompatActivity {

    private ActivityFileBrowserBinding binding;
    private FileAdapter adapter;
    private File currentDirectory;
    private String rootPath;
    private final List<File> fileList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityFileBrowserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // 设置工具栏
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.browse_sd_card);
        }

        // 获取根目录路径
        rootPath = getIntent().getStringExtra("ROOT_PATH");
        if (rootPath == null) {
            rootPath = "/storage";
        }
        currentDirectory = new File(rootPath);

        // 设置RecyclerView
        binding.recyclerFiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter();
        binding.recyclerFiles.setAdapter(adapter);
        binding.recyclerFiles.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // 加载文件
        loadFiles(currentDirectory.getAbsolutePath());
    }

    private void loadFiles(String path) {
        binding.progressBar.setVisibility(View.VISIBLE);
        fileList.clear();
        
        File directory = new File(path);
        currentDirectory = directory;
        binding.textCurrentPath.setText(path);

        // 检查是否可以返回上一级目录
        if (!path.equals(rootPath)) {
            File parentFile = directory.getParentFile();
            if (parentFile != null) {
                fileList.add(parentFile); // 添加父目录
            }
        }

        // 获取当前目录下的所有文件和文件夹
        File[] files = directory.listFiles();
        if (files != null) {
            // 筛选和排序文件
            List<File> sortedFiles = Arrays.asList(files);
            Collections.sort(sortedFiles, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    // 文件夹排在前面
                    if (f1.isDirectory() && !f2.isDirectory()) {
                        return -1;
                    } else if (!f1.isDirectory() && f2.isDirectory()) {
                        return 1;
                    }
                    // 按名称排序
                    return f1.getName().compareToIgnoreCase(f2.getName());
                }
            });

            for (File file : sortedFiles) {
                // 忽略隐藏文件
                if (!file.isHidden()) {
                    // 如果是目录或视频文件，则添加到列表
                    if (file.isDirectory() || isVideoFile(file.getName())) {
                        fileList.add(file);
                    }
                }
            }
        }

        // 更新UI
        binding.progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();

        // 显示空文件夹信息
        boolean isEmpty = fileList.isEmpty() || (fileList.size() == 1 && fileList.get(0).equals(directory.getParentFile()));
        binding.textEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.textEmpty.setText(R.string.no_video_files);
    }

    private boolean isVideoFile(String fileName) {
        try {
            if (fileName == null || !fileName.contains(".")) {
                return false;
            }
            String[] videoExtensions = {"mp4", "mkv", "mov", "avi", "flv", "wmv", "3gp", "webm"};
            String ext = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
            for (String videoExt : videoExtensions) {
                if (ext.equals(videoExt)) {
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    private void onFileClick(File file) {
        if (file.isDirectory()) {
            // 如果是父目录，返回上一级
            if (file.equals(currentDirectory.getParentFile())) {
                loadFiles(file.getAbsolutePath());
            } else {
                // 导航到子目录
                loadFiles(file.getAbsolutePath());
            }
        } else {
            // 选择视频文件
            try {
                // 确保文件可访问
                if (!file.canRead()) {
                    Toast.makeText(this, "无法读取文件，请检查权限", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // 获取视频时长
                long duration = 0;
                try {
                    android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                    retriever.setDataSource(file.getAbsolutePath());
                    String durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (durationStr != null) {
                        duration = Long.parseLong(durationStr);
                    }
                    retriever.release();
                    
                    // 如果获取时长失败，尝试使用MediaPlayer
                    if (duration == 0) {
                        android.media.MediaPlayer mp = new android.media.MediaPlayer();
                        mp.setDataSource(file.getAbsolutePath());
                        mp.prepare();
                        duration = mp.getDuration();
                        mp.release();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    // 获取时长失败，但仍然继续导入视频
                    Toast.makeText(this, "无法获取视频时长: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                
                // 使用FileProvider获取URI（处理Android 7.0+的FileUriExposedException）
                Uri fileUri = Uri.fromFile(file);
                Intent resultIntent = new Intent();
                resultIntent.setData(fileUri);
                
                // 添加调试信息
                resultIntent.putExtra("FILE_PATH", file.getAbsolutePath());
                resultIntent.putExtra("FILE_NAME", file.getName());
                resultIntent.putExtra("FILE_SIZE", file.length());
                resultIntent.putExtra("FILE_DURATION", duration);
                
                // 设置结果并关闭Activity
                setResult(Activity.RESULT_OK, resultIntent);
                finish();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "选择文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // 如果不是根目录，返回上一级
        if (!currentDirectory.getAbsolutePath().equals(rootPath) && currentDirectory.getParentFile() != null) {
            loadFiles(currentDirectory.getParentFile().getAbsolutePath());
        } else {
            super.onBackPressed();
        }
    }

    private class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(
                    R.layout.item_file, parent, false);
            return new FileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            File file = fileList.get(position);
            
            // 设置文件名
            String fileName = file.getName();
            
            // 对于父目录特殊处理
            if (file.equals(currentDirectory.getParentFile())) {
                fileName = "..";
                // 设置上箭头图标
                holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(FileBrowserActivity.this, R.drawable.ic_arrow_up),
                        null, null, null);
            } else if (file.isDirectory()) {
                // 设置文件夹图标
                holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(FileBrowserActivity.this, R.drawable.ic_folder),
                        null, null, null);
            } else {
                // 设置视频文件图标
                holder.fileNameTextView.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(FileBrowserActivity.this, R.drawable.ic_video_file),
                        null, null, null);
            }
            
            holder.fileNameTextView.setText(fileName);
            
            // 设置文件信息
            if (file.isDirectory()) {
                // 获取文件夹中的文件数量
                File[] files = file.listFiles();
                int count = files != null ? files.length : 0;
                holder.fileInfoTextView.setText(getString(R.string.items_count, count));
            } else {
                // 格式化文件大小
                String fileSize = Formatter.formatFileSize(FileBrowserActivity.this, file.length());
                holder.fileInfoTextView.setText(fileSize);
            }
            
            // 点击事件
            holder.itemView.setOnClickListener(v -> onFileClick(file));
        }

        @Override
        public int getItemCount() {
            return fileList.size();
        }

        class FileViewHolder extends RecyclerView.ViewHolder {
            TextView fileNameTextView;
            TextView fileInfoTextView;

            FileViewHolder(@NonNull View itemView) {
                super(itemView);
                fileNameTextView = itemView.findViewById(R.id.text_file_name);
                fileInfoTextView = itemView.findViewById(R.id.text_file_info);
            }
        }
    }
} 