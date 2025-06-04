package com.inf.myjavavideo.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.inf.myjavavideo.data.model.PlaylistVideo;
import com.inf.myjavavideo.data.model.Video;

import java.util.List;

@Dao
public interface PlaylistVideoDao {
    @Insert
    long insert(PlaylistVideo playlistVideo);

    @Update
    void update(PlaylistVideo playlistVideo);

    @Delete
    void delete(PlaylistVideo playlistVideo);

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    void deleteByPlaylistAndVideo(int playlistId, int videoId);

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position")
    List<PlaylistVideo> getPlaylistVideos(int playlistId);

    @Transaction
    @Query("SELECT v.* FROM videos v INNER JOIN playlist_videos pv ON v.id = pv.videoId WHERE pv.playlistId = :playlistId ORDER BY pv.position")
    List<Video> getVideosForPlaylist(int playlistId);

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    int getVideoCountForPlaylist(int playlistId);

    @Query("SELECT MAX(position) FROM playlist_videos WHERE playlistId = :playlistId")
    int getMaxPositionForPlaylist(int playlistId);

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    int getPlaylistVideoCount(int playlistId);

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    void deleteByPlaylistId(int playlistId);
} 