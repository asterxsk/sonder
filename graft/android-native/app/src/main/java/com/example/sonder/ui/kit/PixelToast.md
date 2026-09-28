# android-native/app/src/main/java/com/example/sonder/ui/kit/PixelToast.kt

- ToastTone · class · L16-L20 — sealed class ToastTone(val color: androidx.compose.ui.graphics.Color)
- Success · class · L17-L17 — data object Success : ToastTone(PixelPalette.Success)
- Error · class · L18-L18 — data object Error : ToastTone(PixelPalette.Danger)
- Info · class · L19-L19 — data object Info : ToastTone(PixelPalette.Info)
- PixelToast · function · L26-L45 — @Composable fun PixelToast( message: String, modifier: Modifier = Modifier, tone: ToastTone = ToastTone.Info, )
