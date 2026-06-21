package com.prplegryn.bd.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.prplegryn.bd.BdApplication
import com.prplegryn.bd.data.ContentOptions
import com.prplegryn.bd.data.DownloadTask
import com.prplegryn.bd.data.SourceInfo
import com.prplegryn.bd.data.TaskStatus
import com.prplegryn.bd.download.DownloadScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResolverState(
    val input: String = "",
    val loading: Boolean = false,
    val source: SourceInfo? = null,
    val selectedEpisodes: Set<Int> = emptySet(),
    val error: String = "",
)

data class AccountState(
    val checking: Boolean = false,
    val loggedIn: Boolean = false,
    val name: String = "",
    val vip: Boolean = false,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as BdApplication
    private val _resolver = MutableStateFlow(ResolverState())
    private val _account = MutableStateFlow(AccountState())

    val resolver = _resolver.asStateFlow()
    val account = _account.asStateFlow()
    val tasks = app.tasks.tasks
    val settings = app.preferences.settings

    init {
        refreshAccount()
    }

    fun setInput(value: String) {
        _resolver.value = _resolver.value.copy(input = value, error = "")
    }

    fun resolve() {
        val input = _resolver.value.input.trim()
        if (input.isBlank() || _resolver.value.loading) return
        viewModelScope.launch {
            _resolver.value = _resolver.value.copy(loading = true, error = "", source = null)
            runCatching { app.client.resolve(input) }
                .onSuccess { source ->
                    _resolver.value = _resolver.value.copy(
                        loading = false,
                        source = source,
                        selectedEpisodes = source.episodes.map { it.index }.toSet(),
                    )
                }
                .onFailure {
                    _resolver.value = _resolver.value.copy(
                        loading = false,
                        error = it.message ?: "解析失败",
                    )
                }
        }
    }

    fun clearSource() {
        _resolver.value = ResolverState()
    }

    fun toggleEpisode(index: Int) {
        val current = _resolver.value.selectedEpisodes
        _resolver.value = _resolver.value.copy(
            selectedEpisodes = if (index in current) current - index else current + index,
        )
    }

    fun selectAllEpisodes(selected: Boolean) {
        val source = _resolver.value.source ?: return
        _resolver.value = _resolver.value.copy(
            selectedEpisodes = if (selected) source.episodes.map { it.index }.toSet() else emptySet(),
        )
    }

    fun createTasks(content: ContentOptions) {
        val state = _resolver.value
        val source = state.source ?: return
        val userSettings = settings.value
        val newTasks = source.episodes
            .filter { it.index in state.selectedEpisodes }
            .map { episode ->
                DownloadTask(
                    sourceUrl = source.sourceUrl,
                    sourceTitle = source.title,
                    episode = episode,
                    options = content,
                    preferredVideoQuality = userSettings.videoQuality,
                    preferredAudioQuality = userSettings.audioQuality,
                    preferredCodec = userSettings.videoCodec,
                )
            }
        app.tasks.addAll(newTasks)
        newTasks.forEach { DownloadScheduler.enqueue(app, it) }
        clearSource()
    }

    fun pause(id: String) = DownloadScheduler.pause(app, id)

    fun resume(id: String) {
        app.tasks.find(id)?.let { DownloadScheduler.enqueue(app, it) }
    }

    fun cancel(id: String) = DownloadScheduler.cancel(app, id)

    fun remove(id: String) {
        DownloadScheduler.cancel(app, id)
        app.tasks.remove(id)
    }

    fun retry(id: String) = resume(id)

    fun refreshAccount() {
        if (app.preferences.cookie.isBlank()) {
            _account.value = AccountState()
            return
        }
        viewModelScope.launch {
            _account.value = _account.value.copy(checking = true)
            runCatching { app.client.accountInfo() }
                .onSuccess {
                    if (it.loggedIn) app.preferences.accountName = it.name
                    _account.value = AccountState(
                        checking = false,
                        loggedIn = it.loggedIn,
                        name = it.name,
                        vip = it.vip,
                    )
                }
                .onFailure {
                    _account.value = AccountState(
                        checking = false,
                        loggedIn = app.preferences.accountName.isNotBlank(),
                        name = app.preferences.accountName,
                    )
                }
        }
    }

    fun saveCookie(cookie: String) {
        app.preferences.cookie = cookie
        refreshAccount()
    }

    fun logout() {
        app.preferences.clearAuth()
        _account.value = AccountState()
    }

    fun updateSettings(transform: (com.prplegryn.bd.data.UserSettings) -> com.prplegryn.bd.data.UserSettings) {
        app.preferences.update(transform)
    }

    fun setStorageTree(uri: android.net.Uri?) {
        app.preferences.setStorageTree(uri)
    }

    fun activeCount(): Int = tasks.value.count {
        it.status in setOf(
            TaskStatus.QUEUED,
            TaskStatus.PREPARING,
            TaskStatus.DOWNLOADING,
            TaskStatus.MERGING,
        )
    }
}

