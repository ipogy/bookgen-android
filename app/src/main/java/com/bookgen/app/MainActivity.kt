package com.bookgen.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
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
}
