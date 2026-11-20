# AndroidX Test runner loads Kotlin runtime facades reflectively from the target APK.
# Keep them in minified debug builds so on-device instrumentation tests can start.
-keep class kotlin.** { *; }
-dontwarn kotlin.**
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class androidx.compose.** { *; }

# Cross-APK instrumentation tests inspect dialog recreation and focus state.
-keepclassmembers class com.sour.lumaduo.utils.AboutDialogLauncher {
	*** dialogSnapshot*(...);
}
-keep class com.sour.lumaduo.utils.AboutDialogLauncher$DialogSnapshot { *; }

# Instrumentation invokes these target-APK symbols after the debug APK is minified.
-keepclassmembers class com.sour.lumaduo.binding.input.advance_setting.sqlite.SuperConfigDatabaseHelper {
    public java.lang.Object queryElementAttribute(long, long, java.lang.String);
}
-keep class com.sour.lumaduo.grid.PcCardDecor { *; }
-keep class com.sour.lumaduo.utils.AppTheme { *; }
-keep class com.sour.lumaduo.utils.BgAccent { *; }
-keep class com.sour.lumaduo.gamemenu.TouchPointerSensitivity** { *; }
-keep class com.sour.lumaduo.gamemenu.TouchPointerPreset** { *; }
-keep class com.sour.lumaduo.gamemenu.GameMenuCardsKt { *; }
# USB panel instrumentation renders state and exercises controller navigation.
-keep class com.sour.lumaduo.UsbDevicePanelKt { *; }
-keep class com.sour.lumaduo.UsbPanelDevice { *; }
-keep class com.sour.lumaduo.UsbDeviceType** { *; }
-keep class com.sour.lumaduo.utils.AppActionSheet** { *; }
-keep class com.sour.lumaduo.ui.ControllerInputKt {
    public static boolean isGamepadNavigationSource(android.view.KeyEvent);
}
# Per-app settings instrumentation verifies the JSON/Intent round trip after R8.
-keep class com.sour.lumaduo.utils.AppSettingsManager {
    public <init>(android.content.Context);
    public static final com.sour.lumaduo.utils.AppSettingsManager$Companion Companion;
    public void saveAppLastSettings(java.lang.String, com.sour.lumaduo.nvstream.http.NvApp, com.sour.lumaduo.preferences.PreferenceConfiguration);
    public com.sour.lumaduo.preferences.PreferenceConfiguration getAppLastSettings(java.lang.String, com.sour.lumaduo.nvstream.http.NvApp);
    public com.sour.lumaduo.utils.LastSettingsSummary getLastSettingsSummary(java.lang.String, com.sour.lumaduo.nvstream.http.NvApp);
    public boolean applyLastSettingsFromIntent(android.content.Intent, com.sour.lumaduo.preferences.PreferenceConfiguration);
}
-keep class com.sour.lumaduo.utils.AppSettingsManager$Companion {
    public void addLastSettingsToIntent(android.content.Intent, com.sour.lumaduo.preferences.PreferenceConfiguration);
}
-keep class com.sour.lumaduo.utils.LastSettingsSummary {
    public java.util.List getRows();
}
-keepclassmembers class com.sour.lumaduo.nvstream.http.NvApp {
    public <init>(java.lang.String, int, boolean);
}
-keepclassmembers class com.sour.lumaduo.preferences.IconListPreference {
    public int[] getEntryIcons();
}
-keepclassmembers class com.sour.lumaduo.preferences.DisplayChoicePreference {
    public java.util.Set getEnabledValues();
}

# Virtual-controller instrumentation calls across the target/test APK boundary.
-keep class com.sour.lumaduo.binding.input.virtual_controller.** { *; }

# Instrumentation exercises constructors and IME metadata across the APK boundary.
-keepclassmembers class com.sour.lumaduo.ui.StreamView {
    public <init>(...);
    public void setTextInputEnabled(boolean);
    public boolean isTextInputEnabled();
}

