package com.example.sonder.platform.block

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
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

    // State, not a plain field: onNewIntent retargets a REUSED gate and the live
    // composition must recompose onto the new package.
    private val targetPkg = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        coordinator.onGateShown()

        targetPkg.value = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
            ?: coordinator.pendingTargetPackage
            ?: ""

        setContent {
            SonderTheme {
                BlockRoute(
                    targetPackage = targetPkg.value,
                    // A grant is success: finish and drop the user back into the
                    // app they just unlocked, not onto the home screen.
                    onAccessGranted = { finish() },
                    onDismiss = { exitToHome() },
                )
            }
        }

        // BACK on the gate = an un-granted exit: leave for the home screen, never
        // fall through to whatever is beneath the gate.
        onBackPressedDispatcher.addCallback(
            this,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    exitToHome()
                }
            },
        )
    }

    /**
     * launchGate reuses a live gate (CLEAR_TOP|SINGLE_TOP, singleTop), so onCreate
     * does NOT re-run and the new EXTRA_TARGET_PACKAGE would be dropped. Retarget the
     * composition and re-take the overlay, or the table and header keep the first
     * blocked package and winning grants the wrong app.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPkg.value = intent.getStringExtra(EXTRA_TARGET_PACKAGE)
            ?: coordinator.pendingTargetPackage
            ?: targetPkg.value
        coordinator.onGateShown()
    }

    /**
     * Un-granted exit path: send the user to the launcher, then finish. Finishing
     * alone would pop the gate and reveal MainActivity in the same task.
     */
    private fun exitToHome() {
        val toHome = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // The gate itself cannot watch the departure — the launcher transition never
        // reaches the coordinator — so it can only re-pin when this handoff fails and
        // the blocked app is left in front. A resolved HOME activity needs no re-pin.
        if (runCatching { startActivity(toHome) }.isFailure) {
            coordinator.onGateHandoffFailed(targetPkg.value)
        }
        finish()
    }

    override fun onDestroy() {
        // If the user backed out of the gate without a grant, tell the coordinator
        // so it drops the overlay and can tell a superseded re-pin apart.
        if (isFinishing) {
            coordinator.onGateDismissed(targetPkg.value)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
    }
}
