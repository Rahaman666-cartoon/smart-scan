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

    private val _language = MutableStateFlow(getLanguage())
    val language: StateFlow<String> = _language

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

    fun getLanguage(): String {
        return prefs.getString("app_language", "en") ?: "en"
    }

    fun setLanguage(langCode: String) {
        prefs.edit().putString("app_language", langCode).apply()
        _language.value = langCode
    }
}
