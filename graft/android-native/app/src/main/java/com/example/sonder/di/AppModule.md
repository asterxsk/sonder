# android-native/app/src/main/java/com/example/sonder/di/AppModule.kt

- AppModule · class · L30-L80 — @Module @InstallIn(SingletonComponent::class) object AppModule
- migrate · method · L40-L54 — override fun migrate(db: SupportSQLiteDatabase)
- provideDatabase · method · L57-L62 — @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): SonderDatabase
- provideTargetDao · method · L64-L64 — @Provides fun provideTargetDao(db: SonderDatabase): TargetDao
- provideGrantDao · method · L65-L65 — @Provides fun provideGrantDao(db: SonderDatabase): GrantDao
- provideDebtDao · method · L66-L66 — @Provides fun provideDebtDao(db: SonderDatabase): DebtDao
- provideLockoutDao · method · L67-L67 — @Provides fun provideLockoutDao(db: SonderDatabase): LockoutDao
- provideHandDao · method · L68-L68 — @Provides fun provideHandDao(db: SonderDatabase): HandDao
- provideDailyUsageDao · method · L69-L69 — @Provides fun provideDailyUsageDao(db: SonderDatabase): DailyUsageDao
- provideApplicationScope · method · L71-L74 — @Provides @Singleton @ApplicationScope fun provideApplicationScope(): CoroutineScope
- provideSettingsRepository · method · L76-L79 — @Provides @Singleton fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository
