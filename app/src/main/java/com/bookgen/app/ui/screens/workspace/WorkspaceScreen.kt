package com.bookgen.app.ui.screens.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookgen.app.domain.model.ChapterWithSections
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.domain.model.SectionRecord
import com.bookgen.app.domain.model.SectionRevision
import com.bookgen.app.ui.components.EditorialCard
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
    onStartGeneration: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WorkspaceScreen(
        state = state,
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
        onRegenerateSection = { sec ->
            viewModel.markSectionPendingForRegen(sec.id, "再生成キューに追加")
        },
        onStartOrResumePipeline = onStartGeneration,
        onDismissSnackbar = viewModel::clearSnackbar,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: WorkspaceUiState,
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
    onStartOrResumePipeline: () -> Unit,
    onDismissSnackbar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddChapterDialog by rememberSaveable { mutableStateOf(false) }
    var chapterForNewSection by remember { mutableStateOf<String?>(null) }
    var editingChapter by remember { mutableStateOf<ChapterWithSections?>(null) }

    LaunchedEffect(state.snackbarMessage) {
        val msg = state.snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onDismissSnackbar()
        }
    }

    val summary = state.detail?.summary
    val chapters = state.detail?.chapters.orEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = summary?.title ?: "アウトライン・執筆スタジオ",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    IconButton(onClick = { showAddChapterDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = "章を追加",
                        )
                    }
                    IconButton(onClick = onOpenReader) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                            contentDescription = "リーダーで読む",
                        )
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

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 6.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "pipeline_status") {
                if (summary != null) {
                    PipelineStatusCard(
                        completed = summary.completedSections,
                        total = summary.totalSections,
                        incompleteCount = state.incompleteSections.size,
                        progressFraction = summary.progressFraction,
                        isGenerating = state.isGenerating,
                        onStartOrResume = onStartOrResumePipeline,
                        onOpenReader = onOpenReader,
                    )
                }
            }

            item(key = "live_stream_drawer") {
                LiveStreamConsoleDrawer(
                    expanded = state.streamDrawerExpanded,
                    activeLabel = state.activeWritingLabel,
                    previewText = state.liveStreamPreview,
                    onToggle = onToggleStreamDrawer,
                )
            }

            itemsIndexed(
                items = chapters,
                key = { _, item -> item.chapter.id },
            ) { index, chWithSecs ->
                val isExpanded = chWithSecs.chapter.id in state.expandedChapterIds
                ChapterOutlineCard(
                    chapterWithSections = chWithSecs,
                    isExpanded = isExpanded,
                    canMoveUp = index > 0,
                    canMoveDown = index < chapters.lastIndex,
                    onToggleExpand = { onToggleChapter(chWithSecs.chapter.id) },
                    onMoveUp = { onMoveChapterUp(index) },
                    onMoveDown = { onMoveChapterDown(index) },
                    onEditChapter = { editingChapter = chWithSecs },
                    onAddSection = { chapterForNewSection = chWithSecs.chapter.id },
                    onMoveSectionUp = { secIdx -> onMoveSectionUp(chWithSecs.chapter.id, secIdx) },
                    onMoveSectionDown = { secIdx -> onMoveSectionDown(chWithSecs.chapter.id, secIdx) },
                    onEditSection = onEditSectionClick,
                    onRegenerateSection = onRegenerateSection,
                    onDeleteSection = onDeleteSection,
                )
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
            onDismiss = { editingChapter = null },
            onConfirm = { t, f ->
                editingChapter = null
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
}

@Composable
private fun PipelineStatusCard(
    completed: Int,
    total: Int,
    incompleteCount: Int,
    progressFraction: Float,
    isGenerating: Boolean,
    onStartOrResume: () -> Unit,
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
                text = if (incompleteCount > 0) "残り${incompleteCount}節" else "全節完了",
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
            text = "バックグラウンド実行対応・画面を閉じても進捗と各節の状態を保持",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStartOrResume,
                shape = BookGenRadius.Control,
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        isGenerating -> "執筆状況を確認"
                        incompleteCount > 0 -> "残りの${incompleteCount}節を執筆"
                        else -> "AIで全体を再調整"
                    },
                )
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
            .clickable(onClick = onToggle),
        shape = BookGenRadius.Card,
        color = Slate900,
        contentColor = Slate100,
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "LIVE STREAM • $activeLabel",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = CodeMonoFontFamily),
                    color = Slate100,
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "折りたたむ" else "展開する",
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
    onRegenerateSection: (SectionRecord) -> Unit,
    onDeleteSection: (String) -> Unit,
) {
    val ch = chapterWithSections.chapter
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "第${ch.chapterNumber}章: ${ch.title}",
                    style = MaterialTheme.typography.titleSmall,
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
            IconButton(enabled = canMoveUp, onClick = onMoveUp) {
                Icon(Icons.Outlined.ArrowUpward, contentDescription = "章を上へ")
            }
            IconButton(enabled = canMoveDown, onClick = onMoveDown) {
                Icon(Icons.Outlined.ArrowDownward, contentDescription = "章を下へ")
            }
            IconButton(onClick = onEditChapter) {
                Icon(Icons.Outlined.Edit, contentDescription = "章を編集")
            }
            Icon(
                imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
            )
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
                        onDelete = { onDeleteSection(section.id) },
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
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
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
        IconButton(enabled = canMoveUp, onClick = onMoveUp) {
            Icon(Icons.Outlined.ArrowUpward, contentDescription = "節を上へ", modifier = Modifier.size(18.dp))
        }
        IconButton(enabled = canMoveDown, onClick = onMoveDown) {
            Icon(Icons.Outlined.ArrowDownward, contentDescription = "節を下へ", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRegenerate) {
            Icon(Icons.Outlined.AutoFixHigh, contentDescription = "節を再生成待ち", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "節を削除", modifier = Modifier.size(18.dp))
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${section.chapterNumber}.${section.sectionNumber} 編集")
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    label = { Text("節タイトル") },
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
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}
