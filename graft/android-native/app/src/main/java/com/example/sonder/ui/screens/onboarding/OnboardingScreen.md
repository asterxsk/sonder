# android-native/app/src/main/java/com/example/sonder/ui/screens/onboarding/OnboardingScreen.kt

- OnboardingScreen · function · L49-L157 — @Composable fun OnboardingScreen( onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel(), )
- ProgressRailHeight · variable · L160-L160 — private val ProgressRailHeight = 8.dp
- StepCard · function · L162-L220 — @Composable private fun StepCard( permission: SonderPermission, stepNumber: Int, totalSteps: Int, alreadyGranted: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier, )
- GhostStep · function · L222-L252 — @Composable private fun GhostStep(permission: SonderPermission)
- CompletionCard · function · L254-L291 — @Composable private fun CompletionCard(onBegin: () -> Unit)
- StepCopy · class · L293-L293 — private data class StepCopy(val title: String, val body: String, val reassure: String)
- copyFor · function · L295-L316 — private fun copyFor(p: SonderPermission): StepCopy
