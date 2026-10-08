package com.example.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smartscan_settings", Context.MODE_PRIVATE)

    enum class ThemeMode {
        SYSTEM, LIGHT, DARK
    }

    enum class AppThemeAccent {
        DEFAULT, INDIGO, TEAL, CORAL
    }

    enum class PdfQuality(val compression: Int) {
        LOW(45), MEDIUM(75), HIGH(100)
    }

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode

    private val _themeAccent = MutableStateFlow(getThemeAccent())
    val themeAccent: StateFlow<AppThemeAccent> = _themeAccent

    private val _pdfQuality = MutableStateFlow(getPdfQuality())
    val pdfQuality: StateFlow<PdfQuality> = _pdfQuality



    private val _userName = MutableStateFlow(getUserName())
    val userName: StateFlow<String> = _userName

    private val _userAvatarIndex = MutableStateFlow(getUserAvatarIndex())
    val userAvatarIndex: StateFlow<Int> = _userAvatarIndex

    private val _qrCodesRead = MutableStateFlow(getQrCodesRead())
    val qrCodesRead: StateFlow<Int> = _qrCodesRead

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun getThemeAccent(): AppThemeAccent {
        val name = prefs.getString("theme_accent", AppThemeAccent.DEFAULT.name) ?: AppThemeAccent.DEFAULT.name
        return try { AppThemeAccent.valueOf(name) } catch (e: Exception) { AppThemeAccent.DEFAULT }
    }

    fun setThemeAccent(accent: AppThemeAccent) {
        prefs.edit().putString("theme_accent", accent.name).apply()
        _themeAccent.value = accent
    }

    fun getPdfQuality(): PdfQuality {
        val name = prefs.getString("pdf_quality", PdfQuality.MEDIUM.name) ?: PdfQuality.MEDIUM.name
        return try { PdfQuality.valueOf(name) } catch (e: Exception) { PdfQuality.MEDIUM }
    }

    fun setPdfQuality(quality: PdfQuality) {
        prefs.edit().putString("pdf_quality", quality.name).apply()
        _pdfQuality.value = quality
    }



    fun getUserName(): String {
        return prefs.getString("user_name", "SmartScan Pro") ?: "SmartScan Pro"
    }

    fun setUserName(name: String) {
        prefs.edit().putString("user_name", name).apply()
        _userName.value = name
    }

    fun getUserAvatarIndex(): Int {
        return prefs.getInt("user_avatar_index", 0)
    }

    fun setUserAvatarIndex(index: Int) {
        prefs.edit().putInt("user_avatar_index", index).apply()
        _userAvatarIndex.value = index
    }

    fun getQrCodesRead(): Int {
        return prefs.getInt("qr_codes_read", 0)
    }

    fun incrementQrCodesRead() {
        val current = getQrCodesRead()
        val newCount = current + 1
        prefs.edit().putInt("qr_codes_read", newCount).apply()
        _qrCodesRead.value = newCount
    }
}
