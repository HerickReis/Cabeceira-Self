package com.cabeceira.app

import android.app.Application
import android.content.Context
import com.cabeceira.app.data.BookRepository
import com.cabeceira.app.data.SettingsStore
import com.cabeceira.app.data.local.AppDatabase
import com.cabeceira.app.data.remote.BookApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Injeção de dependência manual — suficiente para um MVP. */
class AppContainer(context: Context) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val database = AppDatabase.build(context)
    private val api = BookApi(http, BuildConfig.GOOGLE_BOOKS_API_KEY)

    val repository = BookRepository(context.applicationContext, database, api)
    val settings = SettingsStore(context)
}

class CabeceiraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
