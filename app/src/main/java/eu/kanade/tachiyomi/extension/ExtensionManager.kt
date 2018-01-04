package eu.kanade.tachiyomi.extension

import android.content.Context
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.extension.api.FDroidApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.extension.util.ExtensionInstallReceiver
import eu.kanade.tachiyomi.extension.util.ExtensionInstaller
import eu.kanade.tachiyomi.extension.util.ExtensionLoader
import eu.kanade.tachiyomi.source.SourceManager
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers


class ExtensionManager(private val context: Context) {

    private val api = FDroidApi()

    private val installer by lazy { ExtensionInstaller(context) }

    private val installedExtensionsRelay = BehaviorRelay.create<List<Extension.Installed>>()

    var installedExtensions = emptyList<Extension.Installed>()
        private set(value) {
            field = value
            installedExtensionsRelay.call(value)
        }

    private val availableExtensionsRelay = BehaviorRelay.create<List<Extension.Available>>()

    var availableExtensions = emptyList<Extension.Available>()
        private set(value) {
            field = value
            availableExtensionsRelay.call(value)
            setUpdateFieldOfInstalledExtensions(value)
        }

    private lateinit var sourceManager: SourceManager

    fun init(sourceManager: SourceManager) {
        this.sourceManager = sourceManager
        initExtensions()
        ExtensionInstallReceiver(InstallationListener()).register(context)
    }

    fun getInstalledExtensionsObservable(): Observable<List<Extension.Installed>> {
        return installedExtensionsRelay
    }

    fun getAvailableExtensionsObservable(): Observable<List<Extension.Available>> {
        if (!availableExtensionsRelay.hasValue()) {
            findAvailableExtensions()
        }
        return availableExtensionsRelay
    }

    private fun setUpdateFieldOfInstalledExtensions(availableExtensions: List<Extension.Available>) {
        val mutInstalledExtensions = installedExtensions.toMutableList()
        var changed = false

        for ((index, installedExt) in mutInstalledExtensions.withIndex()) {
            val pkgName = installedExt.pkgName
            val availableExt = availableExtensions.find { it.pkgName == pkgName } ?: continue

            val hasUpdate = availableExt.versionCode > installedExt.versionCode
            if (installedExt.hasUpdate != hasUpdate) {
                mutInstalledExtensions[index] = installedExt.copy(hasUpdate = hasUpdate)
                changed = true
            }
        }
        if (changed) {
            installedExtensions = mutInstalledExtensions
        }
    }

    private fun initExtensions() {
        val extensions = ExtensionLoader.loadExtensions(context)
        installedExtensions = extensions
        extensions.flatMap { it.sources }.forEach { sourceManager.registerSource(it) }
    }

    fun findAvailableExtensions() {
        api.findExtensions()
                .onErrorReturn { emptyList() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { availableExtensions = it }
    }

    fun installExtension(extension: Extension.Available): Observable<InstallStep> {
        return installer.downloadAndInstall(api.getApkUrl(extension), extension)
    }

    fun updateExtension(extension: Extension.Installed): Observable<InstallStep>  {
        val availableExt = availableExtensions.find { it.pkgName == extension.pkgName }
                ?: return Observable.empty()
        return installExtension(availableExt)
    }

    fun uninstallExtension(extension: Extension.Installed) {
        installer.uninstallApk(extension.pkgName)
    }

    private inner class InstallationListener : ExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: Extension.Installed) {
            val newExtension = extension.withUpdateCheck()
            installedExtensions += newExtension
            newExtension.sources.forEach { sourceManager.registerSource(it) }
            installer.onApkInstalled(extension.pkgName)
        }

        override fun onExtensionUpdated(extension: Extension.Installed) {
            val mutInstalledExtensions = installedExtensions.toMutableList()
            val newExtension = extension.withUpdateCheck()
            val oldExtension = mutInstalledExtensions.find { it.pkgName == newExtension.pkgName }
            if (oldExtension != null) {
                mutInstalledExtensions -= oldExtension
            }
            mutInstalledExtensions += newExtension
            installedExtensions = mutInstalledExtensions
            newExtension.sources.forEach { sourceManager.registerSource(it, true) }
            installer.onApkInstalled(extension.pkgName)
        }

        override fun onPackageUninstalled(pkgName: String) {
            val removedExtension = installedExtensions.find { it.pkgName == pkgName }
            if (removedExtension != null) {
                installedExtensions -= removedExtension
                removedExtension.sources.forEach { sourceManager.unregisterSource(it) }
            }
        }

        private fun Extension.Installed.withUpdateCheck(): Extension.Installed {
            val availableExt = availableExtensions.find { it.pkgName == pkgName }
            if (availableExt != null && availableExt.versionCode > versionCode) {
                return copy(hasUpdate = true)
            }
            return this
        }
    }

}