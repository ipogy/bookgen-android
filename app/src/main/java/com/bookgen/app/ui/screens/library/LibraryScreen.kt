package com.bookgen.app.ui.screens.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.BookStatus
import com.bookgen.app.domain.model.BookSummary
import com.bookgen.app.domain.model.SearchHit
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.ui.components.AiSettingsBottomSheet
import com.bookgen.app.ui.components.BookStatusBadge
import com.bookgen.app.ui.components.EditorialCard
import com.bookgen.app.ui.components.EditorialTooltipBox
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
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LibraryScreen(
        state = state,
        currentTheme = themePref,
        aiSettings = aiSettings,
        onNewBookClick = onNewBookClick,
        onOpenWorkspace = onOpenWorkspace,
        onOpenReader = onOpenReader,
        onFilterSelected = viewModel::onFilterSelected,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onDeleteBook = viewModel::deleteBook,
        onImportDirectory = viewModel::importLegacyDirectory,
        onImportDocumentUri = { uri -> viewModel.importLegacyFromDocumentUri(context, uri) },
        onImportTreeUri = { uri -> viewModel.importLegacyFromTreeUri(context, uri) },
        onThemeSelected = viewModel::updateThemePreference,
        onSaveAiSettings = viewModel::saveAiSettings,
        onClearAiKeys = viewModel::clearAiKeys,
        onDismissMessages = viewModel::clearBannerMessages,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    currentTheme: ThemePreference,
    aiSettings: AiSettings,
    onNewBookClick: () -> Unit,
    onOpenWorkspace: (String) -> Unit,
    onOpenReader: (String, Int) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onDeleteBook: (String) -> Unit,
    onImportDirectory: (String) -> Unit,
    onImportDocumentUri: (Uri) -> Unit = {},
    onImportTreeUri: (Uri) -> Unit = {},
    onThemeSelected: (ThemePreference) -> Unit,
    onSaveAiSettings: (AiSettings) -> Unit,
    onClearAiKeys: () -> Unit,
    onDismissMessages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var deleteCandidateId by rememberSaveable { mutableStateOf<String?>(null) }
    val deleteCandidate = remember(deleteCandidateId, state.books) {
        deleteCandidateId?.let { id -> state.books.firstOrNull { it.id == id } }
    }

    LaunchedEffect(state.errorMessage, state.importStatusMessage) {
        val msg = state.errorMessage ?: state.importStatusMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onDismissMessages()
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
                when {
                    event.isCtrlPressed && event.key == Key.F -> {
                        searchFocusRequester.requestFocus()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.N -> {
                        if (!showImportDialog && !showSettingsDialog && deleteCandidateId == null) {
                            onNewBookClick()
                            true
                        } else {
                            false
                        }
                    }
                    event.isCtrlPressed && event.key == Key.I -> {
                        showImportDialog = true
                        true
                    }
                    event.isCtrlPressed && event.key == Key.Comma -> {
                        showSettingsDialog = true
                        true
                    }
                    event.key == Key.Escape -> {
                        when {
                            deleteCandidateId != null -> deleteCandidateId = null
                            showImportDialog -> showImportDialog = false
                            showSettingsDialog -> showSettingsDialog = false
                            state.searchQuery.isNotEmpty() -> {
                                onSearchQueryChanged("")
                                focusManager.clearFocus()
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
                    else -> false
                }
            },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Co-Author Studio",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
                actions = {
                    EditorialTooltipBox(label = "旧データを取り込む", shortcutHint = "Ctrl+I") {
                        IconButton(onClick = { showImportDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.FileDownload,
                                contentDescription = "旧データを取り込む",
                            )
                        }
                    }
                    EditorialTooltipBox(label = "設定", shortcutHint = "Ctrl+,") {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "設定",
                            )
                        }
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
        val highlightColor = MaterialTheme.colorScheme.primary
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 340.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1040.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 4.dp,
                    bottom = innerPadding.calculateBottomPadding() + 92.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(
                    key = "search_strip",
                    contentType = "search_strip",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SearchAndFilterStrip(
                        query = state.searchQuery,
                        activeFilter = state.activeFilter,
                        isSearching = state.isSearching,
                        searchFocusRequester = searchFocusRequester,
                        onQueryChange = onSearchQueryChanged,
                        onFilterSelected = onFilterSelected,
                    )
                }

                if (state.searchQuery.isNotBlank()) {
                    item(
                        key = "search_heading",
                        contentType = "section_heading",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = "全文検索結果 (${state.searchHits.size}件)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics {
                                heading()
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                    if (state.searchHits.isEmpty() && !state.isSearching) {
                        item(
                            key = "search_empty",
                            contentType = "empty_notice",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "一致する本文・見出しが見つかりませんでした。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { onSearchQueryChanged("") },
                                    shape = BookGenRadius.Control,
                                ) {
                                    Text("検索をクリア")
                                }
                            }
                        }
                    } else {
                        items(
                            items = state.searchHits,
                            key = { "hit_${it.sectionId}" },
                            contentType = { "search_hit" },
                        ) { hit ->
                            SearchHitRow(
                                hit = hit,
                                highlightColor = highlightColor,
                                onClick = { onOpenReader(hit.bookId, (hit.chapterNumber - 1).coerceAtLeast(0)) },
                            )
                        }
                    }
                } else {
                    if (state.heroBook != null) {
                        item(
                            key = "hero_card",
                            contentType = "hero_card",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            HeroResumeCard(
                                book = state.heroBook,
                                onResumeWorkspace = { onOpenWorkspace(state.heroBook.id) },
                                onReadClick = { onOpenReader(state.heroBook.id, 0) },
                            )
                        }
                    }

                    item(
                        key = "library_heading",
                        contentType = "section_heading",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            text = "ライブラリ (${state.filteredBooks.size}冊)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.semantics {
                                heading()
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }

                    if (state.isLoading) {
                        item(
                            key = "loading",
                            contentType = "loading",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp)
                                    .semantics(mergeDescendants = true) {},
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "ライブラリを読み込み中…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else if (state.filteredBooks.isEmpty()) {
                        item(
                            key = "empty_library",
                            contentType = "empty_notice",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            if (state.books.isNotEmpty() && state.activeFilter != LibraryFilter.ALL) {
                                FilteredEmptyNotice(
                                    filterLabel = state.activeFilter.label,
                                    onResetFilter = { onFilterSelected(LibraryFilter.ALL) },
                                )
                            } else {
                                EmptyLibraryNotice(
                                    onCreateClick = onNewBookClick,
                                    onImportClick = { showImportDialog = true },
                                )
                            }
                        }
                    } else {
                        items(
                            items = state.filteredBooks,
                            key = { it.id },
                            contentType = { "book_row" },
                        ) { book ->
                            BookLibraryRow(
                                book = book,
                                onOpenWorkspace = { onOpenWorkspace(book.id) },
                                onOpenReader = { onOpenReader(book.id, 0) },
                                onRequestDelete = { deleteCandidateId = book.id },
                            )
                        }
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
            onPickDocument = { uri ->
                showImportDialog = false
                onImportDocumentUri(uri)
            },
            onPickTree = { uri ->
                showImportDialog = false
                onImportTreeUri(uri)
            },
        )
    }

    if (showSettingsDialog) {
        AiSettingsBottomSheet(
            currentSettings = aiSettings,
            currentTheme = currentTheme,
            onThemeSelected = onThemeSelected,
            onSaveSettings = onSaveAiSettings,
            onClearKeys = onClearAiKeys,
            onDismiss = { showSettingsDialog = false },
        )
    }

    deleteCandidate?.let { victim ->
        AlertDialog(
            onDismissRequest = { deleteCandidateId = null },
            title = { Text("書籍を削除しますか？") },
            text = { Text("「${victim.title}」の全章・執筆履歴が端末から完全に削除されます。") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBook(victim.id)
                        deleteCandidateId = null
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
                TextButton(onClick = { deleteCandidateId = null }) {
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
    searchFocusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onFilterSelected: (LibraryFilter) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(searchFocusRequester),
            singleLine = true,
            shape = BookGenRadius.Chip,
            placeholder = { Text("書籍タイトル・章・本文を検索…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
                    EditorialTooltipBox(label = "検索をクリア", shortcutHint = "Esc") {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                                focusManager.clearFocus()
                            },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "検索をクリア")
                        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LibraryFilter.entries.forEach { filter ->
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

@OptIn(ExperimentalLayoutApi::class)
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
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookLibraryRow(
    book: BookSummary,
    onOpenWorkspace: () -> Unit,
    onOpenReader: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    EditorialCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = "「${book.title}」のスタジオを開く",
                onClick = onOpenWorkspace,
            ),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp),
            ) {
                GenreBadge(label = book.genreName.ifBlank { book.genreKey })
                BookStatusBadge(
                    status = book.status,
                    emptyOrFailedCount = book.emptyOrFailedSections,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                EditorialTooltipBox(label = "リーダーで開く") {
                    IconButton(onClick = onOpenReader) {
                        Icon(
                            imageVector = Icons.Outlined.AutoStories,
                            contentDescription = "「${book.title}」をリーダーで開く",
                        )
                    }
                }
                EditorialTooltipBox(label = "書籍を削除") {
                    IconButton(onClick = onRequestDelete) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "「${book.title}」を削除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
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
    }
}

@Composable
private fun SearchHitRow(
    hit: SearchHit,
    highlightColor: Color,
    onClick: () -> Unit,
) {
    val highlighted = remember(hit.snippet, highlightColor) {
        buildHighlightedSnippet(hit.snippet, highlightColor)
    }
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
            text = highlighted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun buildHighlightedSnippet(raw: String, highlightColor: Color): AnnotatedString = buildAnnotatedString {
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
private fun FilteredEmptyNotice(
    filterLabel: String,
    onResetFilter: () -> Unit,
) {
    EditorialCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "「$filterLabel」に一致する書籍はありません",
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "フィルタを解除するとライブラリ内のすべての書籍が表示されます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onResetFilter, shape = BookGenRadius.Control) {
            Text("すべて表示に戻す")
        }
    }
}

@Composable
private fun LegacyImportDialog(
    isImporting: Boolean,
    onDismiss: () -> Unit,
    onConfirmPath: (String) -> Unit,
    onPickDocument: (Uri) -> Unit,
    onPickTree: (Uri) -> Unit,
) {
    var dirPath by rememberSaveable { mutableStateOf("") }
    val docLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onPickDocument(uri)
    }
    val treeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) onPickTree(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Web版 bookgen データ取込") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "SAF（Android ファイルピッカー）で `books/` フォルダや `app_data.db` を選択するか、パスを直接入力できます。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        enabled = !isImporting,
                        onClick = { treeLauncher.launch(null) },
                        modifier = Modifier.weight(1f),
                        shape = BookGenRadius.Control,
                    ) {
                        Text("フォルダ選択")
                    }
                    OutlinedButton(
                        enabled = !isImporting,
                        onClick = { docLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                        shape = BookGenRadius.Control,
                    ) {
                        Text("DB/原稿選択")
                    }
                }
                OutlinedTextField(
                    value = dirPath,
                    onValueChange = { dirPath = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("/sdcard/Download/bookgen/books") },
                    label = { Text("または直接ディレクトリパス") },
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
