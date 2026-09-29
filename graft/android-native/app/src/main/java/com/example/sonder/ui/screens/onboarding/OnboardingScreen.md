# android-native/app/src/main/java/com/example/sonder/ui/screens/onboarding/OnboardingScreen.kt

- OnboardingScreen · function · L49-L162 — @Composable fun OnboardingScreen( onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel(), )
- ProgressRailHeight · variable · L165-L165 — private val ProgressRailHeight = 8.dp
- StepCard · function · L167-L225 — @Composable private fun StepCard( permission: SonderPermission, stepNumber: Int, totalSteps: Int, alreadyGranted: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier, )
- GhostStep · function · L227-L257 — @Composable private fun GhostStep(permission: SonderPermission)
- CompletionCard · function · L259-L296 — @Composable private fun CompletionCard(onBegin: () -> Unit)
- StepCopy · class · L298-L298 — private data class StepCopy(val title: String, val body: String, val reassure: String)
- copyFor · function · L300-L321 — private fun copyFor(p: SonderPermission): StepCopy
