package com.inf.myjavavideo.data.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.inf.myjavavideo.data.model.User;

import java.util.List;

@Dao
public interface UserDao {
    @Insert
    long insert(User user);

    @Update
    void update(User user);

    @Delete
    void delete(User user);

    @Query("SELECT * FROM users WHERE id = :id")
    User getUserById(int id);

    @Query("SELECT * FROM users WHERE username = :username")
    User getUserByUsername(String username);

    @Query("SELECT * FROM users WHERE phone = :phone")
    User getUserByPhone(String phone);

    @Query("SELECT * FROM users WHERE username = :usernameOrPhone OR phone = :usernameOrPhone")
    User getUserByUsernameOrPhone(String usernameOrPhone);

    @Query("SELECT * FROM users")
    List<User> getAllUsers();
} 