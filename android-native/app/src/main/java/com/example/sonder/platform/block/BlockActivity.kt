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

    private var targetPkg: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        coordinator.onGateShown()

        targetPkg = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
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

        // BACK gesture/button on the gate = leave (with re-gate cooldown), never loop.
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )
    }

    override fun onDestroy() {
        // If the user backed out of the gate without a grant, start the re-gate
        // cooldown so back doesn't loop straight back into the table.
        if (isFinishing) {
            coordinator.onGateDismissed(targetPkg)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
