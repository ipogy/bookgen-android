package com.bookgen.app.ui.screens.wizard

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.GenreInfo
import com.bookgen.app.domain.model.MarkupFormat
import com.bookgen.app.ui.components.AiSettingsBottomSheet
import com.bookgen.app.ui.components.EditorialCard
import com.bookgen.app.ui.components.GenreBadge
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras
import kotlin.math.roundToInt

private val TONE_PRESETS = listOf("厳密・論理", "平易・解説", "実践ハンズオン")
private val SAMPLE_THEMES = listOf(
    "RustとKotlin Multiplatformで作るモバイルシステム設計" to "技術実務書",
    "情報処理技術者試験に一発合格するための計算・午後問題攻略" to "資格対策",
    "海底都市の記録係が失われた設計図を探すSFミステリー" to "小説プロット",
)

@Composable
fun WizardRoute(
    viewModel: WizardViewModel,
    onBack: () -> Unit,
    onBookCreated: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val aiSettings by viewModel.aiSettings.collectAsStateWithLifecycle()

    WizardScreen(
        state = state,
        aiSettings = aiSettings,
        onBack = onBack,
        onThemeChanged = viewModel::onThemeChanged,
        onTitleChanged = viewModel::onTitleChanged,
        onAudienceChanged = viewModel::onAudienceChanged,
        onToggleAutoDetect = viewModel::onToggleAutoDetect,
        onSelectGenre = viewModel::onSelectGenre,
        onSelectTemplate = viewModel::onSelectTemplate,
        onChapterCountChanged = viewModel::onChapterCountChanged,
        onSectionsPerChapterChanged = viewModel::onSectionsPerChapterChanged,
        onFormatSelected = viewModel::onFormatSelected,
        onToneSelected = viewModel::onToneSelected,
        onToggleUseAiOutline = viewModel::onToggleUseAiOutline,
        onSaveAiSettings = viewModel::saveAiSettings,
        onClearAiKeys = viewModel::clearAiKeys,
        onSubmit = { viewModel.createPlannedBook(onBookCreated) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WizardScreen(
    state: WizardUiState,
    aiSettings: AiSettings,
    onBack: () -> Unit,
    onThemeChanged: (String) -> Unit,
    onTitleChanged: (String) -> Unit,
    onAudienceChanged: (String) -> Unit,
    onToggleAutoDetect: (Boolean) -> Unit,
    onSelectGenre: (GenreInfo) -> Unit,
    onSelectTemplate: (StructureTemplate) -> Unit,
    onChapterCountChanged: (Int) -> Unit,
    onSectionsPerChapterChanged: (Int) -> Unit,
    onFormatSelected: (MarkupFormat) -> Unit,
    onToneSelected: (String) -> Unit,
    onToggleUseAiOutline: (Boolean) -> Unit,
    onSaveAiSettings: (AiSettings) -> Unit,
    onClearAiKeys: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("新しい本を企画") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "戻る",
                        )
                    }
                },
                actions = {
                    Surface(
                        shape = BookGenRadius.Chip,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.semantics {
                            contentDescription = "作成手順 1 / 2"
                        },
                    ) {
                        Text(
                            text = "ステップ 1/2",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Tune,
                            contentDescription = "AIモデル・キー設定",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.navigationBarsPadding()) {
                    HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = "約${state.targetCharCount}字 / ${state.chapterCount}章・${state.chapterCount * state.sectionsPerChapter}節",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (state.errorMessage != null) {
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                        }
                        Button(
                            onClick = onSubmit,
                            enabled = !state.isSubmitting,
                            shape = BookGenRadius.Control,
                        ) {
                            Text(if (state.isSubmitting) "構成生成中…" else "構成案を生成する")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "theme_card", contentType = "theme_card") {
                    EditorialCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "書籍のテーマ・企画メモ",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.themePrompt,
                            onValueChange = onThemeChanged,
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 5,
                            isError = state.errorMessage != null,
                            label = { Text("テーマ・企画概要 *") },
                            placeholder = { Text("例: Kotlin ComposeとRustで高速・堅牢なオフラインアプリを作る実践ガイド") },
                            supportingText = {
                                Text(
                                    text = state.errorMessage ?: "必須項目・書きたいテーマや対象読者を1〜3文で入力してください",
                                )
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "サンプルテーマから入力:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            SAMPLE_THEMES.forEach { (sampleText, label) ->
                                FilterChip(
                                    selected = state.themePrompt == sampleText,
                                    onClick = { onThemeChanged(sampleText) },
                                    label = { Text(label) },
                                    shape = BookGenRadius.Chip,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = state.bookTitle,
                            onValueChange = onTitleChanged,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("書籍タイトル（空欄なら自動設定）") },
                        )
                    }
                }

                item(key = "genre_section", contentType = "genre_section") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "ジャンル判定",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.autoDetectGenre,
                                onClick = { onToggleAutoDetect(true) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) {
                                Text("AI自動判定")
                            }
                            SegmentedButton(
                                selected = !state.autoDetectGenre,
                                onClick = { onToggleAutoDetect(false) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) {
                                Text("手動選択 (12種)")
                            }
                        }

                        state.selectedGenre?.let { chosen ->
                            EditorialCard(modifier = Modifier.fillMaxWidth()) {
                                GenreBadge(label = chosen.name)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = chosen.elementLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (!state.autoDetectGenre) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                state.genres.forEach { genre ->
                                    FilterChip(
                                        selected = state.selectedGenre?.key == genre.key,
                                        onClick = { onSelectGenre(genre) },
                                        label = { Text(genre.name) },
                                        shape = BookGenRadius.Chip,
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "params_card", contentType = "params_card") {
                    EditorialCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "構成テンプレート・執筆パラメータ",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            StructureTemplate.entries.forEach { tpl ->
                                FilterChip(
                                    selected = state.template == tpl,
                                    onClick = { onSelectTemplate(tpl) },
                                    label = { Text(tpl.label) },
                                    shape = BookGenRadius.Chip,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "章数: ${state.chapterCount}章",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = state.chapterCount.toFloat(),
                            onValueChange = { onChapterCountChanged(it.roundToInt()) },
                            valueRange = 2f..12f,
                            steps = 9,
                            modifier = Modifier.semantics { contentDescription = "章数スライダー" },
                        )
                        Text(
                            text = "1章あたりの節数: ${state.sectionsPerChapter}節",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Slider(
                            value = state.sectionsPerChapter.toFloat(),
                            onValueChange = { onSectionsPerChapterChanged(it.roundToInt()) },
                            valueRange = 2f..6f,
                            steps = 3,
                            modifier = Modifier.semantics { contentDescription = "1章あたりの節数スライダー" },
                        )

                        Spacer(Modifier.height(10.dp))
                        Text("ソース記法", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.markupFormat == MarkupFormat.MARKDOWN,
                                onClick = { onFormatSelected(MarkupFormat.MARKDOWN) },
                                shape = SegmentedButtonDefaults.itemShape(0, 2),
                            ) {
                                Text("Markdown")
                            }
                            SegmentedButton(
                                selected = state.markupFormat == MarkupFormat.ASCIIDOC,
                                onClick = { onFormatSelected(MarkupFormat.ASCIIDOC) },
                                shape = SegmentedButtonDefaults.itemShape(1, 2),
                            ) {
                                Text("AsciiDoc")
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text("文体・トーン", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TONE_PRESETS.forEach { toneOpt ->
                                FilterChip(
                                    selected = state.tone == toneOpt,
                                    onClick = { onToneSelected(toneOpt) },
                                    label = { Text(toneOpt) },
                                    shape = BookGenRadius.Chip,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.targetAudience,
                            onValueChange = onAudienceChanged,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("想定読者") },
                        )

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled = state.hasPlannerKey,
                                    role = Role.Switch,
                                    onClick = { onToggleUseAiOutline(!(state.useAiOutline && state.hasPlannerKey)) },
                                )
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = "AIで構成案を詳細企画する",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    text = if (state.hasPlannerKey) {
                                        "モデル (${aiSettings.plannerModel}) で目次JSONを立案します"
                                    } else {
                                        "APIキー未設定のためローカル骨組みを作成（後から執筆可能）"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = state.useAiOutline && state.hasPlannerKey,
                                enabled = state.hasPlannerKey,
                                onCheckedChange = null,
                            )
                        }
                        if (!state.hasPlannerKey) {
                            TextButton(onClick = { showSettingsSheet = true }) {
                                Text("APIキーを設定してAI自動企画を使う")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        AiSettingsBottomSheet(
            currentSettings = aiSettings,
            onSaveSettings = onSaveAiSettings,
            onClearKeys = onClearAiKeys,
            onDismiss = { showSettingsSheet = false },
        )
    }
}
