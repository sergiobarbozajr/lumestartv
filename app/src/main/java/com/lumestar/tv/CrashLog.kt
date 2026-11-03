package com.lumestar.tv

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Guarda o motivo quando o app fecha sozinho, para mostrar em Configurações › Sobre o aparelho. */
object CrashLog {
    private var file: File? = null
    var last: String = ""
        private set
    /** true quando o app fechou na última vez e o usuário ainda não viu */
    var fresh = false
        private set

    fun init(c: Context) {
        val f = File(c.filesDir, "ultimo-erro.txt"); file = f
        if (f.exists()) { last = f.readText(); fresh = c.getSharedPreferences("lumestar", Context.MODE_PRIVATE).getLong("erroVisto", 0L) != f.lastModified() }
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val v = try { c.packageManager.getPackageInfo(c.packageName, 0).versionName } catch (x: Exception) { "?" }
                f.writeText("Versão $v · " + SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(Date()) + " · linha ${t.name}\n" + android.util.Log.getStackTraceString(e).take(6000))
            } catch (x: Throwable) {}
            prev?.uncaughtException(t, e)
        }
    }

    fun markSeen(c: Context) { file?.let { c.getSharedPreferences("lumestar", Context.MODE_PRIVATE).edit().putLong("erroVisto", it.lastModified()).apply() }; fresh = false }
    fun clear() { file?.delete(); last = ""; fresh = false }
}
