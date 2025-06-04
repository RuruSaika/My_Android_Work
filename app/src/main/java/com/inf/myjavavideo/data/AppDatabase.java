package com.inf.myjavavideo.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.inf.myjavavideo.data.dao.PlaylistDao;
import com.inf.myjavavideo.data.dao.PlaylistVideoDao;
import com.inf.myjavavideo.data.dao.SubtitleDao;
import com.inf.myjavavideo.data.dao.UserDao;
import com.inf.myjavavideo.data.dao.VideoDao;
import com.inf.myjavavideo.data.model.Playlist;
import com.inf.myjavavideo.data.model.PlaylistVideo;
import com.inf.myjavavideo.data.model.Subtitle;
import com.inf.myjavavideo.data.model.User;
import com.inf.myjavavideo.data.model.Video;

/**
 * 应用程序数据库类
 */
@Database(entities = {User.class, Video.class, Playlist.class, PlaylistVideo.class, Subtitle.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "video_player_db";
    private static AppDatabase instance;

    public abstract UserDao userDao();
    public abstract VideoDao videoDao();
    public abstract PlaylistDao playlistDao();
    public abstract PlaylistVideoDao playlistVideoDao();
    public abstract SubtitleDao subtitleDao();

    public static synchronized AppDatabase getInstance(Context context) {
        if (instance == null) {
            instance = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    DATABASE_NAME)
                    // 添加从版本4到版本5的迁移策略
                    .addMigrations(MIGRATION_4_5)
                    // 添加从版本5到版本6的迁移策略
                    .addMigrations(MIGRATION_5_6)
                    // 保留破坏性迁移作为备选方案
                    .fallbackToDestructiveMigration()
                    .build();
        }
        return instance;
    }
    
    // 定义从版本4到版本5的迁移策略
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 为Video表添加source_type列，默认值为"file"
            database.execSQL("ALTER TABLE videos ADD COLUMN source_type TEXT DEFAULT 'file'");
            
            // 更新已存在的content://类型URI的source_type为"content"
            database.execSQL("UPDATE videos SET source_type = 'content' WHERE path LIKE 'content://%'");
        }
    };
    
    // 定义从版本5到版本6的迁移策略
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 处理Schema变更或添加新的字段
            // 如果Playlist模型添加了thumbnailPath字段，需要执行以下操作
            database.execSQL("ALTER TABLE playlists ADD COLUMN thumbnailPath TEXT DEFAULT ''");
        }
    };
} 