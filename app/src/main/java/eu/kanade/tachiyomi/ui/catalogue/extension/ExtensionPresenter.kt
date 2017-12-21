package eu.kanade.tachiyomi.ui.catalogue.extension

import android.os.Bundle
import eu.kanade.tachiyomi.extension.ExtensionManager
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.ui.base.presenter.BasePresenter
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import timber.log.Timber
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Presenter of [ExtensionController].
 */
open class ExtensionPresenter(
        private val extensionManager: ExtensionManager = Injekt.get()
) : BasePresenter<ExtensionController>() {

    private var repoSubscription: Subscription? = null


    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        updateExtensions()
    }


    fun updateExtensions(clearCache : Boolean = false) {
        repoSubscription?.unsubscribe()
        if(clearCache){
            extensionManager.clearCache()
        }
        val getExt = extensionManager.getExtensions().flatMapIterable { it -> it }
        val extItems = getExt.groupBy { it -> it.installed }.flatMap { it -> it.toList() }.map { it -> getExtensionItems(it) }.toList()
        repoSubscription = extItems.observeOn(AndroidSchedulers.mainThread()).subscribeOn(Schedulers.io()).subscribeLatestCache(
                { view, ext ->
                    Timber.d("set Extension")
                    ext.forEach {
                        Timber.d("ext size %s", it.size)
                    }

                    val sorted = ext.flatten().sortedWith(compareBy<ExtensionItem> { !it.extension.installed }.thenBy {it.extension.upToDate }.thenBy { it.extension.lang }.thenBy { it.extension.name })
                    view?.setExtensions(sorted)
                },
                { _, error ->
                    Timber.e(error)
                    view?.onError(error)
                })

    }

    private fun getExtensionItems(ext: MutableList<SExtension>): List<ExtensionItem> {
        val langItem = ExtensionGroupItem(ext[0].installed, ext.size )
        var extItems: MutableList<ExtensionItem> = arrayListOf()
        ext.forEach { ex ->
            extItems.add(ExtensionItem(ex, langItem))
        }
        return extItems
    }


}