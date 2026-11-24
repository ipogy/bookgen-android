package com.bookgen.app.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.BookSummary
import com.bookgen.app.domain.model.SearchHit
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.ui.components.BookStatusBadge
import com.bookgen.app.ui.components.EditorialCard
import com.bookgen.app.ui.components.GenreBadge
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras

@Composable
fun LibraryRoute(
    viewModel: LibraryViewModel,
    onNewBookClick: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenReader: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val themePref by viewModel.themePreference.collectAsStateWithLifecycle()

    LibraryScreen(
        state = state,
        currentTheme = themePref,
        onNewBookClick = onNewBookClick,
        onOpenWorkspace = onOpenWorkspace,
        onOpenReader = onOpenReader,
        onFilterSelected = viewModel::onFilterSelected,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onDeleteBook = viewModel::deleteBook,
        onImportDirectory = viewModel::importLegacyDirectory,
        onThemeSelected = viewModel::updateThemePreference,
        onDismissMessages = viewModel::clearBannerMessages,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    currentTheme: ThemePreference,
    onNewBookClick: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenReader: (String, Int) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onImportDirectory: (String) -> Unit,
    onThemeSelected: (ThemePreference) -> Unit,
    onDismissMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<BookSummary?>(null) }

    LaunchedEffect(state.errorMessage, state.importStatusMessage) {
        val msg = state.errorMessage ?: state.importStatusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onDismissMessages()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Co-Author Studio",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    IconButton(onClick = { showImportDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.FileDownload,
                            contentDescription = "旧データを取り込む",
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "設定",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewBookClick,
                shape = BookGenRadius.Fab,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("新しい本を書く") },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 92.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "search_strip") {
                SearchAndFilterStrip(
                    query = state.searchQuery,
                    activeFilter = state.activeFilter,
                    isSearching = state.isSearching,
                    onQueryChange = onSearchQueryChanged,
                    onFilterSelected = onFilterSelected,
                )
            }

            if (state.searchQuery.isNotBlank()) {
                item(key = "search_heading") {
                    Text(
                        text = "全文検索結果 (${state.searchHits.size}件)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.searchHits.isEmpty() && !state.isSearching) {
                    item(key = "search_empty") {
                        Text(
                            text = "一致する本文・見出しが見つかりませんでした。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                } else {
                    items(
                        items = state.searchHits,
                        key = { "hit_${it.sectionId}" },
                    ) { hit ->
                        SearchHitRow(
                            hit = hit,
                            onClick = { onOpenReader(hit.bookId, (hit.chapterNumber - 1).coerceAtLeast(0)) },
                        )
                    }
                }
            } else {
                if (state.heroBook != null) {
                    item(key = "hero_card") {
                        HeroResumeCard(
                            book = state.heroBook,
                            onResumeWorkspace = { onOpenWorkspace(state.heroBook.id) },
                            onReadClick = { onOpenReader(state.heroBook.id, 0) },
                        )
                    }
                }

                item(key = "library_heading") {
                    Text(
                        text = "ライブラリ (${state.filteredBooks.size}冊)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.isLoading) {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.filteredBooks.isEmpty()) {
                    item(key = "empty_library") {
                        EmptyLibraryNotice(
                            onCreateClick = onNewBookClick,
                            onImportClick = { showImportDialog = true },
                        )
                    }
                } else {
                    items(
                        items = state.filteredBooks,
                        key = { it.id },
                    ) { book ->
                        BookLibraryRow(
                            book = book,
                            onOpenWorkspace = { onOpenWorkspace(book.id) },
                            onOpenReader = { onOpenReader(book.id, 0) },
                            onRequestDelete = { deleteCandidate = book },
                        )
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        LegacyImportDialog(
            isImporting = state.isImporting,
            onDismiss = { showImportDialog = false },
            onConfirmPath = { path ->
                showImportDialog = false
                onImportDirectory(path)
            },
        )
    }

    if (showSettingsDialog) {
        LibrarySettingsDialog(
            currentTheme = currentTheme,
            onThemeSelected = onThemeSelected,
            onDismiss = { showSettingsDialog = false },
        )
    }

    deleteCandidate?.let { victim ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("書籍を削除しますか？") },
            text = { Text("「${victim.title}」の全章・執筆履歴が端末から完全に削除されます。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBook(victim.id)
                        deleteCandidate = null
                    },
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

@Composable
private fun SearchAndFilterStrip(
    query: String,
    activeFilter: LibraryFilter,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = BookGenRadius.Chip,
            placeholder = { Text("書籍タイトル・章・本文をFTS5高速検索…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = "検索をクリア")
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = BookGenThemeExtras.colors.borderSubtle,
            ),
        )

        AnimatedVisibility(visible = query.isBlank()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryFilter.entries) { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { onFilterSelected(filter) },
                        label = { Text(filter.label) },
                        shape = BookGenRadius.Chip,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroResumeCard(
    book: BookSummary,
    onResumeWorkspace: () -> Unit,
    onReadClick: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GenreBadge(label = book.genreName.ifBlank { book.genreKey })
            BookStatusBadge(
                status = book.status,
                emptyOrFailedCount = book.emptyOrFailedSections,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${book.completedSections} / ${book.totalSections} 節完了 • ${book.charCount}字",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { book.progressFraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = if (book.status == BookStatus.COMPLETED) onReadClick else onResumeWorkspace,
                shape = BookGenRadius.Control,
            ) {
                Text(if (book.status == BookStatus.COMPLETED) "続きから読む" else "執筆を再開")
            }
            OutlinedButton(
                onClick = if (book.status == BookStatus.COMPLETED) onResumeWorkspace else onReadClick,
                shape = BookGenRadius.Control,
            ) {
                Text(if (book.status == BookStatus.COMPLETED) "構成編集" else "プレビュー")
            }
        }
    }
}

@Composable
private fun BookLibraryRow(
    book: BookSummary,
    onOpenWorkspace: () -> Unit,
    onOpenReader: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenWorkspace)
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                GenreBadge(label = book.genreName.ifBlank { book.genreKey })
                BookStatusBadge(
                    status = book.status,
                    emptyOrFailedCount = book.emptyOrFailedSections,
                )
            }
            Row {
                IconButton(onClick = onOpenReader) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = "リーダーを開く",
                    )
                }
                IconButton(onClick = onRequestDelete) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "削除",
                    )
                }
            }
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${book.totalChapters}章 / ${book.completedSections}/${book.totalSections}節 • ${book.charCount}字",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { book.progressFraction },
            modifier = Modifier.fillMaxWidth(),
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
    }
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    onClick: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(
            text = "${hit.bookTitle} • 第${hit.chapterNumber}章.${hit.sectionNumber}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = hit.sectionTitle,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = highlightSnippet(hit.snippet),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun highlightSnippet(raw: String) = buildAnnotatedString {
    val highlightColor = MaterialTheme.colorScheme.primary
    var idx = 0
    while (idx < raw.length) {
        val open = raw.indexOf('«', startIndex = idx)
        if (open < 0) {
            append(raw.substring(idx))
            break
        }
        val close = raw.indexOf('»', startIndex = open + 1)
        if (close < 0) {
            append(raw.substring(idx))
            break
        }
        append(raw.substring(idx, open))
        withStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold)) {
            append(raw.substring(open + 1, close))
        }
        idx = close + 1
    }
}

