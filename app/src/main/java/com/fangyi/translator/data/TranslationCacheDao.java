package com.fangyi.translator.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TranslationCacheDao {

    @Query("SELECT * FROM translation_cache WHERE originalText = :originalText LIMIT 1")
    TranslationCacheEntity findByOriginal(String originalText);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(TranslationCacheEntity entity);

    @Delete
    void delete(TranslationCacheEntity entity);

    @Query("DELETE FROM translation_cache WHERE id NOT IN " +
            "(SELECT id FROM translation_cache ORDER BY timestamp DESC LIMIT :keepCount)")
    void trimTo(int keepCount);

    @Query("SELECT COUNT(*) FROM translation_cache")
    int getCount();

    @Query("DELETE FROM translation_cache")
    void clearAll();
}
