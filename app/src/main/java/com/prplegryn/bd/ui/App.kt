package com.prplegryn.bd.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.prplegryn.bd.data.ContentOptions
import com.prplegryn.bd.data.DanmakuFormat
import com.prplegryn.bd.data.DownloadTask
import com.prplegryn.bd.data.SourceInfo
import com.prplegryn.bd.data.TaskStatus
import com.prplegryn.bd.data.UserSettings
import kotlin.math.roundToInt

private enum class MainScreen { HOME, TASKS, SETTINGS }

@Composable
fun BdApp(viewModel: MainViewModel) {
    var screen by remember { mutableStateOf(MainScreen.HOME) }
    var loginVisible by remember { mutableStateOf(false) }
    val resolver by viewModel.resolver.collectAsState()

    if (loginVisible) {
        LoginScreen(
            onCookie = viewModel::saveCookie,
            onClose = { loginVisible = false },
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationDestination(
                    selected = screen == MainScreen.HOME,
                    label = "首页",
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    onClick = { screen = MainScreen.HOME },
                )
                NavigationDestination(
                    selected = screen == MainScreen.TASKS,
                    label = "任务",
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    onClick = { screen = MainScreen.TASKS },
                )
                NavigationDestination(
                    selected = screen == MainScreen.SETTINGS,
                    label = "设置",
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { screen = MainScreen.SETTINGS },
                )
            }
        },
    ) { padding ->
        AnimatedContent(
            targetState = screen,
            label = "main-navigation",
            modifier = Modifier.padding(padding),
        ) { target ->
            when (target) {
                MainScreen.HOME -> HomeScreen(
                    viewModel = viewModel,
                    onOpenTasks = { screen = MainScreen.TASKS },
                )
                MainScreen.TASKS -> TasksScreen(viewModel)
                MainScreen.SETTINGS -> SettingsScreen(
                    viewModel = viewModel,
                    onLogin = { loginVisible = true },
                )
            }
        }
    }

    if (resolver.source != null) {
        TaskSetupSheet(
            source = resolver.source!!,
            selectedCount = resolver.selectedEpisodes.size,
            settings = viewModel.settings.collectAsState().value,
            onDismiss = viewModel::clearSource,
            onToggleEpisode = viewModel::toggleEpisode,
            selectedEpisodes = resolver.selectedEpisodes,
            onSelectAll = viewModel::selectAllEpisodes,
            onStart = {
                viewModel.createTasks(it)
                screen = MainScreen.TASKS
            },
        )
    }
}

