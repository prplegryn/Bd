package com.prplegryn.bd

import android.app.Application
import android.webkit.CookieManager
import com.prplegryn.bd.data.AppPreferences
import com.prplegryn.bd.data.TaskRepository
import com.prplegryn.bd.network.BiliClient

class BdApplication : Application() {
    lateinit var preferences: AppPreferences
        private set
    lateinit var tasks: TaskRepository
        private set
    lateinit var client: BiliClient
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(this)
        tasks = TaskRepository(this)
        client = BiliClient(preferences)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
        }
    }
}
