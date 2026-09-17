# android-native/app/src/main/java/com/example/sonder/ui/kit/PixelToast.kt

- ToastTone · class · L15-L19 — sealed class ToastTone(val color: Color)
- Success · class · L16-L16 — data object Success : ToastTone(PixelPalette.Success)
- Error · class · L17-L17 — data object Error : ToastTone(PixelPalette.Danger)
- Info · class · L18-L18 — data object Info : ToastTone(PixelPalette.Info)
- PixelToast · function · L25-L43 — @Composable fun PixelToast( message: String, modifier: Modifier = Modifier, tone: ToastTone = ToastTone.Info, )
