# android-native/app/src/main/java/com/example/sonder/theme/PixelTypography.kt

- PixelFont · variable · L12-L15 — val PixelFont = FontFamily( Font(R.font.press_start_2p, FontWeight.Normal), Font(R.font.press_start_2p, FontWeight.Bold), )
- MonoFont · variable · L18-L21 — val MonoFont = FontFamily( Font(R.font.dm_mono_regular, FontWeight.Normal), Font(R.font.dm_mono_medium, FontWeight.Medium), )
- PixelTypeScale · class · L28-L37 — object PixelTypeScale
- MonoTypeScale · class · L40-L44 — object MonoTypeScale
- PixelTypography · variable · L47-L55 — val PixelTypography = Typography( displayLarge = PixelTypeScale.Wordmark, titleLarge = PixelTypeScale.ScreenTitle, titleMedium = PixelTypeScale.SectionTitle, labelLarge = PixelTypeScale.Button, bodyLarge = MonoTypeScale.Body, bodyMedium = MonoTypeScale.Metadata, bodySmall = MonoTypeScale.PackageId, )
