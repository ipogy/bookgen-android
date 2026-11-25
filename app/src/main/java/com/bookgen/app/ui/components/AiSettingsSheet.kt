package com.bookgen.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bookgen.app.domain.model.AiSettings
import com.bookgen.app.domain.model.ThemePreference
import com.bookgen.app.ui.theme.BookGenRadius
import com.bookgen.app.ui.theme.BookGenThemeExtras

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSettingsBottomSheet(
    currentSettings: AiSettings,
    currentTheme: ThemePreference? = null,
    onThemeSelected: ((ThemePreference) -> Unit)? = null,
    onSaveSettings: (AiSettings) -> Unit,
    onClearKeys: () -> Unit,
    onDismiss: () -> Unit,
) {
    var geminiKey by remember(currentSettings) { mutableStateOf(currentSettings.geminiApiKey) }
    var openRouterKey by remember(currentSettings) { mutableStateOf(currentSettings.openRouterApiKey) }
    var plannerModel by remember(currentSettings) { mutableStateOf(currentSettings.plannerModel) }
    var writerModel by remember(currentSettings) { mutableStateOf(currentSettings.writerModel) }
    var embeddingModel by remember(currentSettings) { mutableStateOf(currentSettings.embeddingModel) }
    var providers by remember(currentSettings) { mutableStateOf(currentSettings.openRouterProviders) }
    var concurrency by remember(currentSettings) { mutableIntStateOf(currentSettings.maxConcurrency) }
    var revealKeys by rememberSaveable { mutableStateOf(false) }
    var confirmClearKeys by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = BookGenRadius.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "スタジオ設定 & BYOK APIキー",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "設定を閉じる",
                    )
                }
            }
            Text(
                text = "APIキーは端末内の Android Keystore (AES-256-GCM) にのみ暗号化保存され、SQLite やログには記録されません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (currentTheme != null && onThemeSelected != null) {
                HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
                Text(
                    text = "アプリテーマ",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                .clickable(
                                    role = Role.RadioButton,
                                    onClick = { onThemeSelected(pref) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = currentTheme == pref,
                                onClick = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(text = label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
            Text(
                text = "AI プロバイダ認証 (BYOK)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            val visual = if (revealKeys) VisualTransformation.None else PasswordVisualTransformation()
            val eyeIcon = if (revealKeys) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility

            OutlinedTextField(
                value = geminiKey,
                onValueChange = { geminiKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = visual,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                label = { Text("Gemini API Key (AIza...)") },
                supportingText = { Text("現在: ${currentSettings.maskedGeminiKey()}") },
                trailingIcon = {
                    IconButton(onClick = { revealKeys = !revealKeys }) {
                        Icon(
                            imageVector = eyeIcon,
                            contentDescription = if (revealKeys) "APIキーを隠す" else "APIキーを表示する",
                        )
                    }
                },
            )

            OutlinedTextField(
                value = openRouterKey,
                onValueChange = { openRouterKey = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = visual,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                ),
                label = { Text("OpenRouter API Key (sk-or-...)") },
                supportingText = { Text("スラッシュ入りモデル (例: inclusionai/...) で使用") },
            )

            HorizontalDivider(color = BookGenThemeExtras.colors.borderSubtle)
            Text(
                text = "モデル・並列制御",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = plannerModel,
                onValueChange = { plannerModel = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("構成企画モデル (Planner)") },
            )
            OutlinedTextField(
                value = writerModel,
                onValueChange = { writerModel = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("本文執筆モデル (Writer)") },
            )
            OutlinedTextField(
                value = embeddingModel,
                onValueChange = { embeddingModel = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("ジャンル分類 Embedding モデル") },
            )
            OutlinedTextField(
                value = providers,
                onValueChange = { providers = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("OpenRouter プロバイダ指定 (カンマ区切り)") },
            )

            Text(
                text = "同時生成スレッド数: ${concurrency}並列",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..4).forEach { count ->
                    FilterChip(
                        selected = concurrency == count,
                        onClick = { concurrency = count },
                        label = { Text("${count}並列") },
                        shape = BookGenRadius.Chip,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentSettings.hasAnyApiKey()) {
                    OutlinedButton(
                        onClick = { confirmClearKeys = true },
                        shape = BookGenRadius.Control,
                    ) {
                        Text("キー消去", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text("キャンセル")
                    }
                }
                Button(
                    onClick = {
                        onSaveSettings(
                            AiSettings(
                                geminiApiKey = geminiKey.trim(),
                                openRouterApiKey = openRouterKey.trim(),
                                embeddingModel = plannerModelOrDefault(embeddingModel, AiSettings.DEFAULT_EMBEDDING_MODEL),
                                plannerModel = plannerModelOrDefault(plannerModel, AiSettings.DEFAULT_PLANNER_MODEL),
                                writerModel = plannerModelOrDefault(writerModel, AiSettings.DEFAULT_WRITER_MODEL),
                                openRouterProviders = providers.trim(),
                                maxConcurrency = concurrency,
                            ),
                        )
                        onDismiss()
                    },
                    shape = BookGenRadius.Control,
                ) {
                    Text("設定を保存")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmClearKeys) {
        AlertDialog(
            onDismissRequest = { confirmClearKeys = false },
            title = { Text("APIキーを消去しますか？") },
            text = { Text("端末の Keystore に保存されている Gemini / OpenRouter APIキーを削除します。") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmClearKeys = false
                        onClearKeys()
                        geminiKey = ""
                        openRouterKey = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("消去する")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearKeys = false }) {
                    Text("キャンセル")
                }
            },
        )
    }
}

private fun plannerModelOrDefault(raw: String, fallback: String): String =
    raw.trim().ifEmpty { fallback }
