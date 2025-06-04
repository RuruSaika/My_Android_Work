package com.inf.myjavavideo.data.model;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/**
 * 播放列表实体类
 */
@Entity(tableName = "playlists")
public class Playlist {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private String name;
    private long dateCreated;
    private int userId;
    private String thumbnailPath; // 播放列表缩略图路径

    public Playlist() {
    }

    @Ignore
    public Playlist(String name, int userId) {
        this.name = name;
        this.userId = userId;
        this.dateCreated = System.currentTimeMillis();
        this.thumbnailPath = ""; // 初始为空
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(long dateCreated) {
        this.dateCreated = dateCreated;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getThumbnailPath() {
        return thumbnailPath;
    }
    
    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }
} 