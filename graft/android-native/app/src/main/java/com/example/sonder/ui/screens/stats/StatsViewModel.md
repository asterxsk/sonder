# android-native/app/src/main/java/com/example/sonder/ui/screens/stats/StatsViewModel.kt

- StatsUiState · class · L15-L20 — data class StatsUiState( val wins: Int = 0, val losses: Int = 0, val hands: List<HandEntity> = emptyList(), val loaded: Boolean = false, )
- StatsViewModel · class · L22-L39 — @HiltViewModel class StatsViewModel @Inject constructor( handDao: HandDao, ) : ViewModel()
