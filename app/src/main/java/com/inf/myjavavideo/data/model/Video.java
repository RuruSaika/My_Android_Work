package com.inf.myjavavideo.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * 视频实体类，用于存储视频信息
 */
@Entity(tableName = "videos")
public class Video implements Serializable {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String title;
    private String path;
    private String thumbnailPath;
    
    @ColumnInfo(name = "duration")
    private long duration;
    
    @ColumnInfo(name = "last_played_position")
    private long lastPlayedPosition;
    
    @ColumnInfo(name = "date_added")
    private long dateAdded;
    
    @ColumnInfo(name = "is_favorite")
    private boolean isFavorite;
    
    @ColumnInfo(name = "size")
    private long size;
    
    // 添加视频来源类型字段，用于区分不同来源的视频
    @ColumnInfo(name = "source_type")
    private String sourceType;
    
    public Video() {
    }
    
    @Ignore
    public Video(String title, String path, String thumbnailPath, long duration) {
        this.title = title;
        this.path = path;
        this.thumbnailPath = thumbnailPath;
        this.duration = duration;
        this.lastPlayedPosition = 0;
        this.dateAdded = System.currentTimeMillis();
        this.isFavorite = false;
        this.size = 0;
        this.sourceType = path.startsWith("content://") ? "content" : "file";
    }
    
    @Ignore
    public Video(String title, String path, String thumbnailPath, long duration, long size) {
        this.title = title;
        this.path = path;
        this.thumbnailPath = thumbnailPath;
        this.duration = duration;
        this.lastPlayedPosition = 0;
        this.dateAdded = System.currentTimeMillis();
        this.isFavorite = false;
        this.size = size;
        this.sourceType = path.startsWith("content://") ? "content" : "file";
    }
    
    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getLastPlayedPosition() {
        return lastPlayedPosition;
    }

    public void setLastPlayedPosition(long lastPlayedPosition) {
        this.lastPlayedPosition = lastPlayedPosition;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Video video = (Video) o;
        
        return id == video.id;
    }
    
    @Override
    public int hashCode() {
        return id;
    }
} 