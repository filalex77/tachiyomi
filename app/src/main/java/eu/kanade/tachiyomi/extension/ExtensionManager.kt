package eu.kanade.tachiyomi.extension

import android.content.Context
import com.jakewharton.rxrelay.BehaviorRelay
import eu.kanade.tachiyomi.extension.api.FDroidApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.source.SourceManager
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber


class ExtensionManager(private val context: Context) {

    private val api = FDroidApi()

    private val installer = ExtensionInstaller(context)

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
                .subscribe {
                    availableExtensions = it
                }
    }

    fun installExtension(extension: Extension.Available) {
        api.downloadExtension(extension)
                .map {
                    val stream = it.body()?.source()
                    if (stream != null) {
                        installer.installFromStream(stream, extension.apkName)
                    }
                }
                .doOnError { Timber.e(it) }
                .onErrorResumeNext(Observable.empty())
                .subscribeOn(Schedulers.io())
                .subscribe()
    }

    fun updateExtension(extension: Extension.Installed) {
        val availableExt = availableExtensions.find { it.pkgName == extension.pkgName } ?: return
        installExtension(availableExt)
    }

    fun uninstallExtension(extension: Extension.Installed) {
        installer.uninstall(extension.pkgName)
    }

    private inner class InstallationListener : ExtensionInstallReceiver.Listener {

        override fun onExtensionInstalled(extension: Extension.Installed) {
            val newExtension = extension.withUpdateCheck()
            installedExtensions += newExtension
            newExtension.sources.forEach { sourceManager.registerSource(it) }
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
        }

        override fun onExtensionUninstalled(pkgName: String) {
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