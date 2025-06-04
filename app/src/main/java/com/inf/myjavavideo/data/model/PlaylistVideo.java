package com.inf.myjavavideo.data.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 播放列表和视频的关联实体，用于实现多对多关系
 */
@Entity(tableName = "playlist_videos",
        foreignKeys = {
                @ForeignKey(entity = Playlist.class,
                        parentColumns = "id",
                        childColumns = "playlistId",
                        onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = Video.class,
                        parentColumns = "id",
                        childColumns = "videoId",
                        onDelete = ForeignKey.CASCADE)
        },
        indices = {
                @Index("playlistId"),
                @Index("videoId")
        })
public class PlaylistVideo {
    @PrimaryKey(autoGenerate = true)
    private int id;
    
    private int playlistId;
    private int videoId;
    private int position; // 在播放列表中的位置

    public PlaylistVideo() {
    }

    @Ignore
    public PlaylistVideo(int playlistId, int videoId, int position) {
        this.playlistId = playlistId;
        this.videoId = videoId;
        this.position = position;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(int playlistId) {
        this.playlistId = playlistId;
    }

    public int getVideoId() {
        return videoId;
    }

    public void setVideoId(int videoId) {
        this.videoId = videoId;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }
} 