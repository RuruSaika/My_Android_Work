package com.inf.myjavavideo.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.inf.myjavavideo.data.model.Playlist;

import java.util.List;

@Dao
public interface PlaylistDao {
    @Insert
    long insert(Playlist playlist);

    @Update
    void update(Playlist playlist);

    @Delete
    void delete(Playlist playlist);

    @Query("SELECT * FROM playlists WHERE id = :id")
    Playlist getPlaylistById(int id);

    @Query("SELECT * FROM playlists WHERE userId = :userId")
    List<Playlist> getPlaylistsByUserId(int userId);

    @Query("SELECT * FROM playlists WHERE userId = :userId AND name LIKE '%' || :query || '%'")
    List<Playlist> searchPlaylistsByUserId(int userId, String query);
} 