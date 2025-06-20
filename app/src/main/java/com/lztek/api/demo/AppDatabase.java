//package com.lztek.api.demo;
//
//import android.content.Context;
//
//import androidx.room.Database;
//import androidx.room.Room;
//import androidx.room.RoomDatabase;
//
//@Database(entities = {Session.class}, version = 2, exportSchema = false)
//public abstract class AppDatabase extends RoomDatabase {
//    public abstract SessionDao sessionDao();
//
//    private static volatile AppDatabase INSTANCE;
//
//    public static AppDatabase getDatabase(Context context) {
//        if (INSTANCE == null) {
//            synchronized (AppDatabase.class) {
//                if (INSTANCE == null) {
//                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
//                            AppDatabase.class, "OfflineSessionsDB_v2") // Change database name to avoid conflict
//                            .fallbackToDestructiveMigration()
//                            .fallbackToDestructiveMigrationFrom(1)
//                            .build();
//                }
//            }
//        }
//        return INSTANCE;
//    }
//}


package com.lztek.api.demo;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {Session.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    public abstract SessionDao sessionDao();

    private static volatile AppDatabase INSTANCE;

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE sessions ADD COLUMN vital_json TEXT");
            database.execSQL("ALTER TABLE sessions ADD COLUMN audio_path TEXT");
            database.execSQL("ALTER TABLE sessions ADD COLUMN video_path TEXT");
            database.execSQL("ALTER TABLE sessions ADD COLUMN photo_path TEXT");
        }
    };

    public static AppDatabase getDatabase(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "OfflineSessionsDB")
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}