@Composable
private fun RowScope.NavigationDestination(
    selected: Boolean,
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            } else {
                Color.Transparent
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                icon()
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun HomeScreen(
    viewModel: MainViewModel,
    onOpenTasks: () -> Unit,
) {
    val state by viewModel.resolver.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val clipboard = LocalClipboardManager.current
    val activeCount = tasks.count { it.status in activeTaskStatuses }
    val failedCount = tasks.count { it.status == TaskStatus.FAILED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Bd",
                        style = MaterialTheme.typography.displaySmall,
                        letterSpacing = (-1).sp,
                    )
                    Text(
                        "链接、选集、下载，一处完成",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        when {
                            activeCount > 0 -> "$activeCount 进行中"
                            failedCount > 0 -> "$failedCount 需处理"
                            else -> "就绪"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "添加下载",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        "粘贴或分享链接，识别后选择要保存的内容。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::setInput,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("视频链接或编号") },
                        placeholder = { Text("例如 BV…、av…、ep… 或网页链接") },
                        trailingIcon = {
                            if (state.input.isNotBlank()) {
                                IconButton(onClick = { viewModel.setInput("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "清空")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboard.getText()?.text?.let(viewModel::setInput)
                            },
                            modifier = Modifier.weight(0.42f),
                        ) {
                            Text("粘贴链接")
                        }
                        Button(
                            onClick = viewModel::resolve,
                            enabled = state.input.isNotBlank() && !state.loading,
                            modifier = Modifier.weight(0.58f),
                        ) {
                            if (state.loading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(19.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (state.loading) "正在识别" else "识别链接")
                        }
                    }
                    if (state.error.isNotBlank()) {
                        Text(
                            state.error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(18.dp))
            SectionHeader(
                title = "最近任务",
                action = if (tasks.isNotEmpty()) "查看全部" else null,
                modifier = if (tasks.isNotEmpty()) Modifier.clickable(onClick = onOpenTasks) else Modifier,
            )
        }
        if (tasks.isEmpty()) {
            item {
                EmptyState(
                    title = "还没有下载任务",
                    detail = "粘贴链接后，应用会先展示内容和选集，再由你确认下载。",
                )
            }
        } else {
            items(tasks.take(4), key = { it.id }) { task ->
                CompactTaskRow(task, onClick = onOpenTasks)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSetupSheet(
    source: SourceInfo,
    selectedCount: Int,
    settings: UserSettings,
    selectedEpisodes: Set<Int>,
    onDismiss: () -> Unit,
    onToggleEpisode: (Int) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onStart: (ContentOptions) -> Unit,
) {
    var options by remember(source.sourceUrl) { mutableStateOf(settings.content) }
    var showEpisodes by remember(source.sourceUrl) { mutableStateOf(source.episodes.size > 1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.outline),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = source.coverUrl.replace("http://", "https://"),
                        contentDescription = null,
                        modifier = Modifier
                            .size(width = 104.dp, height = 68.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            sourceTypeLabel(source.type),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            source.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (source.owner.isNotBlank()) {
                            Text(
                                source.owner,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (source.episodes.size > 1) {
                item {
                    SettingRow(
                        title = "选集",
                        value = "已选 $selectedCount / ${source.episodes.size}",
                        modifier = Modifier.clickable { showEpisodes = !showEpisodes },
                    )
                }
                if (showEpisodes) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(onClick = { onSelectAll(selectedCount != source.episodes.size) }) {
                                Text(if (selectedCount == source.episodes.size) "取消全选" else "全选")
                            }
                        }
                    }
                    items(source.episodes, key = { it.index }) { episode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleEpisode(episode.index) }
                                .padding(horizontal = 16.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = episode.index in selectedEpisodes,
                                onCheckedChange = { onToggleEpisode(episode.index) },
                            )
                            Text(
                                "${episode.index}. ${episode.title}",
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            }
            item {
                SectionHeader("下载内容")
                OptionGrid(options = options, onChange = { options = it })
            }
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "${videoQualityLabel(settings.videoQuality)} · " +
                            "${audioQualityLabel(settings.audioQuality)} · ${settings.videoCodec.uppercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { onStart(options) },
                        enabled = selectedCount > 0 && (
                            options.video || options.audio || options.subtitles ||
                                options.danmaku || options.cover || options.metadata
                            ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(if (selectedCount > 1) "开始 $selectedCount 个下载" else "开始下载")
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionGrid(
    options: ContentOptions,
    onChange: (ContentOptions) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        OptionSwitch("视频", options.video) { onChange(options.copy(video = it)) }
        OptionSwitch("音频", options.audio) { onChange(options.copy(audio = it)) }
        OptionSwitch("字幕", options.subtitles) { onChange(options.copy(subtitles = it)) }
        OptionSwitch("弹幕", options.danmaku) { onChange(options.copy(danmaku = it)) }
        OptionSwitch("封面文件", options.cover) { onChange(options.copy(cover = it)) }
        OptionSwitch("媒体元数据", options.metadata) { onChange(options.copy(metadata = it)) }
        OptionSwitch("章节信息", options.chapterInfo) { onChange(options.copy(chapterInfo = it)) }
        if (options.danmaku) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("弹幕格式", modifier = Modifier.weight(1f))
                listOf(DanmakuFormat.ASS, DanmakuFormat.XML).forEach { format ->
                    FilterChip(
                        selected = options.danmakuFormat == format,
                        onClick = { onChange(options.copy(danmakuFormat = format)) },
                        label = { Text(format.name) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TasksScreen(viewModel: MainViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    val visible = tasks.filter {
        if (tab == 0) it.status != TaskStatus.COMPLETED else it.status == TaskStatus.COMPLETED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            "下载任务",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
        )
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("进行中 ${tasks.count { it.status != TaskStatus.COMPLETED }}") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("已完成 ${tasks.count { it.status == TaskStatus.COMPLETED }}") },
            )
        }
        if (visible.isEmpty()) {
            EmptyState(
                title = if (tab == 0) "没有进行中的任务" else "还没有已完成内容",
                detail = if (tab == 0) "从首页添加链接并确认选集。" else "下载完成后会出现在这里。",
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(top = 10.dp, bottom = 28.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(visible, key = { it.id }) { task ->
                    TaskRow(
                        task = task,
                        onPause = { viewModel.pause(task.id) },
                        onResume = { viewModel.resume(task.id) },
                        onRetry = { viewModel.retry(task.id) },
                        onRemove = { viewModel.remove(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactTaskRow(task: DownloadTask, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = task.episode.coverUrl.replace("http://", "https://"),
            contentDescription = null,
            modifier = Modifier
                .size(width = 84.dp, height = 56.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(task.episode.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                taskStatusLabel(task.status),
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.status == TaskStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (task.status in activeTaskStatuses) {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }
        Text(
            "${task.progress}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var errorExpanded by remember(task.id, task.error) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = task.episode.coverUrl.replace("http://", "https://"),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 92.dp, height = 62.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        task.sourceTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TaskAction(task, onPause, onResume, onRetry, onRemove)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    taskStatusLabel(task.status),
                    style = MaterialTheme.typography.labelLarge,
                    color = when (task.status) {
                        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
                        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (task.speedBytesPerSecond > 0) {
                    Text(
                        "${formatBytes(task.speedBytesPerSecond)}/s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (task.status in activeTaskStatuses || task.status == TaskStatus.PAUSED) {
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                ) {
                    Text(
                        "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${task.progress}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (task.error.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.64f),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "下载失败",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    task.errorSummary(),
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.78f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { errorExpanded = !errorExpanded }) {
                                Text(if (errorExpanded) "收起" else "详情")
                            }
                        }
                        if (errorExpanded) {
                            Text(
                                task.error,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 12,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            TextButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(taskErrorReport(task)))
                                    Toast.makeText(context, "已复制报错", Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Text("复制报错")
                            }
                        }
                    }
                }
            }
            if (task.status == TaskStatus.COMPLETED && task.outputUri.isNotBlank()) {
                TextButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(Uri.parse(task.outputUri), "video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            ContextCompat.startActivity(context, intent, null)
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("打开文件")
                }
            }
        }
    }
}

private fun DownloadTask.errorSummary(): String {
    if (error.contains("HTTP 403")) return "视频流下载被拒绝 · HTTP 403"
    return error.lineSequence()
        .firstOrNull { it.isNotBlank() && !it.startsWith("下载失败") }
        ?.take(48)
        ?: "请查看详情"
}

private fun taskErrorReport(task: DownloadTask): String {
    if (task.error.startsWith("下载失败")) return task.error
    return buildString {
        appendLine("下载失败")
        appendLine("任务：${task.episode.title}")
        appendLine("来源：${task.sourceTitle}")
        appendLine("入口：${task.sourceUrl}")
        appendLine("编号：${task.episode.bvid.ifBlank { "av${task.episode.aid}" }} / cid=${task.episode.cid}")
        appendLine()
        appendLine(task.error)
    }.trim()
}

@Composable
private fun TaskAction(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    when (task.status) {
        TaskStatus.QUEUED,
        TaskStatus.PREPARING,
        TaskStatus.DOWNLOADING,
        TaskStatus.MERGING
        -> IconButton(
            onClick = onPause,
            modifier = Modifier.semantics { contentDescription = "暂停" },
        ) {
            Text("Ⅱ", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        TaskStatus.PAUSED,
        TaskStatus.CANCELLED
        -> IconButton(onClick = onResume) {
            Icon(Icons.Default.PlayArrow, contentDescription = "继续")
        }
        TaskStatus.FAILED -> IconButton(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = "重试")
        }
        TaskStatus.COMPLETED -> IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "移除记录")
        }
    }
}

@Composable
private fun SettingsScreen(
    viewModel: MainViewModel,
    onLogin: () -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val account by viewModel.account.collectAsState()
    var qualityDialog by remember { mutableStateOf<String?>(null) }
    var contentDialog by remember { mutableStateOf(false) }
    var danmakuDialog by remember { mutableStateOf(false) }
    var keywordDialog by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            val resolver = (viewModel.getApplication<android.app.Application>()).contentResolver
            resolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        viewModel.setStorageTree(uri)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 30.dp),
    ) {
        item {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
        item {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clickable { if (!account.loggedIn) onLogin() },
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (account.loggedIn) account.name.take(1) else "B",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (account.loggedIn) account.name else "登录与 Cookie",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            when {
                                account.checking -> "正在检查登录状态"
                                account.vip -> "大会员账号 · Cookie 已记忆"
                                account.loggedIn -> "已登录 · Cookie 已记忆"
                                else -> "使用内置浏览器登录，解锁账号可用画质"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (account.loggedIn) {
                        TextButton(onClick = viewModel::logout) { Text("退出") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)); SectionHeader("媒体偏好") }
        item {
            SettingRow(
                "视频画质",
                videoQualityLabel(settings.videoQuality),
                modifier = Modifier.clickable { qualityDialog = "video" },
            )
            SoftDivider()
            SettingRow(
                "音频质量",
                audioQualityLabel(settings.audioQuality),
                modifier = Modifier.clickable { qualityDialog = "audio" },
            )
            SoftDivider()
            SettingRow(
                "视频编码",
                settings.videoCodec.uppercase(),
                description = "优先选择；不可用时自动回退",
                modifier = Modifier.clickable { qualityDialog = "codec" },
            )
        }
        item { Spacer(Modifier.height(12.dp)); SectionHeader("存储与任务") }
        item {
            SettingRow(
                "下载目录",
                if (settings.storageTreeUri.isBlank()) "系统下载/Bd" else "自定义目录/Bd",
                description = if (settings.storageTreeUri.isBlank()) {
                    "无需存储权限；也可以选择外部目录"
                } else {
                    Uri.parse(settings.storageTreeUri).lastPathSegment.orEmpty()
                },
                modifier = Modifier.clickable { folderLauncher.launch(null) },
            )
            SoftDivider()
            SettingRow(
                "覆盖同名文件",
                description = "关闭时自动添加序号，避免误删已有内容",
                showChevron = false,
                trailing = {
                    Switch(
                        checked = settings.overwrite,
                        onCheckedChange = {
                            viewModel.updateSettings { value -> value.copy(overwrite = it) }
                        },
                    )
                },
            )
            SoftDivider()
            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row {
                    Text("并行任务数", modifier = Modifier.weight(1f))
                    Text(
                        settings.parallelTasks.toString(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = settings.parallelTasks.toFloat(),
                    onValueChange = {
                        viewModel.updateSettings { value ->
                            value.copy(parallelTasks = it.roundToInt().coerceIn(1, 4))
                        }
                    },
                    valueRange = 1f..4f,
                    steps = 2,
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)); SectionHeader("默认下载内容") }
        item {
            SettingRow(
                "下载内容",
                contentSummary(settings.content),
                description = "视频、音频、字幕、弹幕、封面等默认开关",
                modifier = Modifier.clickable { contentDialog = true },
            )
        }
        item { Spacer(Modifier.height(12.dp)); SectionHeader("弹幕样式与过滤") }
        item {
            SettingRow(
                "弹幕字体",
                settings.danmakuFont,
                modifier = Modifier.clickable { qualityDialog = "font" },
            )
            SoftDivider()
            SettingRow(
                "弹幕样式",
                "${(settings.danmakuOpacity * 100).roundToInt()}% · ${String.format("%.1fx", settings.danmakuSpeed)}",
                description = "不透明度、滚动速度",
                modifier = Modifier.clickable { danmakuDialog = true },
            )
            SoftDivider()
            SettingRow(
                "关键词过滤",
                keywordSummary(settings.blockedKeywords),
                description = "每行一个关键词或正则",
                modifier = Modifier.clickable { keywordDialog = true },
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            SectionHeader("关于")
            SettingRow(
                title = "Bd",
                value = "1.0.0",
                description = "原生 Android 下载与离线管理工具",
                showChevron = false,
            )
        }
    }

    qualityDialog?.let { kind ->
        ChoiceDialog(
            kind = kind,
            settings = settings,
            onDismiss = { qualityDialog = null },
            onSettings = {
                viewModel.updateSettings { _ -> it }
                qualityDialog = null
            },
        )
    }
    if (contentDialog) {
        DownloadContentDialog(
            options = settings.content,
            onChange = { value -> viewModel.updateSettings { it.copy(content = value) } },
            onDismiss = { contentDialog = false },
        )
    }
    if (danmakuDialog) {
        DanmakuStyleDialog(
            settings = settings,
            onSettings = { value -> viewModel.updateSettings { value } },
            onDismiss = { danmakuDialog = false },
        )
    }
    if (keywordDialog) {
        KeywordDialog(
            value = settings.blockedKeywords,
            onConfirm = { value -> viewModel.updateSettings { it.copy(blockedKeywords = value) } },
            onDismiss = { keywordDialog = false },
        )
    }
}

@Composable
private fun DownloadContentDialog(
    options: ContentOptions,
    onChange: (ContentOptions) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("默认下载内容") },
        text = {
            Column {
                Text(
                    "新任务会默认使用这些内容开关，单个任务仍可在确认面板中调整。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OptionGrid(options = options, onChange = onChange)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun DanmakuStyleDialog(
    settings: UserSettings,
    onSettings: (UserSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("弹幕样式") },
        text = {
            Column {
                Row {
                    Text("不透明度", modifier = Modifier.weight(1f))
                    Text("${(settings.danmakuOpacity * 100).roundToInt()}%")
                }
                Slider(
                    value = settings.danmakuOpacity,
                    onValueChange = { value -> onSettings(settings.copy(danmakuOpacity = value)) },
                    valueRange = 0.2f..1f,
                )
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("滚动速度", modifier = Modifier.weight(1f))
                    Text(String.format("%.1fx", settings.danmakuSpeed))
                }
                Slider(
                    value = settings.danmakuSpeed,
                    onValueChange = { value -> onSettings(settings.copy(danmakuSpeed = value)) },
                    valueRange = 0.5f..2f,
                    steps = 5,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}

@Composable
private fun KeywordDialog(
    value: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关键词过滤") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("屏蔽关键词或正则") },
                supportingText = { Text("每行一个规则") },
                minLines = 4,
                maxLines = 8,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(draft)
                    onDismiss()
                },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun contentSummary(options: ContentOptions): String {
    val enabled = listOfNotNull(
        "视频".takeIf { options.video },
        "音频".takeIf { options.audio },
        "字幕".takeIf { options.subtitles },
        "弹幕".takeIf { options.danmaku },
        "封面".takeIf { options.cover },
        "元数据".takeIf { options.metadata },
        "章节".takeIf { options.chapterInfo },
    )
    return if (enabled.isEmpty()) "未启用" else enabled.take(3).joinToString("、") +
        if (enabled.size > 3) "等 ${enabled.size} 项" else ""
}

private fun keywordSummary(value: String): String {
    val count = value.lineSequence().map(String::trim).count { it.isNotBlank() }
    return if (count == 0) "未设置" else "$count 条"
}

@Composable
private fun ChoiceDialog(
    kind: String,
    settings: UserSettings,
    onDismiss: () -> Unit,
    onSettings: (UserSettings) -> Unit,
) {
    val entries: List<Pair<Any, String>> = when (kind) {
        "video" -> listOf(127, 126, 125, 120, 116, 112, 80, 74, 64, 32, 16)
            .map { it to videoQualityLabel(it) }
        "audio" -> listOf(30251, 30255, 30250, 30280, 30232, 30216)
            .map { it to audioQualityLabel(it) }
        "codec" -> listOf("avc", "hevc", "av1").map { it to it.uppercase() }
        else -> listOf(
            "sans-serif" to "系统无衬线",
            "sans-serif-medium" to "系统无衬线 Medium",
            "serif" to "系统衬线",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (kind) {
                    "video" -> "视频画质"
                    "audio" -> "音频质量"
                    "codec" -> "视频编码"
                    else -> "弹幕字体"
                },
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(entries) { entry ->
                    val selected = when (kind) {
                        "video" -> settings.videoQuality == entry.first
                        "audio" -> settings.audioQuality == entry.first
                        "codec" -> settings.videoCodec == entry.first
                        else -> settings.danmakuFont == entry.first
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSettings(
                                    when (kind) {
                                        "video" -> settings.copy(videoQuality = entry.first as Int)
                                        "audio" -> settings.copy(audioQuality = entry.first as Int)
                                        "codec" -> settings.copy(videoCodec = entry.first as String)
                                        else -> settings.copy(danmakuFont = entry.first as String)
                                    },
                                )
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.second, modifier = Modifier.weight(1f))
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("B", fontWeight = FontWeight.Bold, fontSize = 22.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val activeTaskStatuses = setOf(
    TaskStatus.QUEUED,
    TaskStatus.PREPARING,
    TaskStatus.DOWNLOADING,
    TaskStatus.MERGING,
)
