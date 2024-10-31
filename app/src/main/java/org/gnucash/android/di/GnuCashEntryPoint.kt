package org.gnucash.android.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.gnucash.android.model.Repository
import org.gnucash.android.model.export.DropboxHelper

@EntryPoint
@InstallIn(SingletonComponent::class)
interface GnuCashEntryPoint {
    fun repository(): Repository
    fun dropBoxHelper(): DropboxHelper
}