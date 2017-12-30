package eu.kanade.tachiyomi.extension

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import com.jakewharton.rxrelay.PublishRelay
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

internal class ExtensionInstaller(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val downloadReceiver = DownloadCompletionReceiver()

    private val requestedDownloads = hashMapOf<String, Long>()

    private val downloadsRelay = PublishRelay.create<Pair<Long, InstallStep>>()

    fun downloadAndInstall(url: String, extension: Extension) = Observable.defer {
        val pkgName = extension.pkgName

        val oldDownload = requestedDownloads[pkgName]
        if (oldDownload != null) {
            deleteDownload(pkgName)
        }

        downloadReceiver.register()

        val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(extension.name)
                .setMimeType(APK_MIME)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val id = downloadManager.enqueue(request)
        requestedDownloads.put(pkgName, id)

        downloadsRelay.filter { it.first == id }
                .map { it.second }
                // Poll download status
                .mergeWith(pollStatus(id))
                // Force an error if the download takes more than 3 minutes
                .mergeWith(Observable.timer(3, TimeUnit.MINUTES).map { InstallStep.Error })
                // Stop when the application is installed or errors
                .takeUntil { it.isCompleted() }
                // Always notify on main thread
                .observeOn(AndroidSchedulers.mainThread())
                // Always remove the download when unsubscribed
                .doOnUnsubscribe { deleteDownload(pkgName) }
    }

    private fun pollStatus(id: Long): Observable<InstallStep> {
        val query = DownloadManager.Query().setFilterById(id)

        return Observable.interval(0, 1, TimeUnit.SECONDS)
                // Get the current download status
                .map {
                    downloadManager.query(query).use { cursor ->
                        cursor.moveToFirst()
                        cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    }
                }
                // Ignore duplicate results
                .distinctUntilChanged()
                // Stop polling when the download fails or finishes
                .takeUntil { it == DownloadManager.STATUS_SUCCESSFUL || it == DownloadManager.STATUS_FAILED }
                // Map to our model
                .flatMap { status ->
                    when (status) {
                        DownloadManager.STATUS_PENDING -> Observable.just(InstallStep.Pending)
                        DownloadManager.STATUS_RUNNING -> Observable.just(InstallStep.Downloading)
                        else -> Observable.empty()
                    }
                }
    }

    fun completeDownload(pkgName: String) {
        val id = requestedDownloads[pkgName] ?: return
        downloadsRelay.call(id to InstallStep.Installed)
    }

    fun installApk(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)

        context.startActivity(intent)
    }

    fun uninstallApk(pkgName: String) {
        val packageUri = Uri.parse("package:$pkgName")
        val uninstallIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri)
        context.startActivity(uninstallIntent)
    }

    fun deleteDownload(pkgName: String) {
        val downloadId = requestedDownloads.remove(pkgName)
        if (downloadId != null) {
            downloadManager.remove(downloadId)
        }
        if (requestedDownloads.isEmpty()) {
            downloadReceiver.unregister()
        }
    }

    private inner class DownloadCompletionReceiver : BroadcastReceiver() {

        private var isRegistered = false

        fun register() {
            if (isRegistered) return
            isRegistered = true

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            context.registerReceiver(this, filter)
        }

        fun unregister() {
            if (!isRegistered) return
            isRegistered = false

            context.unregisterReceiver(this)
        }

        override fun onReceive(context: Context, intent: Intent?) {
            val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0) ?: return

            val uri = downloadManager.getUriForDownloadedFile(id)
            if (uri != null) {
                downloadsRelay.call(id to InstallStep.Installing)
                installApk(uri)
            } else {
                downloadsRelay.call(id to InstallStep.Error)
            }
        }
    }

    private companion object {
        const val APK_MIME = "application/vnd.android.package-archive"
    }

}