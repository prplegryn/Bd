package com.prplegryn.bd.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TaskRepository(context: Context) {
    private val prefs = context.getSharedPreferences("tasks", Context.MODE_PRIVATE)
    private val _tasks = MutableStateFlow(
        tasksFromJson(prefs.getString("items", "[]") ?: "[]")
            .map {
                if (it.status in activeStatuses) {
                    it.copy(status = TaskStatus.PAUSED, error = "应用重启后已暂停")
                } else {
                    it
                }
            },
    )

    val tasks = _tasks.asStateFlow()

    @Synchronized
    fun addAll(items: List<DownloadTask>) {
        _tasks.value = (items + _tasks.value).distinctBy { it.id }
        persist()
    }

    @Synchronized
    fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map {
            if (it.id == id) transform(it).copy(updatedAt = System.currentTimeMillis()) else it
        }
        persist()
    }

    @Synchronized
    fun remove(id: String) {
        _tasks.value = _tasks.value.filterNot { it.id == id }
        persist()
    }

    fun find(id: String): DownloadTask? = _tasks.value.firstOrNull { it.id == id }

    private fun persist() {
        prefs.edit().putString("items", _tasks.value.toJsonString()).apply()
    }

    private companion object {
        val activeStatuses = setOf(
            TaskStatus.QUEUED,
            TaskStatus.PREPARING,
            TaskStatus.DOWNLOADING,
            TaskStatus.MERGING,
        )
    }
}

