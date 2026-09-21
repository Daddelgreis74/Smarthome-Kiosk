package com.example.smarthomekiosk

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class KioskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("KioskApplication", "FATAL CRASH in thread ${thread.name}", throwable)
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                
                val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                val fullReport = "$deviceInfo\n\n$stackTrace"
                
                try {
                    val crashFile = File(filesDir, "latest_crash.txt")
                    crashFile.writeText(fullReport)
                } catch (e: Exception) {
                    Log.e("KioskApplication", "Failed to write crash log", e)
                }

                val intent = Intent(this, CrashActivity::class.java).apply {
                    putExtra("error_message", throwable.localizedMessage ?: throwable.javaClass.simpleName)
                    putExtra("stack_trace", fullReport)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
                
                Process.killProcess(Process.myPid())
                System.exit(1)
            } catch (e: Exception) {
                Log.e("KioskApplication", "Error in crash handler", e)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
