# android-native/app/src/main/java/com/example/sonder/ui/screens/onboarding/OnboardingScreen.kt

- OnboardingScreen · function · L52-L156 — @Composable fun OnboardingScreen( onDone: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel(), )
- StepCard · function · L158-L217 — @Composable private fun StepCard( permission: SonderPermission, stepNumber: Int, totalSteps: Int, alreadyGranted: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier, )
- GhostStep · function · L219-L249 — @Composable private fun GhostStep(permission: SonderPermission)
- CompletionCard · function · L251-L288 — @Composable private fun CompletionCard(onBegin: () -> Unit)
- StepCopy · class · L290-L290 — private data class StepCopy(val title: String, val body: String, val reassure: String)
- copyFor · function · L292-L313 — private fun copyFor(p: SonderPermission): StepCopy
- openPermissionSettings · function · L315-L334 — private fun openPermissionSettings(context: android.content.Context, permission: SonderPermission)
