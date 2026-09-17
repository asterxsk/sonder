# android-native/app/src/main/java/com/example/sonder/di/AppModule.kt

- AppModule · class · L19-L40 — @Module @InstallIn(SingletonComponent::class) object AppModule
- provideDatabase · method · L23-L28 — @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): SonderDatabase
- provideTargetDao · method · L30-L30 — @Provides fun provideTargetDao(db: SonderDatabase): TargetDao
- provideGrantDao · method · L31-L31 — @Provides fun provideGrantDao(db: SonderDatabase): GrantDao
- provideDebtDao · method · L32-L32 — @Provides fun provideDebtDao(db: SonderDatabase): DebtDao
- provideLockoutDao · method · L33-L33 — @Provides fun provideLockoutDao(db: SonderDatabase): LockoutDao
- provideHandDao · method · L34-L34 — @Provides fun provideHandDao(db: SonderDatabase): HandDao
- provideSettingsRepository · method · L36-L39 — @Provides @Singleton fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository
