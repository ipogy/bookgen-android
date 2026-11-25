package com.bookgen.app.ui.screens.reader

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookgen.app.data.storage.ExportShareHelper
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.DocBlock
import com.bookgen.app.domain.model.ExportFormatType
import com.bookgen.app.domain.model.ReaderFontChoice
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.ui.components.EditorialCard
import com.bookgen.app.ui.components.NativeAstBlockView
import com.bookgen.app.ui.components.WavyProgressTrack
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenTheme
import com.bookgen.app.ui.theme.BookGenThemeExtras
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

private class CreateTypedDocumentContract : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.second
            putExtra(Intent.EXTRA_TITLE, input.first)
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

@Composable
fun ReaderRoute(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onShareExportedFile: ((String, ExportFormatType) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSavePath by remember { mutableStateOf<String?>(null) }

    val saveToDeviceLauncher = rememberLauncherForActivityResult(
        contract = CreateTypedDocumentContract(),
    ) { destUri ->
        val srcPath = pendingSavePath
        pendingSavePath = null
        if (destUri != null && srcPath != null) {
            scope.launch {
                ExportShareHelper.copyExportedFileToUri(context, srcPath, destUri)
                    .onSuccess { bytes ->
                        viewModel.notifyMessage("端末に保存しました (${bytes / 1024} KiB)")
                    }
                    .onFailure { err ->
                        viewModel.notifyMessage(err.message ?: "端末保存に失敗しました")
                    }
            }
        }
    }

    val effectiveShareHandler: (String, ExportFormatType) -> Unit = { path, fmt ->
        if (onShareExportedFile != null) {
            onShareExportedFile(path, fmt)
        } else {
            ExportShareHelper.shareExportedFile(context, path, fmt)
                .onFailure { err ->
                    viewModel.notifyMessage(err.message ?: "共有アプリの起動に失敗しました")
                }
        }
    }

    BookGenTheme(preference = state.appearance.theme) {
        ReaderScreen(
            state = state,
            onBack = onBack,
            onOpenWorkspace = {
                state.detail?.summary?.id?.let(onOpenWorkspace)
            },
            onSelectChapter = viewModel::selectChapter,
            onScrollFractionChanged = viewModel::persistScrollFraction,
            onToggleChrome = viewModel::toggleChromeVisibility,
            onThemeSelected = viewModel::setTheme,
            onFontSelected = viewModel::setFontChoice,
            onFontSizeChanged = viewModel::setFontSizeSp,
            onLineHeightChanged = viewModel::setLineHeight,
            onPaddingChanged = viewModel::setHorizontalPadding,
            onSelectExportFormat = viewModel::selectExportFormat,
            onToggleIncludeToc = viewModel::setIncludeToc,
            onToggleIncludeIncomplete = viewModel::setIncludeIncompleteNotice,
            onStartExport = viewModel::startExport,
            onCancelExport = viewModel::cancelExport,
            onShareExportedFile = effectiveShareHandler,
            onSaveExportedToDevice = { path, fmt ->
                pendingSavePath = path
                val fileName = File(path).name.ifBlank { "book.${fmt.extension}" }
                saveToDeviceLauncher.launch(fileName to fmt.mimeType)
            },
            onDismissSnackbar = viewModel::clearSnackbar,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    onBack: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onScrollFractionChanged: (Double) -> Unit,
    onToggleChrome: () -> Unit,
    onThemeSelected: (ThemePreference) -> Unit,
    onFontSelected: (ReaderFontChoice) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onPaddingChanged: (Int) -> Unit,
    onSelectExportFormat: (ExportFormatType) -> Unit,
    onToggleIncludeToc: (Boolean) -> Unit,
    onToggleIncludeIncomplete: (Boolean) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShareExportedFile: ((String, ExportFormatType) -> Unit)?,
    onSaveExportedToDevice: ((String, ExportFormatType) -> Unit)? = null,
    onDismissSnackbar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showTocSheet by rememberSaveable { mutableStateOf(false) }
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }

    val detail = state.detail
    val chapters = detail?.chapters.orEmpty()
    val currentChapter = chapters.getOrNull(state.currentChapterIndex)
    val listState = rememberLazyListState()
    val totalListItems = remember(state.parsedSections) {
        1 + state.parsedSections.sumOf { sec -> 1 + if (sec.blocks.isEmpty()) 1 else sec.blocks.size }
    }

    LaunchedEffect(state.currentChapterIndex, state.savedScrollFraction, totalListItems) {
        val maxIdx = (totalListItems - 1).coerceAtLeast(0)
        val targetIdx = (maxIdx * state.savedScrollFraction).toInt().coerceIn(0, maxIdx)
        listState.scrollToItem(targetIdx)
    }

    LaunchedEffect(listState, state.currentChapterIndex, totalListItems) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .debounce(600L)
            .collect { visibleIdx ->
                if (!state.isLoading && totalListItems > 1) {
                    val fraction = visibleIdx.toDouble() / (totalListItems - 1).toDouble()
                    onScrollFractionChanged(fraction.coerceIn(0.0, 1.0))
                }
            }
    }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onDismissSnackbar()
        }
    }

    val chapterProgress = if (chapters.isEmpty()) 0f else {
        (state.currentChapterIndex + 1).toFloat() / chapters.size.toFloat()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
                LinearProgressIndicator(
                    progress = { chapterProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                AnimatedVisibility(
                    visible = state.chromeVisible,
                    enter = slideInVertically { -it },
                    exit = slideOutVertically { -it },
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                text = currentChapter?.let {
                                    "第${it.chapter.chapterNumber}章: ${it.chapter.title}"
                                } ?: (detail?.summary?.title ?: "リーダー"),
                                maxLines = 1,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "戻る",
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { showTocSheet = true }) {
                                Icon(Icons.AutoMirrored.Outlined.List, contentDescription = "目次")
                            }
                            IconButton(onClick = { showAppearanceSheet = true }) {
                                Icon(Icons.Outlined.TextFormat, contentDescription = "表示設定")
                            }
                            IconButton(onClick = { showExportSheet = true }) {
                                Icon(Icons.Outlined.IosShare, contentDescription = "書き出し")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                        ),
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = state.chromeVisible && chapters.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.navigationBarsPadding()) {
                        HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                enabled = state.currentChapterIndex > 0,
                                onClick = { onSelectChapter(state.currentChapterIndex - 1) },
                            ) {
                                Text("← 前の章")
                            }
                            Text(
                                text = "${state.currentChapterIndex + 1} / ${chapters.size} 章",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                enabled = state.currentChapterIndex < chapters.lastIndex,
                                onClick = { onSelectChapter(state.currentChapterIndex + 1) },
                            ) {
                                Text("次の章 →")
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading && detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val chapterCharSum = state.parsedSections.sumOf { it.charCount }
        val estMinutes = max(1, (chapterCharSum / 500L).toInt())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleChrome,
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp),
                contentPadding = PaddingValues(
                    start = state.appearance.horizontalPaddingDp.dp,
                    end = state.appearance.horizontalPaddingDp.dp,
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    bottom = innerPadding.calculateBottomPadding() + 36.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                currentChapter?.let { ch ->
                    item(
                        key = "chapter_banner_${ch.chapter.id}",
                        contentType = "chapter_banner",
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "CHAPTER ${ch.chapter.chapterNumber} • ${chapterCharSum}字 (約${estMinutes}分)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = ch.chapter.title,
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            if (ch.chapter.focusArea.isNotBlank()) {
                                Text(
                                    text = ch.chapter.focusArea,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(top = 6.dp),
                                color = BookGenThemeExtras.colors.borderSubtle,
                            )
                        }
                    }
                }

                state.parsedSections.forEach { sec ->
                    val hasLeadingHeading = sec.blocks.firstOrNull() is DocBlock.Heading
                    if (!hasLeadingHeading) {
                        item(
                            key = "sec_title_${sec.sectionId}",
                            contentType = "section_title",
                        ) {
                            Text(
                                text = "${sec.chapterNumber}.${sec.sectionNumber} ${sec.title}",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }

                    if (!sec.isCompleted) {
                        item(
                            key = "sec_incomplete_${sec.sectionId}",
                            contentType = "section_incomplete",
                        ) {
                            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "この節はまだ執筆が完了していません。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onOpenWorkspace,
                                    shape = BookGenRadius.Control,
                                ) {
                                    Text("執筆スタジオを開く")
                                }
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = sec.blocks,
                            key = { idx, _ -> "${sec.sectionId}_$idx" },
                            contentType = { _, block -> block::class },
                        ) { _, block ->
                            NativeAstBlockView(
                                block = block,
                                appearance = state.appearance,
                                format = sec.format,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTocSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTocSheet = false },
            shape = BookGenRadius.Sheet,
        ) {
            TocBottomSheetContent(
                chapters = chapters,
                activeIndex = state.currentChapterIndex,
                onSelect = { idx ->
                    showTocSheet = false
                    onSelectChapter(idx)
                },
            )
        }
    }

    if (showAppearanceSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppearanceSheet = false },
            shape = BookGenRadius.Sheet,
        ) {
            AppearanceBottomSheetContent(
                state = state,
                onThemeSelected = onThemeSelected,
                onFontSelected = onFontSelected,
                onFontSizeChanged = onFontSizeChanged,
                onLineHeightChanged = onLineHeightChanged,
                onPaddingChanged = onPaddingChanged,
            )
        }
    }

    if (showExportSheet) {
        ModalBottomSheet(
            onDismissRequest = { showExportSheet = false },
            shape = BookGenRadius.Sheet,
        ) {
            ExportBottomSheetContent(
                exportState = state.exportState,
                onSelectFormat = onSelectExportFormat,
                onToggleToc = onToggleIncludeToc,
                onToggleIncomplete = onToggleIncludeIncomplete,
                onStartExport = onStartExport,
                onCancelExport = onCancelExport,
                onShareFile = onShareExportedFile,
                onSaveToDevice = onSaveExportedToDevice,
            )
        }
    }
}

@Composable
private fun TocBottomSheetContent(
    chapters: List<ChapterWithSections>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("目次", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
        chapters.forEachIndexed { idx, ch ->
            val selected = idx == activeIndex
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(idx) },
                shape = BookGenRadius.Control,
                color = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            ) {
                Text(
                    text = "第${ch.chapter.chapterNumber}章: ${ch.chapter.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AppearanceBottomSheetContent(
    state: ReaderUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onFontSelected: (ReaderFontChoice) -> Unit,
    onFontSizeChanged: (Float) -> Unit,
    onLineHeightChanged: (Float) -> Unit,
    onPaddingChanged: (Int) -> Unit,
) {
    val app = state.appearance
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("表示カスタマイズ", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                ThemePreference.LIGHT to "Light",
                ThemePreference.SEPIA to "Sepia",
                ThemePreference.DARK to "Dark",
            ).forEachIndexed { idx, (pref, label) ->
                SegmentedButton(
                    selected = app.theme == pref,
                    onClick = { onThemeSelected(pref) },
                    shape = SegmentedButtonDefaults.itemShape(idx, 3),
                ) {
                    Text(label)
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf(
                ReaderFontChoice.SERIF to "明朝体 (Serif)",
                ReaderFontChoice.SANS to "ゴシック体 (Sans)",
            ).forEachIndexed { idx, (font, label) ->
                SegmentedButton(
                    selected = app.fontChoice == font,
                    onClick = { onFontSelected(font) },
                    shape = SegmentedButtonDefaults.itemShape(idx, 2),
                ) {
                    Text(label)
                }
            }
        }

        Text("文字サイズ: ${app.fontSizeSp.toInt()}sp", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = app.fontSizeSp,
            onValueChange = onFontSizeChanged,
            valueRange = 14f..24f,
        )

        Text("行間: ${"%.2f".format(app.lineHeightMultiplier)}x", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = app.lineHeightMultiplier,
            onValueChange = onLineHeightChanged,
            valueRange = 1.4f..2.1f,
        )

        Text("左右余白: ${app.horizontalPaddingDp}dp", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = app.horizontalPaddingDp.toFloat(),
            onValueChange = { onPaddingChanged(it.toInt()) },
            valueRange = 12f..36f,
        )
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ExportBottomSheetContent(
    exportState: ExportUiState,
    onSelectFormat: (ExportFormatType) -> Unit,
    onToggleToc: (Boolean) -> Unit,
    onToggleIncomplete: (Boolean) -> Unit,
    onStartExport: () -> Unit,
    onCancelExport: () -> Unit,
    onShareFile: ((String, ExportFormatType) -> Unit)?,
    onSaveToDevice: ((String, ExportFormatType) -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("書籍書き出し (Rust 高速ビルダー)", style = MaterialTheme.typography.titleMedium)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormatType.entries.chunked(2).forEach { rowFormats ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowFormats.forEach { fmt ->
                        FilterChip(
                            selected = exportState.selectedFormat == fmt,
                            onClick = { onSelectFormat(fmt) },
                            label = { Text(fmt.label) },
                            modifier = Modifier.weight(1f),
                            shape = BookGenRadius.Control,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("目次を含める", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = exportState.includeToc, onCheckedChange = onToggleToc)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("未完成節の注記を挿入", style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = exportState.includeIncompleteNotice,
                onCheckedChange = onToggleIncomplete,
            )
        }

        if (exportState.isExporting) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${exportState.stageLabel} ${(exportState.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
                WavyProgressTrack(
                    progress = exportState.progress,
                    isActive = true,
                )
            }
        }

        exportState.errorMessage?.let { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        exportState.lastCompletedPath?.let { path ->
            Surface(
                shape = BookGenRadius.Control,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, BookGenThemeExtras.colors.borderSubtle),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "書き出し成功 (${exportState.lastBytesWritten / 1024} KiB)",
                        style = MaterialTheme.typography.labelLarge,
                        color = BookGenThemeExtras.colors.success,
                    )
                    Text(
                        text = path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (onShareFile != null) {
                            OutlinedButton(
                                onClick = { onShareFile(path, exportState.selectedFormat) },
                                modifier = Modifier.weight(1f),
                                shape = BookGenRadius.Control,
                            ) {
                                Text("他アプリで共有")
                            }
                        }
                        if (onSaveToDevice != null) {
                            OutlinedButton(
                                onClick = { onSaveToDevice(path, exportState.selectedFormat) },
                                modifier = Modifier.weight(1f),
                                shape = BookGenRadius.Control,
                            ) {
                                Text("端末に保存")
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (exportState.isExporting) {
                OutlinedButton(
                    onClick = onCancelExport,
                    modifier = Modifier.weight(1f),
                    shape = BookGenRadius.Control,
                ) {
                    Text("キャンセル")
                }
            } else {
                Button(
                    onClick = onStartExport,
                    modifier = Modifier.weight(1f),
                    shape = BookGenRadius.Control,
                ) {
                    Text("${exportState.selectedFormat.name} を生成")
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
