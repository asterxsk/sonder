# android-native/app/src/main/java/com/example/sonder/ui/screens/onboarding/OnboardingViewModel.kt

- OnboardingViewModel · class · L22-L64 — @HiltViewModel class OnboardingViewModel @Inject constructor( private val audit: PermissionAudit, private val settings: SettingsRepository, ) : ViewModel()
- refresh · method · L48-L51 — fun refresh()
- currentStepOf · method · L54-L56 — private fun currentStepOf(missing: Set<SonderPermission>): Int
- completeOnboarding · method · L58-L63 — fun completeOnboarding(onDone: () -> Unit)