# Cursor instrumentation checks negotiated mode across the target/test APK boundary.
-keepclassmembers class com.sour.lumaduo.CursorServiceManager {
    public boolean isServiceRunning();
}

# Remote-IME instrumentation constructs the controller via default arguments and
# copies RemoteTextContext fixtures across the APK boundary.
-keep class com.sour.lumaduo.utils.RemoteImeController { <init>(...); }
-keep class com.sour.lumaduo.nvstream.RemoteTextContext { *; }

# Display-panel instrumentation constructs state and calls the target APK's
# composables/persistence seam. These symbols must retain their cross-APK ABI.
# Test composables also read Compose-generated $stable fields on these models.
-keep class com.sour.lumaduo.gamemenu.GameMenuScreenKt {
    public static void GameMenuScreen(...);
}
-keep class com.sour.lumaduo.gamemenu.GameMenuCallbacks { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.GameMenuComposeUiState { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.GameMenuVisibleCards { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.GameMenuGuideDismissController { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.BitrateCardState { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.DisplayChoice { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.DisplaySettingsDraft { <init>(...); public static final int $stable; }
-keepclassmembers class com.sour.lumaduo.gamemenu.SensitivityParameter {
    public static final int $stable;
}
-keepclassmembers class com.sour.lumaduo.preferences.TouchpadInputValues {
    public <init>(...);
}
-keep class com.sour.lumaduo.gamemenu.AudioHapticsCardState { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.WaveformHapticsCardState { <init>(...); public static final int $stable; }
-keep class com.sour.lumaduo.gamemenu.GyroCardState { <init>(...); public static final int $stable; }
-keepclassmembers class com.sour.lumaduo.gamemenu.BitrateCardController {
    public static final com.sour.lumaduo.gamemenu.BitrateCardController$Companion Companion;
}
-keep class com.sour.lumaduo.gamemenu.BitrateCardController$Companion {
    *** persistDisplayDraft*(...);
}
-keep class com.sour.lumaduo.DisplayReconnectSettings** { *; }
-keepclassmembers class com.sour.lumaduo.preferences.DisplayPreferenceValues {
    public java.util.Map of(...);
}
-keepclassmembers class com.sour.lumaduo.preferences.PreferenceConfiguration {
    public <init>();
    public *** *Width(...);
    public *** *Height(...);
    public *** *Fps(...);
    public *** *Bitrate(...);
    public *** *Resolution(...);
    public *** *AbrMode(...);
    public *** *ScreenCombinationMode(...);
    public boolean getShowDisplaySettingsCard();
    public void setShowDisplaySettingsCard(boolean);
    public void setResolutionScale(int);
    public boolean getShowAudioHapticsCard();
    public int getPerfOverlayBgOpacity();
}
-keepclassmembers class com.sour.lumaduo.preferences.PreferenceConfiguration$Companion {
    public int getDefaultBitrate(java.lang.String, java.lang.String);
    public com.sour.lumaduo.preferences.PreferenceConfiguration readPreferences(android.content.Context, android.view.Display);
}

# Feature-page navigation is exercised by instrumentation after R8.
-keep class com.sour.lumaduo.preferences.StreamSettings$SettingsFragment {
    public <init>();
    public java.lang.String getActiveSettingsScreenKey();
    public void applySearchFilter(java.lang.String);
    public void openSettingsScreen(java.lang.String, java.lang.String);
    public boolean closeSettingsScreen();
}

# Backup instrumentation exercises the public exporter/importer across APKs.
-keep class com.sour.lumaduo.utils.ConfigurationSyncManager {
    public <init>(android.content.Context);
    public java.lang.String exportSyncPackage();
    public com.sour.lumaduo.utils.ConfigurationSyncManager$ImportResult importSyncPackage(java.lang.String, java.lang.String);
}
-keep class com.sour.lumaduo.utils.ConfigurationSyncManager$ImportResult {
    public int getDefaultPreferencesImported();
    public int getTotalImported();
    public int getCrownProfilesFailed();
    public int getPairingItemsFailed();
}
