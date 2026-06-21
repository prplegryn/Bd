package com.prplegryn.bd.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prplegryn.bd.data.SourceType
import com.prplegryn.bd.data.TaskStatus
import java.util.Locale

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        action?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    value: String = "",
    description: String = "",
    modifier: Modifier = Modifier,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value.isNotBlank()) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        trailing?.invoke()
        if (showChevron && trailing == null) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SoftDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
    )
}

fun taskStatusLabel(status: TaskStatus): String = when (status) {
    TaskStatus.QUEUED -> "等待中"
    TaskStatus.PREPARING -> "正在解析媒体"
    TaskStatus.DOWNLOADING -> "下载中"
    TaskStatus.MERGING -> "正在封装"
    TaskStatus.PAUSED -> "已暂停"
    TaskStatus.COMPLETED -> "已完成"
    TaskStatus.FAILED -> "失败"
    TaskStatus.CANCELLED -> "已取消"
}

fun sourceTypeLabel(type: SourceType): String = when (type) {
    SourceType.VIDEO -> "投稿视频"
    SourceType.BANGUMI -> "番剧"
    SourceType.COURSE -> "课程"
    SourceType.FAVOURITE -> "收藏夹"
    SourceType.WATCH_LATER -> "稍后再看"
    SourceType.SPACE -> "个人空间"
    SourceType.COLLECTION -> "合集"
    SourceType.SERIES -> "视频列表"
}

fun videoQualityLabel(value: Int): String = when (value) {
    127 -> "8K 超高清"
    126 -> "杜比视界"
    125 -> "4K HDR"
    120 -> "4K 超高清"
    116 -> "1080P 60帧"
    112 -> "1080P 高码率"
    100 -> "智能修复"
    80 -> "1080P 高清"
    74 -> "720P 60帧"
    64 -> "720P"
    32 -> "480P"
    16 -> "360P"
    else -> "自动"
}

fun audioQualityLabel(value: Int): String = when (value) {
    30251 -> "Hi-Res"
    30255 -> "杜比音效"
    30250 -> "杜比全景声"
    30280 -> "320kbps"
    30232 -> "128kbps"
    30216 -> "64kbps"
    else -> "自动"
}

fun formatBytes(value: Long): String {
    if (value <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) {
        amount /= 1024
        unit++
    }
    return if (unit == 0) {
        "${amount.toLong()} ${units[unit]}"
    } else {
        String.format(Locale.US, "%.1f %s", amount, units[unit])
    }
}
