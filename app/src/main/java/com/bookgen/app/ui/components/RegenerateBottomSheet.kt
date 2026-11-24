package com.bookgen.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookgen.app.ui.theme.BookGenRadius

private val REGEN_TONE_PRESETS = listOf(
    "初心者向けに噛み砕いて解説する",
    "具体的な実装コード例と手順を増やす",
    "より簡潔・コンパクトに整理する",
    "技術的トレードオフと根拠を深掘りする",
    "情景・ストーリー描写を豊かに補強する",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegenerateBottomSheet(
    targetTitleLabel: String,
    sectionCount: Int,
    onDismiss: () -> Unit,
    onConfirmRegenerate: (instruction: String) -> Unit,
) {
    var instruction by rememberSaveable(targetTitleLabel) {
        mutableStateOf(REGEN_TONE_PRESETS.first())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = BookGenRadius.Sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "部分再生成・トーン調整",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "対象: $targetTitleLabel（計${sectionCount}節）\n既存の本文はリビジョン履歴に安全に保存され、いつでも元に戻せます。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "トーン・調整プリセット",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                REGEN_TONE_PRESETS.forEach { preset ->
                    FilterChip(
                        selected = instruction == preset,
                        onClick = { instruction = preset },
                        label = { Text(preset) },
                        shape = BookGenRadius.Chip,
                    )
                }
            }

            OutlinedTextField(
                value = instruction,
                onValueChange = { instruction = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                label = { Text("AI へのカスタム再生成指示") },
                placeholder = { Text("例: 具体例を2つ追加し、末尾に実践チェックリストを表形式で入れてください") },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = BookGenRadius.Control,
                ) {
                    Text("キャンセル")
                }
                Button(
                    onClick = {
                        onConfirmRegenerate(instruction.trim().ifEmpty { "品質を改善して書き直す" })
                    },
                    modifier = Modifier.weight(1f),
                    shape = BookGenRadius.Control,
                ) {
                    Text("AI で再生成を実行")
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }
}
