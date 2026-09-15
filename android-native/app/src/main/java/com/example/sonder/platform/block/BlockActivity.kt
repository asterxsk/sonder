package com.example.sonder.platform.block

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.sonder.platform.enforcement.EnforcementCoordinator
import com.example.sonder.ui.screens.block.BlockRoute
import com.example.sonder.theme.SonderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Full-screen gate shown when a blocked app is opened. Hosts the blackjack table.
 * Dismisses the coordinator overlay the moment it is in front.
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    @Inject lateinit var coordinator: EnforcementCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        coordinator.onGateShown()

        val targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
            ?: coordinator.pendingTargetPackage
            ?: ""

        setContent {
            SonderTheme {
                BlockRoute(
                    targetPackage = targetPkg,
                    onAccessGranted = { finish() },
                    onDismiss = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        // If the user backed out of the gate without a grant, clear the pending target
        // so the next window event re-gates cleanly.
        if (isFinishing) {
            coordinator.dismissOverlay()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
