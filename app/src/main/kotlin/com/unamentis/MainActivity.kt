package com.unamentis

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.unamentis.core.network.ConnectivityMonitor
import com.unamentis.navigation.DeepLinkDestination
import com.unamentis.navigation.DeepLinkHandler
import com.unamentis.ui.UnaMentisNavHost
import com.unamentis.ui.session.SessionActivityState
import com.unamentis.ui.theme.UnaMentisTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main activity for the UnaMentis application.
 *
 * This activity hosts the Compose UI and serves as the entry point for
 * the user interface. It also handles deep links via the unamentis:// scheme.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var deepLinkHandler: DeepLinkHandler

    @Inject
    lateinit var connectivityMonitor: ConnectivityMonitor

    @Inject
    lateinit var sessionActivityState: SessionActivityState

    private var pendingDeepLink by mutableStateOf<DeepLinkDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Handle deep link from launch intent
        handleDeepLinkIntent(intent)

        setContent {
            UnaMentisTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    UnaMentisNavHost(
                        connectivityMonitor = connectivityMonitor,
                        sessionActivityState = sessionActivityState,
                        initialDeepLink = pendingDeepLink,
                        onDeepLinkConsumed = { pendingDeepLink = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running (singleTask launch mode)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val destination = deepLinkHandler.parseFromIntent(intent)
        if (destination != null && destination !is DeepLinkDestination.Unknown) {
            pendingDeepLink = destination
        }
    }
}
