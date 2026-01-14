package com.unamentis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.unamentis.ui.UnaMentisNavHost
import com.unamentis.ui.theme.UnaMentisTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for the UnaMentis application.
 *
 * This activity hosts the Compose UI and serves as the entry point for
 * the user interface.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            UnaMentisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    UnaMentisNavHost()
                }
            }
        }
    }
}
