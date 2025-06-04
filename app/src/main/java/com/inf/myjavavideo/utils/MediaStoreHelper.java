package com.inf.myjavavideo.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import com.inf.myjavavideo.data.model.Video;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 媒体库助手类，用于从系统媒体库获取视频信息
 */
public class MediaStoreHelper {
    private static final String TAG = "MediaStoreHelper";

    /**
     * 从媒体库加载所有视频文件
     */
    public static List<Video> loadVideosFromMediaStore(Context context) {
        List<Video> videos = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            uri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        } else {
            uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        }
        
        String[] projection = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED
        };
        
        String sortOrder = MediaStore.Video.Media.DATE_ADDED + " DESC";
        
        try (Cursor cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                sortOrder
        )) {
            if (cursor != null) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);
                int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);
                int dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA);
                int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);
                int sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE);
                
                while (cursor.moveToNext()) {
                    String id = cursor.getString(idColumn);
                    String name = cursor.getString(nameColumn);
                    String path = cursor.getString(dataColumn);
                    long duration = cursor.getLong(durationColumn);
                    long size = cursor.getLong(sizeColumn);
                    
                    // 生成缩略图
                    String thumbnailPath = generateThumbnail(context, path, id);
                    
                    // 创建视频对象
                    Video video = new Video(name, path, thumbnailPath, duration, size);
                    videos.add(video);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取视频失败: " + e.getMessage());
        }
        
        return videos;
    }
    
    /**
     * 为视频生成缩略图
     * @param context 上下文
     * @param videoPath 视频路径或URI字符串
     * @param videoId 视频ID，用于缩略图文件命名
     * @return 缩略图文件路径，失败则返回空字符串
     */
    public static String generateThumbnail(Context context, String videoPath, String videoId) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        String thumbnailPath = "";
        
        try {
            Log.d(TAG, "开始为视频生成缩略图: " + videoPath);
            
            if (videoPath.startsWith("content://")) {
                // 处理内容URI
                try {
                    retriever.setDataSource(context, Uri.parse(videoPath));
                    Log.d(TAG, "使用content URI设置数据源成功");
                } catch (Exception e) {
                    Log.e(TAG, "使用content URI设置数据源失败: " + e.getMessage());
                    return thumbnailPath;
                }
            } else {
                // 处理文件路径
                try {
                    retriever.setDataSource(videoPath);
                    Log.d(TAG, "使用文件路径设置数据源成功");
                } catch (Exception e) {
                    Log.e(TAG, "使用文件路径设置数据源失败: " + e.getMessage());
                    return thumbnailPath;
                }
            }
            
            // 尝试获取视频的第一帧，如果失败则尝试不同的时间点
            Bitmap bitmap = null;
            long[] timeOffsets = {0, 1000000, 2000000, 3000000, 5000000}; // 0秒, 1秒, 2秒, 3秒, 5秒
            
            for (long timeOffset : timeOffsets) {
                try {
                    bitmap = retriever.getFrameAtTime(timeOffset, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                    if (bitmap != null && !bitmap.isRecycled() && bitmap.getWidth() > 0) {
                        Log.d(TAG, "在时间点 " + (timeOffset/1000000) + "秒成功获取帧");
                        break;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "获取时间点 " + (timeOffset/1000000) + "秒的帧失败: " + e.getMessage());
                }
            }
            
            if (bitmap == null) {
                Log.e(TAG, "所有时间点都无法获取视频帧");
                return thumbnailPath;
            }
            
            // 确保缩略图目录存在
            File thumbnailDir = new File(context.getFilesDir(), "thumbnails");
            if (!thumbnailDir.exists()) {
                boolean created = thumbnailDir.mkdirs();
                if (!created) {
                    Log.e(TAG, "无法创建缩略图目录");
                    return thumbnailPath;
                }
            }
            
            // 保存缩略图
            File file = new File(thumbnailDir, videoId + ".jpg");
            FileOutputStream fos = null;
            
            try {
                fos = new FileOutputStream(file);
                // 使用更高的压缩质量，确保缩略图清晰
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                fos.flush();
                thumbnailPath = file.getAbsolutePath();
                Log.d(TAG, "成功生成视频缩略图: " + thumbnailPath);
            } catch (IOException e) {
                Log.e(TAG, "保存缩略图失败: " + e.getMessage());
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e(TAG, "关闭文件流失败: " + e.getMessage());
                    }
                }
                
                if (bitmap != null && !bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "生成缩略图失败: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
                Log.e(TAG, "释放缩略图资源失败: " + e.getMessage());
            }
        }
        
        return thumbnailPath;
    }
    
    /**
     * 将毫秒转换为时间格式 (例如: 01:23:45)
     */
    public static String formatDuration(long durationMs) {
        // 如果时长为0，直接返回00:00:00
        if (durationMs <= 0) {
            return "00:00:00";
        }
        
        long seconds = durationMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        
        return String.format("%02d:%02d:%02d", 
                hours, 
                minutes % 60, 
                seconds % 60);
    }
    
    /**
     * 将字节转换为可读格式 (例如: 1.2 MB)
     */
    public static String formatFileSize(long size) {
        if (size <= 0) return "0";
        
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        
        return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }
}