package com.bookgen.app.ui.screens.workspace

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.SectionRecord
import com.bookgen.app.domain.model.SectionRevision
import com.bookgen.app.ui.components.AiSettingsBottomSheet
import com.bookgen.app.ui.components.EditorialCard
import com.bookgen.app.ui.components.EditorialTooltipBox
import com.bookgen.app.ui.components.RegenerateBottomSheet
import com.bookgen.app.ui.components.SectionStatusChip
import com.bookgen.app.ui.components.WavyProgressTrack
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras
import com.bookgen.app.ui.theme.CodeMonoFontFamily
import com.bookgen.app.ui.theme.Slate100
import com.bookgen.app.ui.theme.Slate900

@Composable
fun WorkspaceRoute(
    viewModel: WorkspaceViewModel,
    onBack: () -> Unit,
    onOpenReader: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ ->
        viewModel.startOrResumeGeneration()
    }

    val startWithPermissionCheck = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startOrResumeGeneration()
        }
    }

    WorkspaceScreen(
        state = state,
        aiSettings = aiSettings,
        onBack = onBack,
        onOpenReader = onOpenReader,
        onToggleChapter = viewModel::toggleChapterExpanded,
        onToggleStreamDrawer = viewModel::toggleStreamDrawer,
        onMoveChapterUp = { idx -> viewModel.moveChapter(idx, -1) },
        onMoveChapterDown = { idx -> viewModel.moveChapter(idx, +1) },
        onMoveSectionUp = { chId, idx -> viewModel.moveSection(chId, idx, -1) },
        onMoveSectionDown = { chId, idx -> viewModel.moveSection(chId, idx, +1) },
        onAddChapter = viewModel::addChapter,
        onEditChapterMeta = viewModel::updateChapterMeta,
        onAddSection = viewModel::addSection,
        onDeleteSection = viewModel::deleteSection,
        onEditSectionClick = viewModel::openSectionEditor,
        onDismissSectionEditor = viewModel::closeSectionEditor,
        onSaveSection = viewModel::saveSectionContent,
        onRestoreRevision = viewModel::restoreRevision,
        onRegenerateSection = viewModel::openSectionRegenSheet,
        onRegenerateChapter = viewModel::openChapterRegenSheet,
        onDismissRegenSheet = viewModel::dismissRegenSheet,
        onConfirmRegen = viewModel::confirmPartialRegeneration,
        onStartOrResumePipeline = startWithPermissionCheck,
        onPausePipeline = viewModel::pauseGeneration,
        onOpenApiSettings = viewModel::openApiSettings,
        onDismissApiSettings = viewModel::dismissApiSettings,
        onSaveAiSettings = viewModel::saveAiSettings,
        onClearAiKeys = viewModel::clearAiKeys,
        onDismissSnackbar = viewModel::clearSnackbar,
        showBackButton = showBackButton,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: WorkspaceUiState,
    aiSettings: AiSettings,
    onBack: () -> Unit,
    onOpenReader: () -> Unit,
    onToggleChapter: (String) -> Unit,
    onToggleStreamDrawer: () -> Unit,
    onMoveChapterUp: (Int) -> Unit,
    onMoveChapterDown: (Int) -> Unit,
    onMoveSectionUp: (String, Int) -> Unit,
    onMoveSectionDown: (String, Int) -> Unit,
    onAddChapter: (String, String) -> Unit,
    onEditChapterMeta: (String, String, String) -> Unit,
    onAddSection: (String, String, String) -> Unit,
    onDeleteSection: (String) -> Unit,
    onEditSectionClick: (SectionRecord) -> Unit,
    onDismissSectionEditor: () -> Unit,
    onSaveSection: (String, String, String, MarkupFormat) -> Unit,
    onRestoreRevision: (String, Long) -> Unit,
    onRegenerateSection: (SectionRecord) -> Unit,
    onRegenerateChapter: (ChapterWithSections) -> Unit,
    onDismissRegenSheet: () -> Unit,
    onConfirmRegen: (RegenTarget, String) -> Unit,
    onStartOrResumePipeline: () -> Unit,
    onPausePipeline: () -> Unit,
    onOpenApiSettings: () -> Unit,
    onDismissApiSettings: () -> Unit,
    onSaveAiSettings: (AiSettings) -> Unit,
    onClearAiKeys: () -> Unit,
    onDismissSnackbar: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    var showAddChapterDialog by rememberSaveable { mutableStateOf(false) }
    var chapterForNewSection by rememberSaveable { mutableStateOf<String?>(null) }
    var editingChapterId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteCandidateSectionId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onDismissSnackbar()
        }
    }

    val summary = state.detail?.summary
    val chapters = state.detail?.chapters.orEmpty()
    val editingChapter = remember(editingChapterId, chapters) {
        editingChapterId?.let { id -> chapters.firstOrNull { it.chapter.id == id } }
    }
    val deleteCandidateSection = remember(deleteCandidateSectionId, chapters) {
        deleteCandidateSectionId?.let { id ->
            chapters.asSequence().flatMap { it.sections }.firstOrNull { it.id == id }
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || event.nativeKeyEvent.repeatCount > 0) {
                    return@onPreviewKeyEvent false
                }
                val modalOpen = showAddChapterDialog ||
                    chapterForNewSection != null ||
                    editingChapterId != null ||
                    deleteCandidateSectionId != null ||
                    state.editingSection != null ||
                    state.showApiSettingsSheet ||
                    state.regenTarget != null
                when {
                    event.key == Key.Escape -> {
                        when {
                            deleteCandidateSectionId != null -> deleteCandidateSectionId = null
                            editingChapterId != null -> editingChapterId = null
                            chapterForNewSection != null -> chapterForNewSection = null
                            showAddChapterDialog -> showAddChapterDialog = false
                            state.showApiSettingsSheet -> onDismissApiSettings()
                            state.regenTarget != null -> onDismissRegenSheet()
                            state.editingSection != null -> onDismissSectionEditor()
                            showBackButton -> onBack()
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
                    !modalOpen && event.isCtrlPressed && event.key == Key.N -> {
                        showAddChapterDialog = true
                        true
                    }
                    !modalOpen && event.isCtrlPressed && event.key == Key.R -> {
                        onOpenReader()
                        true
                    }
                    !modalOpen && event.isCtrlPressed && event.key == Key.Comma -> {
                        onOpenApiSettings()
                        true
                    }
                    !modalOpen && event.isCtrlPressed && (event.key == Key.Enter || event.key == Key.NumPadEnter) -> {
                        if (state.isGenerating) onPausePipeline() else onStartOrResumePipeline()
                        true
                    }
                    else -> false
                }
            },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = summary?.title ?: "アウトライン・執筆スタジオ",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (showBackButton) {
                        EditorialTooltipBox(label = "戻る", shortcutHint = "Esc") {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "戻る",
                                )
                            }
                        }
                    }
                },
                actions = {
                    EditorialTooltipBox(label = "AIモデル・キー設定", shortcutHint = "Ctrl+,") {
                        IconButton(onClick = onOpenApiSettings) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "AIモデル・キー設定",
                            )
                        }
                    }
                    EditorialTooltipBox(label = "章を追加", shortcutHint = "Ctrl+N") {
                        IconButton(onClick = { showAddChapterDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = "章を追加",
                            )
                        }
                    }
                    EditorialTooltipBox(label = "リーダーで読む", shortcutHint = "Ctrl+R") {
                        IconButton(onClick = onOpenReader) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                contentDescription = "リーダーで読む",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isLoading && state.detail == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "スタジオを読み込み中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 380.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 6.dp,
                    bottom = innerPadding.calculateBottomPadding() + 28.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(
                    key = "pipeline_status",
                    contentType = "pipeline_status",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    if (summary != null) {
                        PipelineStatusCard(
                            completed = summary.completedSections,
                            total = summary.totalSections,
                            incompleteCount = state.incompleteSections.size,
                            progressFraction = summary.progressFraction,
                            isGenerating = state.isGenerating,
                            onStartOrResume = onStartOrResumePipeline,
                            onPausePipeline = onPausePipeline,
                            onOpenReader = onOpenReader,
                        )
                    }
                }

                item(
                    key = "live_stream_drawer",
                    contentType = "live_stream_drawer",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    LiveStreamConsoleDrawer(
                        expanded = state.streamDrawerExpanded,
                        activeLabel = state.activeWritingLabel,
                        previewText = state.liveStreamPreview,
                        onToggle = onToggleStreamDrawer,
                    )
                }

                items(
                    count = chapters.size,
                    key = { index -> chapters[index].chapter.id },
                    contentType = { "chapter_card" },
                ) { index ->
                    val chWithSecs = chapters[index]
                    val isExpanded = chWithSecs.chapter.id in state.expandedChapterIds
                    ChapterOutlineCard(
                        chapterWithSections = chWithSecs,
                        isExpanded = isExpanded,
                        canMoveUp = index > 0,
                        canMoveDown = index < chapters.lastIndex,
                        onToggleExpand = { onToggleChapter(chWithSecs.chapter.id) },
                        onMoveUp = { onMoveChapterUp(index) },
                        onMoveDown = { onMoveChapterDown(index) },
                        onEditChapter = { editingChapterId = chWithSecs.chapter.id },
                        onAddSection = { chapterForNewSection = chWithSecs.chapter.id },
                        onMoveSectionUp = { secIdx -> onMoveSectionUp(chWithSecs.chapter.id, secIdx) },
                        onMoveSectionDown = { secIdx -> onMoveSectionDown(chWithSecs.chapter.id, secIdx) },
                        onEditSection = onEditSectionClick,
                        onRegenerateChapter = { onRegenerateChapter(chWithSecs) },
                        onRegenerateSection = onRegenerateSection,
                        onRequestDeleteSection = { sec -> deleteCandidateSectionId = sec.id },
                    )
                }
            }
        }
    }

    if (showAddChapterDialog) {
        TitleAndFocusDialog(
            dialogTitle = "新しい章を追加",
            initialTitle = "",
            initialFocus = "",
            titleLabel = "章タイトル",
            focusLabel = "重点テーマ・メモ",
            onDismiss = { showAddChapterDialog = false },
            onConfirm = { t, f ->
                showAddChapterDialog = false
                onAddChapter(t, f)
            },
        )
    }

    chapterForNewSection?.let { targetChId ->
        TitleAndFocusDialog(
            dialogTitle = "新しい節を追加",
            initialTitle = "",
            initialFocus = "",
            titleLabel = "節タイトル",
            focusLabel = "この節で解説する要点",
            onDismiss = { chapterForNewSection = null },
            onConfirm = { t, f ->
                chapterForNewSection = null
                onAddSection(targetChId, t, f)
            },
        )
    }

    editingChapter?.let { ch ->
        TitleAndFocusDialog(
            dialogTitle = "第${ch.chapter.chapterNumber}章を編集",
            initialTitle = ch.chapter.title,
            initialFocus = ch.chapter.focusArea,
            titleLabel = "章タイトル",
            focusLabel = "章の重点方針",
            onDismiss = { editingChapterId = null },
            onConfirm = { t, f ->
                editingChapterId = null
                onEditChapterMeta(ch.chapter.id, t, f)
            },
        )
    }

    state.editingSection?.let { section ->
        SectionEditorDialog(
            section = section,
            revisions = state.editingRevisions,
            onDismiss = onDismissSectionEditor,
            onSave = { newTitle, newContent, fmt ->
                onSaveSection(section.id, newTitle, newContent, fmt)
            },
            onRestoreRevision = { revId -> onRestoreRevision(section.id, revId) },
        )
    }

    if (state.showApiSettingsSheet) {
        AiSettingsBottomSheet(
            currentSettings = aiSettings,
            onSaveSettings = onSaveAiSettings,
            onClearKeys = onClearAiKeys,
            onDismiss = onDismissApiSettings,
        )
    }

    state.regenTarget?.let { target ->
        RegenerateBottomSheet(
            targetTitleLabel = target.titleLabel,
            sectionCount = target.sectionIds.size,
            onDismiss = onDismissRegenSheet,
            onConfirmRegenerate = { instruction ->
                onConfirmRegen(target, instruction)
            },
        )
    }

    deleteCandidateSection?.let { victim ->
        AlertDialog(
            onDismissRequest = { deleteCandidateSectionId = null },
            title = { Text("節を削除しますか？") },
            text = {
                Text("「${victim.chapterNumber}.${victim.sectionNumber} ${victim.title}」と執筆済み本文が削除されます。")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteSection(victim.id)
                        deleteCandidateSectionId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidateSectionId = null }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PipelineStatusCard(
    completed: Int,
    total: Int,
    incompleteCount: Int,
    progressFraction: Float,
    isGenerating: Boolean,
    onStartOrResume: () -> Unit,
    onPausePipeline: () -> Unit,
    onOpenReader: () -> Unit,
) {
    val pct = (progressFraction * 100f).toInt()
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$completed / $total 節 完了 ($pct%)",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = if (isGenerating) "執筆実行中" else if (incompleteCount > 0) "残り${incompleteCount}節" else "全節完了",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        Spacer(Modifier.height(10.dp))
        WavyProgressTrack(
            progress = progressFraction,
            isActive = isGenerating,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "WorkManager バックグラウンド実行対応・画面を閉じても進捗と各節の状態を保持",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isGenerating) {
                OutlinedButton(
                    onClick = onPausePipeline,
                    shape = BookGenRadius.Control,
                ) {
                    Icon(Icons.Outlined.Pause, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("一時停止")
                }
            } else {
                Button(
                    onClick = onStartOrResume,
                    shape = BookGenRadius.Control,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            incompleteCount > 0 -> "残りの${incompleteCount}節を執筆"
                            else -> "未完了節をチェック"
                        },
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenReader,
                shape = BookGenRadius.Control,
            ) {
                Text("読む")
            }
        }
    }
}

@Composable
private fun LiveStreamConsoleDrawer(
    expanded: Boolean,
    activeLabel: String,
    previewText: String,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                stateDescription = if (expanded) "展開中" else "折りたたみ"
            }
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "ストリームコンソールを折りたたむ" else "ストリームコンソールを展開する",
                onClick = onToggle,
            ),
        shape = BookGenRadius.Card,
        color = Slate900,
        contentColor = Slate100,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LIVE STREAM • $activeLabel",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = CodeMonoFontFamily),
                    color = Slate100,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Slate100,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Text(
                    text = previewText.ifBlank { "待機中です。BYOK APIキーを設定してバックグラウンド執筆を開始すると、生成中の本文ストリームがここに表示されます。" },
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = CodeMonoFontFamily),
                    color = Slate100.copy(alpha = 0.86f),
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChapterOutlineCard(
    chapterWithSections: ChapterWithSections,
    isExpanded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleExpand: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEditChapter: () -> Unit,
    onAddSection: () -> Unit,
    onMoveSectionUp: (Int) -> Unit,
    onMoveSectionDown: (Int) -> Unit,
    onEditSection: (SectionRecord) -> Unit,
    onRegenerateChapter: () -> Unit,
    onRegenerateSection: (SectionRecord) -> Unit,
    onRequestDeleteSection: (SectionRecord) -> Unit,
) {
    val ch = chapterWithSections.chapter
    var showChapterMenu by rememberSaveable(ch.id) { mutableStateOf(false) }

    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {
                        stateDescription = if (isExpanded) "展開中" else "折りたたみ"
                    }
                    .clickable(
                        role = Role.Button,
                        onClickLabel = if (isExpanded) "第${ch.chapterNumber}章を折りたたむ" else "第${ch.chapterNumber}章を展開する",
                        onClick = onToggleExpand,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "第${ch.chapterNumber}章: ${ch.title}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ch.focusArea.isNotBlank()) {
                        Text(
                            text = ch.focusArea,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            EditorialTooltipBox(label = "この章を再生成・トーン調整") {
                IconButton(onClick = onRegenerateChapter) {
                    Icon(Icons.Outlined.AutoFixHigh, contentDescription = "この章を再生成・トーン調整")
                }
            }
            Box {
                EditorialTooltipBox(label = "第${ch.chapterNumber}章の操作メニュー") {
                    IconButton(onClick = { showChapterMenu = true }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "第${ch.chapterNumber}章の操作メニュー",
                        )
                    }
                }
                DropdownMenu(
                    expanded = showChapterMenu,
                    onDismissRequest = { showChapterMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("章タイトル・方針を編集") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                        onClick = {
                            showChapterMenu = false
                            onEditChapter()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("新しい節を追加") },
                        leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        onClick = {
                            showChapterMenu = false
                            onAddSection()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("上へ移動") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                        enabled = canMoveUp,
                        onClick = {
                            showChapterMenu = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("下へ移動") },
                        leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                        enabled = canMoveDown,
                        onClick = {
                            showChapterMenu = false
                            onMoveDown()
                        },
                    )
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
                chapterWithSections.sections.forEachIndexed { idx, section ->
                    SectionOutlineRow(
                        section = section,
                        canMoveUp = idx > 0,
                        canMoveDown = idx < chapterWithSections.sections.lastIndex,
                        onMoveUp = { onMoveSectionUp(idx) },
                        onMoveDown = { onMoveSectionDown(idx) },
                        onEdit = { onEditSection(section) },
                        onRegenerate = { onRegenerateSection(section) },
                        onDeleteRequest = { onRequestDeleteSection(section) },
                    )
                    HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle.copy(alpha = 0.6f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onAddSection) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("節を追加")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionOutlineRow(
    section: SectionRecord,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    var showSectionMenu by rememberSaveable(section.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "「${section.title}」を編集",
                onClick = onEdit,
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "${section.chapterNumber}.${section.sectionNumber} ${section.title}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            SectionStatusChip(
                status = section.status,
                charCount = section.charCount,
            )
        }
        Box {
            EditorialTooltipBox(label = "操作メニュー") {
                IconButton(onClick = { showSectionMenu = true }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "「${section.title}」の操作メニュー",
                    )
                }
            }
            DropdownMenu(
                expanded = showSectionMenu,
                onDismissRequest = { showSectionMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text("本文・タイトルを編集") },
                    leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                    onClick = {
                        showSectionMenu = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text("AI で再生成・調整") },
                    leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, contentDescription = null) },
                    onClick = {
                        showSectionMenu = false
                        onRegenerate()
                    },
                )
                DropdownMenuItem(
                    text = { Text("上へ移動") },
                    leadingIcon = { Icon(Icons.Outlined.ArrowUpward, contentDescription = null) },
                    enabled = canMoveUp,
                    onClick = {
                        showSectionMenu = false
                        onMoveUp()
                    },
                )
                DropdownMenuItem(
                    text = { Text("下へ移動") },
                    leadingIcon = { Icon(Icons.Outlined.ArrowDownward, contentDescription = null) },
                    enabled = canMoveDown,
                    onClick = {
                        showSectionMenu = false
                        onMoveDown()
                    },
                )
                HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "この節を削除",
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showSectionMenu = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

@Composable
private fun TitleAndFocusDialog(
    dialogTitle: String,
    initialTitle: String,
    initialFocus: String,
    titleLabel: String,
    focusLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var focus by rememberSaveable { mutableStateOf(initialFocus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(titleLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = focus,
                    onValueChange = { focus = it },
                    label = { Text(focusLabel) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = { onConfirm(title, focus) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}

@Composable
private fun SectionEditorDialog(
    section: SectionRecord,
    revisions: List<SectionRevision>,
    onDismiss: () -> Unit,
    onSave: (String, String, MarkupFormat) -> Unit,
    onRestoreRevision: (Long) -> Unit,
) {
    var title by rememberSaveable(section.id) { mutableStateOf(section.title) }
    var content by rememberSaveable(section.id, section.revisionCount) { mutableStateOf(section.content) }
    var showRevisions by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val hasUnsavedChanges = title != section.title || content != section.content

    val handleRequestClose = {
        if (hasUnsavedChanges) {
            confirmDiscard = true
        } else {
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = handleRequestClose,
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown &&
                event.nativeKeyEvent.repeatCount == 0 &&
                event.isCtrlPressed &&
                event.key == Key.S &&
                title.isNotBlank()
            ) {
                onSave(title, content, section.format)
                true
            } else {
                false
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${section.chapterNumber}.${section.sectionNumber} 編集",
                    modifier = Modifier.weight(1f),
                )
                if (revisions.isNotEmpty()) {
                    TextButton(onClick = { showRevisions = !showRevisions }) {
                        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("履歴(${revisions.size})")
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showRevisions && revisions.isNotEmpty()) {
                    revisions.take(4).forEach { rev ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "#${rev.revisionNumber} (${rev.source})",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            TextButton(onClick = { onRestoreRevision(rev.id) }) {
                                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("復元")
                            }
                        }
                    }
                    HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("節タイトル *") },
                    isError = title.isBlank(),
                    supportingText = if (title.isBlank()) {
                        { Text("節タイトルは空にできません") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("本文 (${section.format.name})") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, content, section.format) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = handleRequestClose) { Text("閉じる") }
        },
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("未保存の変更を破棄しますか？") },
            text = { Text("保存せずに閉じると編集中のタイトル・本文の変更は失われます。") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDiscard = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("破棄する")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text("編集を続ける")
                }
            },
        )
    }
}
