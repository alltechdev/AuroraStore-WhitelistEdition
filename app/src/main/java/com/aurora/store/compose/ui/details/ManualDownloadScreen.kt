/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.core.text.isDigitsOnly
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aurora.extensions.adaptiveNavigationIcon
import com.aurora.extensions.isWindowCompact
import com.aurora.extensions.toast
import com.aurora.gplayapi.data.models.App
import com.aurora.store.R
import com.aurora.store.compose.composables.InfoComposable
import com.aurora.store.compose.composables.TopAppBarComposable
import com.aurora.store.compose.preview.AppPreviewProvider
import com.aurora.store.viewmodel.details.AppDetailsViewModel
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

@Composable
fun ManualDownloadScreen(
    packageName: String,
    onNavigateUp: () -> Unit,
    viewModel: AppDetailsViewModel = hiltViewModel(key = packageName),
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo()
) {
    val context = LocalContext.current

    val app by viewModel.app.collectAsStateWithLifecycle()
    val topAppBarTitle = when {
        windowAdaptiveInfo.isWindowCompact -> app!!.displayName
        else -> stringResource(R.string.title_manual_download)
    }

    LaunchedEffect(key1 = Unit) {
        viewModel.purchaseStatus.collect { success ->
            if (success) {
                context.toast(R.string.toast_manual_available)
                onNavigateUp()
            } else {
                context.toast(R.string.toast_manual_unavailable)
            }
        }
    }

    ScreenContent(
        topAppBarTitle = topAppBarTitle,
        currentVersionCode = app!!.versionCode,
        onNavigateUp = onNavigateUp,
        onDownload = { versionCode ->
            val requestedApp = app!!.copy(
                versionCode = versionCode,
                dependencies = app!!.dependencies.copy(
                    dependentLibraries = app!!.dependencies.dependentLibraries.map { lib ->
                        lib.copy(versionCode = versionCode)
                    }
                )
            )
            viewModel.purchase(requestedApp)
        }
    )
}

@Composable
private fun ScreenContent(
    topAppBarTitle: String? = null,
    currentVersionCode: Long = 0L,
    onNavigateUp: () -> Unit = {},
    onDownload: (versionCode: Long) -> Unit = {},
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfo()
) {

    val coroutineScope = rememberCoroutineScope()
    val snackBarHostState = remember { SnackbarHostState() }
    val errorMessage = stringResource(R.string.manual_download_version_error)

    val focusRequester = remember { FocusRequester() }
    var versionCode by remember {
        val initText = currentVersionCode.toString()
        mutableStateOf(TextFieldValue(text = initText, selection = TextRange(initText.length)))
    }

    LaunchedEffect(focusRequester) {
        awaitFrame()
        focusRequester.requestFocus()
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBarComposable(
                title = topAppBarTitle,
                navigationIcon = windowAdaptiveInfo.adaptiveNavigationIcon,
                onNavigateUp = onNavigateUp
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackBarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(dimensionResource(R.dimen.padding_medium)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.margin_medium))
            ) {
                InfoComposable(
                    icon = R.drawable.ic_download_manager,
                    title = AnnotatedString(text = stringResource(R.string.manual_download_hint))
                )
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    value = versionCode,
                    onValueChange = {
                        if (it.text.isDigitsOnly()) {
                            versionCode = it
                        } else {
                            coroutineScope.launch { snackBarHostState.showSnackbar(errorMessage) }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.padding_medium))
            ) {
                FilledTonalButton(
                    modifier = Modifier.weight(1F),
                    onClick = onNavigateUp
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    modifier = Modifier.weight(1F),
                    onClick = { onDownload(versionCode.text.toLong()) }) {
                    Text(
                        text = stringResource(R.string.action_install),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun ManualDownloadScreenPreview(
    @PreviewParameter(AppPreviewProvider::class) app: App
) {
    ScreenContent(
        topAppBarTitle = app.displayName,
        currentVersionCode = app.versionCode
    )
}