@Composable
private fun EmptyLibraryNotice(
    onCreateClick: () -> Unit,
    onImportClick: () -> Unit,
) {
    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "まだ書籍がありません",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "テーマから1冊構成するか、Web版 bookgen の books ディレクトリを取り込んで続きから執筆できます。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onCreateClick, shape = BookGenRadius.Control) {
                Text("企画ウィザードへ")
            }
            OutlinedButton(onClick = onImportClick, shape = BookGenRadius.Control) {
                Text("旧データ取込")
            }
        }
    }
}

@Composable
private fun LegacyImportDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirmPath: (String) -> Unit,
) {
    var dirPath by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Web版 bookgen データ取込") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "app_data.db や book.json / chapter_*.adoc を含む書籍ディレクトリのパスを指定してください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = dirPath,
                    onValueChange = { dirPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("/sdcard/Download/bookgen/books") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = dirPath.isNotBlank() && !isImporting,
                onClick = { onConfirmPath(dirPath) },
            ) {
                Text(if (isImporting) "取込中…" else "取り込む")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("閉じる") }
        },
    )
}

@Composable
private fun LibrarySettingsDialog(
    currentTheme: ThemePreference,
    onThemeSelected: (ThemePreference) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("表示・スタジオ設定") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "アプリテーマ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                ThemePreference.entries.forEach { pref ->
                    val label = when (pref) {
                        ThemePreference.SYSTEM -> "システム設定に従う"
                        ThemePreference.LIGHT -> "Light (Alabaster Paper)"
                        ThemePreference.DARK -> "Dark (Studio Slate)"
                        ThemePreference.SEPIA -> "Sepia (温かみのある紙面)"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(pref) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = currentTheme == pref,
                            onClick = { onThemeSelected(pref) },
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(text = label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完了") }
        },
    )
}
