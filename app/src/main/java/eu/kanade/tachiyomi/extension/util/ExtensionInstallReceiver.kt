package eu.kanade.tachiyomi.extension.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.LoadResult
import eu.kanade.tachiyomi.util.launchNow
import kotlinx.coroutines.experimental.async

internal class ExtensionInstallReceiver(private val listener: Listener) :
        BroadcastReceiver() {

    fun register(context: Context) {
        context.registerReceiver(this, filter)
    }

    private val filter get() = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED -> {
                if (!isReplacing(intent)) launchNow {
                    val result = getExtensionFromIntent(context, intent)
                    when (result) {
                        is LoadResult.Success -> listener.onExtensionInstalled(result.extension)
                        is LoadResult.Untrusted -> listener.onExtensionUntrusted(result.extension)
                    }
                }
            }
            Intent.ACTION_PACKAGE_REPLACED -> {
                launchNow {
                    val result = getExtensionFromIntent(context, intent)
                    when (result) {
                        is LoadResult.Success -> listener.onExtensionUpdated(result.extension)
                        // Not needed as a package can't be upgraded if the signature is different
                        is LoadResult.Untrusted -> {}
                    }
                }
            }
            Intent.ACTION_PACKAGE_REMOVED -> {
                if (!isReplacing(intent)) {
                    val pkgName = getPackageNameFromIntent(intent)
                    if (pkgName != null) {
                        listener.onExtensionUninstalled(pkgName)
                    }
                }
            }
        }
    }

    private fun isReplacing(intent: Intent): Boolean {
        return intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
    }

    private suspend fun getExtensionFromIntent(context: Context, intent: Intent?): LoadResult {
        val pkgName = getPackageNameFromIntent(intent) ?:
                return LoadResult.Error(Exception("Package name not found"))
        return async { ExtensionLoader.loadExtensionFromPkgName(context, pkgName) }.await()
    }

    private fun getPackageNameFromIntent(intent: Intent?): String? {
        return intent?.data?.encodedSchemeSpecificPart ?: return null
    }

    interface Listener {
        fun onExtensionInstalled(extension: Extension.Installed)
        fun onExtensionUpdated(extension: Extension.Installed)
        fun onExtensionUninstalled(pkgName: String)
        fun onExtensionUntrusted(extension: Extension.Untrusted)
    }

}