package com.inf.myjavavideo.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;

/**
 * 字幕实体类，用于存储用户自定义的字幕信息
 */
@Entity(tableName = "subtitles",
        foreignKeys = @ForeignKey(
                entity = Video.class,
                parentColumns = "id",
                childColumns = "video_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("video_id")})
public class Subtitle implements Serializable {
    
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    @ColumnInfo(name = "video_id")
    private int videoId;
    
    @ColumnInfo(name = "start_time")
    private long startTime; // 字幕开始时间（毫秒）
    
    @ColumnInfo(name = "end_time")
    private long endTime;   // 字幕结束时间（毫秒）
    
    private String text;    // 字幕文本内容
    
    public Subtitle() {
        // 默认构造函数，Room需要
    }
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public int getVideoId() {
        return videoId;
    }
    
    public void setVideoId(int videoId) {
        this.videoId = videoId;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public String getText() {
        return text;
    }
    
    public void setText(String text) {
        this.text = text;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        Subtitle subtitle = (Subtitle) o;
        
        return id == subtitle.id;
    }
    
    @Override
    public int hashCode() {
        return id;
    }
} 