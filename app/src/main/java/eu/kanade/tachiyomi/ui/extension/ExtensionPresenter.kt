package eu.kanade.tachiyomi.ui.extension

import android.os.Bundle
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

/**
 * Presenter of [ExtensionController].
 */
open class ExtensionPresenter(
        private val extensionManager: ExtensionManager = Injekt.get()
) : BasePresenter<ExtensionController>() {

    private var extensions = emptyList<ExtensionItem>()

    private var currentDownloads = hashMapOf<String, InstallStep>()

    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)

        getExtensionsObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { extensions = it }
                .subscribeLatestCache({ view, _ -> view.setExtensions(extensions) })
    }


    private fun getExtensionsObservable(): Observable<List<ExtensionItem>> {
        val installedObservable = extensionManager.getInstalledExtensionsObservable()

        val availableObservable = extensionManager.getAvailableExtensionsObservable()
                .startWith(emptyList<Extension.Available>())

        return Observable.combineLatest(installedObservable, availableObservable, { installed, available ->
            installed to available
        }).debounce(100, TimeUnit.MILLISECONDS).map { (installed, available) ->
            val items = mutableListOf<ExtensionItem>()

            val installedSorted = installed.sortedWith(compareBy({ !it.hasUpdate }, { it.name }))
            val availableSorted = available
                    // Filter out already installed extensions
                    .filter { avail -> installed.none { it.pkgName == avail.pkgName } }
                    .sortedBy { it.name }

            if (installedSorted.isNotEmpty()) {
                val header = ExtensionGroupItem(true, installedSorted.size)
                items += installedSorted.map { extension ->
                    ExtensionItem(extension, header, currentDownloads[extension.pkgName])
                }
            }
            if (availableSorted.isNotEmpty()) {
                val header = ExtensionGroupItem(false, availableSorted.size)
                items += availableSorted.map { extension ->
                    ExtensionItem(extension, header, currentDownloads[extension.pkgName])
                }
            }

            items
        }
    }

    fun installExtension(extension: Extension.Available) {
        extensionManager.installExtension(extension)
                .doOnNext { currentDownloads.put(extension.pkgName, it) }
                .doOnUnsubscribe { currentDownloads.remove(extension.pkgName) }
                .map { state ->
                    val extensions = extensions.toMutableList()
                    val position = extensions.indexOfFirst { it.extension.pkgName == extension.pkgName }

                    if (position != -1) {
                        val item = extensions[position].copy(installStep = state)
                        extensions[position] = item

                        this.extensions = extensions
                        item
                    } else {
                        null
                    }
                }
                .subscribeWithView({ view, item ->
                    if (item != null) {
                        view.downloadUpdate(item)
                    }
                })
    }

    fun updateExtension(extension: Extension.Installed) {
        extensionManager.updateExtension(extension)
    }

    fun uninstallExtension(extension: Extension.Installed) {
        extensionManager.uninstallExtension(extension)
    }

    fun findAvailableExtensions() {
        extensionManager.findAvailableExtensions()
    }

}