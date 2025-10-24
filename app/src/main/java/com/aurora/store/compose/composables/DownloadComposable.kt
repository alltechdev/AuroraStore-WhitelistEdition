/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.composables

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import coil3.compose.LocalAsyncImagePreviewHandler
import com.aurora.gplayapi.data.models.App
import com.aurora.store.R
import com.aurora.store.compose.composables.app.AnimatedAppIconComposable
import com.aurora.store.compose.preview.AppPreviewProvider
import com.aurora.store.compose.preview.coilPreviewProvider
import com.aurora.store.data.model.DownloadStatus
import com.aurora.store.data.room.download.Download
import com.aurora.store.util.CommonUtil.getETAString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Composable to display details of a download in a list
 * @param modifier The modifier to be applied to the composable
 * @param download [Download] to display
 * @param onClick Callback when this composable is clicked
 */
@Composable
fun DownloadComposable(
    modifier: Modifier = Modifier,
    download: Download,
    onClick: () -> Unit = {},
    onClear: () -> Unit = {},
    onCancel: () -> Unit = {},
    onExport: () -> Unit = {},
    onInstall: () -> Unit = {}
) {
    val context = LocalContext.current
    val progress = "${download.progress}%"
    val speed = "${Formatter.formatShortFileSize(LocalContext.current, download.speed)}/s"
    val eta = getETAString(LocalContext.current, download.timeRemaining)

    val coroutineScope = rememberCoroutineScope()
    var isVisible by remember { mutableStateOf(true) }
    var isExpanded by remember { mutableStateOf(false) }

    fun requestClear() {
        coroutineScope.launch {
            isVisible = false
            delay(300) // Let the animation play
            onClear()
        }
    }

    @Composable
    fun SetupDownload() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.weight(1F),) {
                AnimatedAppIconComposable(
                    modifier = Modifier.requiredSize(dimensionResource(R.dimen.icon_size_medium)),
                    iconUrl = download.iconURL,
                    inProgress = download.isRunning,
                    progress = download.progress.toFloat()
                )
                Column(
                    modifier = Modifier
                        .padding(horizontal = dimensionResource(R.dimen.margin_small)),
                ) {
                    Text(
                        text = download.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(download.downloadStatus.localized),
                        style = MaterialTheme.typography.bodySmall
                    )
                    AnimatedContent(targetState = download.downloadStatus) { status ->
                        Text(
                            style = MaterialTheme.typography.bodySmall,
                            text = if (status in DownloadStatus.running) {
                                "$progress • $speed • $eta"
                            } else {
                                DateUtils.getRelativeTimeSpanString(
                                    download.downloadedAt,
                                    Date().time,
                                    DateUtils.DAY_IN_MILLIS
                                ).toString()
                            }
                        )
                    }
                }
            }

            SplitButtonLayout(
                leadingButton = {
                    when {
                        download.isRunning -> {
                            SplitButtonDefaults.LeadingButton(onClick = onCancel) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_cancel),
                                    contentDescription = stringResource(R.string.action_cancel)
                                )
                            }
                        }

                        else -> {
                            SplitButtonDefaults.LeadingButton(onClick = { requestClear() }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_delete_forever),
                                    contentDescription = stringResource(R.string.action_clear)
                                )
                            }
                        }
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(
                        checked = isExpanded,
                        onCheckedChange = { isExpanded = it },
                        enabled = download.canInstall(context)
                    ) {
                        Icon(
                            painter = when {
                                isExpanded -> painterResource(R.drawable.ic_keyboard_arrow_up)
                                else -> painterResource(R.drawable.ic_keyboard_arrow_down)
                            },
                            contentDescription = stringResource(R.string.expand)
                        )
                    }
                }
            )
        }
    }

    @Composable
    fun SetupExpandedMenu() {
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically() + expandVertically() + fadeIn(),
            exit = slideOutVertically() + shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onInstall, enabled = download.canInstall(context)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_install),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_xsmall)))
                    Text(
                        text = stringResource(R.string.action_install),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(dimensionResource(R.dimen.margin_large))
                )

                TextButton(onClick = onExport, enabled = download.canInstall(context)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_file_copy),
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(dimensionResource(R.dimen.padding_xsmall)))
                    Text(
                        text = stringResource(R.string.action_export),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = modifier
                .clickable(onClick = onClick)
                .padding(
                    horizontal = dimensionResource(R.dimen.padding_medium),
                    vertical = dimensionResource(R.dimen.padding_small)
                )
        ) {
            SetupDownload()
            SetupExpandedMenu()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DownloadComposablePreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    CompositionLocalProvider(LocalAsyncImagePreviewHandler provides coilPreviewProvider) {
        DownloadComposable(download = Download.fromApp(app))
    }
}
