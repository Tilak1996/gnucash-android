package org.gnucash.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.gnucash.android.model.db.BookDbHelper
import org.gnucash.android.model.db.adapter.BooksDbAdapter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class GnuCashModule {
    @Singleton
    @Provides
    fun provideBooksDbAdapter(bookDbHelper: BookDbHelper): BooksDbAdapter =
        BooksDbAdapter(bookDbHelper.writableDatabase)
}