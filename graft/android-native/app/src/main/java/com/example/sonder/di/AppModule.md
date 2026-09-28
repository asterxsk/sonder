# android-native/app/src/main/java/com/example/sonder/di/AppModule.kt

- AppModule · class · L22-L67 — @Module @InstallIn(SingletonComponent::class) object AppModule
- migrate · method · L32-L46 — override fun migrate(db: SupportSQLiteDatabase)
- provideDatabase · method · L49-L54 — @Provides @Singleton fun provideDatabase(@ApplicationContext context: Context): SonderDatabase
- provideTargetDao · method · L56-L56 — @Provides fun provideTargetDao(db: SonderDatabase): TargetDao
- provideGrantDao · method · L57-L57 — @Provides fun provideGrantDao(db: SonderDatabase): GrantDao
- provideDebtDao · method · L58-L58 — @Provides fun provideDebtDao(db: SonderDatabase): DebtDao
- provideLockoutDao · method · L59-L59 — @Provides fun provideLockoutDao(db: SonderDatabase): LockoutDao
- provideHandDao · method · L60-L60 — @Provides fun provideHandDao(db: SonderDatabase): HandDao
- provideDailyUsageDao · method · L61-L61 — @Provides fun provideDailyUsageDao(db: SonderDatabase): DailyUsageDao
- provideSettingsRepository · method · L63-L66 — @Provides @Singleton fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository
