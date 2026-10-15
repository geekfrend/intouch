package com.geekfrend.intouch

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.geekfrend.intouch.data.AppDatabase
import com.geekfrend.intouch.data.Prefs
import com.geekfrend.intouch.data.Repository
import com.geekfrend.intouch.widget.WidgetUpdater
import com.geekfrend.intouch.work.SyncWork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Holds the app-wide objects. There is no dependency-injection library; everything is created here. */
class InTouchApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var prefs: Prefs
        private set
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "intouch.db").build()
        repository = Repository(this, db, prefs)
        SyncWork.schedule(this)

        // Whenever contacts or calls change while the app is running, redraw the widgets.
        scope.launch {
            repository.dueOverview.collectLatest { WidgetUpdater.refresh(this@InTouchApp, it) }
        }
    }
}

val Context.app: InTouchApp
    get() = applicationContext as InTouchApp
