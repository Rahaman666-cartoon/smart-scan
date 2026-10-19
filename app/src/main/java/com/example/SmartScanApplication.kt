package com.example

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SmartScanApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}

