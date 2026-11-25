package com.bookgen.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bookgen.app.ui.navigation.BookGenApp
import com.bookgen.app.ui.theme.BookGenTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_BOOK_ID = "com.bookgen.app.EXTRA_BOOK_ID"
    }

    private var initialBookIdState by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        initialBookIdState = intent?.getStringExtra(EXTRA_BOOK_ID)
        val container = (application as BookGenApplication).container

        setContent {
            val themePreference by container.themePreference.collectAsStateWithLifecycle()
            BookGenTheme(preference = themePreference) {
                BookGenApp(
                    container = container,
                    initialBookId = initialBookIdState,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialBookIdState = intent.getStringExtra(EXTRA_BOOK_ID)
    }

    override fun onProvideKeyboardShortcuts(
        data: MutableList<KeyboardShortcutGroup>?,
        menu: Menu?,
        deviceId: Int,
    ) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data ?: return
        data.add(
            KeyboardShortcutGroup(
                "ライブラリ / 企画",
                listOf(
                    KeyboardShortcutInfo("検索にフォーカス", KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("新しい本を企画", KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("旧データを取り込む", KeyEvent.KEYCODE_I, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("設定 / APIキー", KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("構成案を生成 (企画画面)", KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON),
                ),
            ),
        )
        data.add(
            KeyboardShortcutGroup(
                "執筆スタジオ",
                listOf(
                    KeyboardShortcutInfo("章を追加", KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("リーダーを開く", KeyEvent.KEYCODE_R, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("生成を開始 / 一時停止", KeyEvent.KEYCODE_ENTER, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("編集中の節を保存", KeyEvent.KEYCODE_S, KeyEvent.META_CTRL_ON),
                ),
            ),
        )
        data.add(
            KeyboardShortcutGroup(
                "リーダー",
                listOf(
                    KeyboardShortcutInfo("前の章へ", KeyEvent.KEYCODE_DPAD_LEFT, 0),
                    KeyboardShortcutInfo("次の章へ", KeyEvent.KEYCODE_DPAD_RIGHT, 0),
                    KeyboardShortcutInfo("目次を開く", KeyEvent.KEYCODE_T, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("表示設定を開く", KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("書籍を書き出す", KeyEvent.KEYCODE_E, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("文字サイズ拡大", KeyEvent.KEYCODE_EQUALS, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("文字サイズ縮小", KeyEvent.KEYCODE_MINUS, KeyEvent.META_CTRL_ON),
                    KeyboardShortcutInfo("文字サイズリセット", KeyEvent.KEYCODE_0, KeyEvent.META_CTRL_ON),
                ),
            ),
        )
    }
}
