package com.fangyi.translator.data;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "translation_cache", indices = {@Index(value = "originalText", unique = true)})
public class TranslationCacheEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String originalText;
    public String translatedText;
    public String sourceLanguage;
    public String targetLanguage;
    public long timestamp;

    public TranslationCacheEntity(String originalText, String translatedText,
                                   String sourceLanguage, String targetLanguage) {
        this.originalText = originalText;
        this.translatedText = translatedText;
        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.timestamp = System.currentTimeMillis();
    }
}
