package eu.kanade.tachiyomi.extension

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.kanade.tachiyomi.util.getUriCompat
import eu.kanade.tachiyomi.util.saveTo
import okio.BufferedSource
import java.io.File

internal class ExtensionInstaller(private val context: Context) {

    private val extensionDir = File(context.cacheDir, "extension_cache")

    init {
        extensionDir.delete()
        extensionDir.mkdirs()
    }

    fun installFromStream(source: BufferedSource, apkName: String) {
        val destination = File(extensionDir, apkName)
        source.saveTo(destination)
        installFromFile(destination)
    }

    private fun installFromFile(file: File) {
        val uri = file.getUriCompat(context)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun uninstall(pkgName: String) {
        val packageUri = Uri.parse("package:$pkgName")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        context.startActivity(uninstallIntent)
    }
}