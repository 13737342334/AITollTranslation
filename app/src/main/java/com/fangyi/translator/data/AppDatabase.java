package com.fangyi.translator.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {TranslationCacheEntity.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    public abstract TranslationCacheDao translationCacheDao();

    public static void init(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "fangyi_translator.db"
                    ).fallbackToDestructiveMigration().build();
                }
            }
        }
    }

    public static AppDatabase getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AppDatabase not initialized. Call init() first.");
        }
        return instance;
    }
}
