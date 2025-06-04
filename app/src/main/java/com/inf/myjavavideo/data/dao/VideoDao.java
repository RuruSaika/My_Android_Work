package com.inf.myjavavideo.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.inf.myjavavideo.data.model.Video;

import java.util.List;

@Dao
public interface VideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Video video);

    @Update
    void update(Video video);

    @Delete
    void delete(Video video);

    @Query("SELECT * FROM videos WHERE id = :videoId")
    Video getVideoById(int videoId);

    @Query("SELECT * FROM videos ORDER BY title ASC")
    List<Video> getAllVideos();

    @Query("SELECT * FROM videos ORDER BY date_added DESC")
    List<Video> getVideosByDateAddedDesc();

    @Query("SELECT * FROM videos WHERE id > :videoId ORDER BY id ASC LIMIT 1")
    Video getNextVideoAfter(int videoId);

    @Query("SELECT * FROM videos WHERE id < :videoId ORDER BY id DESC LIMIT 1")
    Video getPreviousVideoBefore(int videoId);

    @Query("UPDATE videos SET last_played_position = :position WHERE id = :videoId")
    void updateLastPlayedPosition(int videoId, long position);

    @Query("SELECT * FROM videos WHERE title LIKE '%' || :query || '%'")
    List<Video> searchVideos(String query);

    @Query("SELECT COUNT(*) FROM videos")
    int getVideoCount();

    @Query("SELECT * FROM videos WHERE is_favorite = 1")
    List<Video> getFavoriteVideos();

    @Query("UPDATE videos SET is_favorite = :isFavorite WHERE id = :videoId")
    void updateFavoriteStatus(int videoId, boolean isFavorite);

    @Query("SELECT * FROM videos WHERE path = :path")
    Video getVideoByPath(String path);

    // 根据路径模式查询视频
    @Query("SELECT * FROM videos WHERE path LIKE :pathPattern")
    List<Video> getVideosByPathPattern(String pathPattern);
} 