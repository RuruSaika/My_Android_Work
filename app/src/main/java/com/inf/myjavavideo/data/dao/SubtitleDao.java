package com.inf.myjavavideo.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.inf.myjavavideo.data.model.Subtitle;

import java.util.List;

@Dao
public interface SubtitleDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Subtitle subtitle);
    
    @Update
    void update(Subtitle subtitle);
    
    @Delete
    void delete(Subtitle subtitle);
    
    @Query("DELETE FROM subtitles WHERE id = :subtitleId")
    void deleteById(int subtitleId);
    
    @Query("DELETE FROM subtitles WHERE video_id = :videoId")
    void deleteAllByVideoId(int videoId);
    
    @Query("SELECT * FROM subtitles WHERE id = :subtitleId")
    Subtitle getSubtitleById(int subtitleId);
    
    @Query("SELECT * FROM subtitles WHERE video_id = :videoId ORDER BY start_time ASC")
    List<Subtitle> getSubtitlesForVideo(int videoId);
    
    @Query("SELECT * FROM subtitles WHERE video_id = :videoId AND " +
            "start_time <= :currentTime AND end_time >= :currentTime " +
            "ORDER BY start_time DESC LIMIT 1")
    Subtitle getSubtitleForTime(int videoId, long currentTime);
    
    @Query("SELECT COUNT(*) FROM subtitles WHERE video_id = :videoId")
    int getSubtitleCountForVideo(int videoId);
    
    @Query("DELETE FROM subtitles WHERE video_id = :videoId")
    void deleteAllSubtitlesForVideo(int videoId);
} 