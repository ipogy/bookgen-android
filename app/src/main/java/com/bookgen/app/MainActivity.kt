package com.bookgen.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bookgen.core.uniffi.coreHealthCheck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BootstrapGreeting(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun BootstrapGreeting(modifier: Modifier = Modifier) {
    val statusText = remember {
        runCatching {
            val health = coreHealthCheck("android-bootstrap")
            "${health.engineName} v${health.crateVersion}: ${health.echoNote}"
        }.getOrElse { err ->
            "Rust Core init error: ${err.message}"
        }
    }
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Co-Author Studio",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
