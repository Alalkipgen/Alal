package com.alal.notes.di

import android.content.Context
import com.alal.notes.data.dao.CategoryDao
import com.alal.notes.data.dao.DailyStatDao
import com.alal.notes.data.dao.NoteDao
import com.alal.notes.data.dao.TagDao
import com.alal.notes.data.dao.TemplateDao
import com.alal.notes.data.dao.VersionDao
import com.alal.notes.data.db.AlalDatabase
import com.alal.notes.data.prefs.UserPreferences
import com.alal.notes.data.prefs.alalDataStore
import com.alal.notes.domain.wordcount.AndroidIcuWordBreakEngine
import com.alal.notes.domain.wordcount.WordCounter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AlalDatabase = AlalDatabase.build(context)

    @Provides fun provideNoteDao(db: AlalDatabase): NoteDao = db.noteDao()
    @Provides fun provideCategoryDao(db: AlalDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideTagDao(db: AlalDatabase): TagDao = db.tagDao()
    @Provides fun provideTemplateDao(db: AlalDatabase): TemplateDao = db.templateDao()
    @Provides fun provideVersionDao(db: AlalDatabase): VersionDao = db.versionDao()
    @Provides fun provideDailyStatDao(db: AlalDatabase): DailyStatDao = db.dailyStatDao()

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences =
        UserPreferences(context.alalDataStore)

    @Provides
    @Singleton
    fun provideWordCounter(): WordCounter = WordCounter(AndroidIcuWordBreakEngine())
}
