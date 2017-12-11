package eu.kanade.tachiyomi.ui.catalogue.extension

import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.updater.UpdateDownloaderService
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.catalogue.SourceDividerItemDecoration
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.snack
import eu.kanade.tachiyomi.util.visible
import kotlinx.android.synthetic.main.extension_controller.*
import timber.log.Timber

/**
 * Controller to manage the catalogues available in the app.
 */
open class ExtensionController :
        NucleusController<ExtensionPresenter>(),
        FlexibleAdapter.OnItemClickListener, ExtensionDownloadDialog.Listener {

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        return applicationContext?.getString(R.string.pref_category_extensions)
    }

    override fun createPresenter(): ExtensionPresenter {
        return ExtensionPresenter()
    }

    override fun inflateView(inflater: LayoutInflater, container: ViewGroup): View {
        return inflater.inflate(R.layout.extension_controller, container, false)
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)

        // Initialize adapter, scroll listener and recycler views
        adapter = ExtensionAdapter(this)
        // Create recycler and set adapter.
        ext_recycler.layoutManager = LinearLayoutManager(view.context)
        ext_recycler.adapter = adapter
        ext_recycler.addItemDecoration(SourceDividerItemDecoration(view.context))
        ext_progress.visible()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
        snack = null
    }

    /**
     * Called from the presenter when the network request is received.
     *
     * @param page the current page.
     * @param mangas the list of manga of the page.
     */
    fun onAdd(extension: List<ExtensionItem>) {
        val adapter = adapter ?: return
        hideProgressBar()
        adapter.onLoadMoreComplete(extension)
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onError(error: Throwable) {
        Timber.e(error)
        val adapter = adapter ?: return
        adapter.onLoadMoreComplete(null)
        hideProgressBar()

        val message = view!!.context.getString(R.string.error_with_repository)

        snack?.dismiss()
        snack = ext_recycler.snack(message, Snackbar.LENGTH_INDEFINITE) {
            setAction(R.string.action_retry) {
                showProgressBar()
                presenter.updateExtensions()
            }
        }
    }


    /**
     * Shows the progress bar.
     */
    private fun showProgressBar() {
        ext_progress?.visible()
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active progress bars.
     */
    private fun hideProgressBar() {
        ext_progress?.gone()
    }

    /**
     * Called when a extension is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(position: Int): Boolean {
        val item = adapter?.getItem(position) as? ExtensionItem ?: return false
        if (item.extension.upToDate) {
            return false
        }
        val appContext = applicationContext
        if (appContext != null) {
            val dialog = ExtensionDownloadDialog(this, item.extension)
            dialog.showDialog(router)
        }
        return false
    }

    /**
     * Called to update adapter containing sources.
     */
    fun setExtensions(extensions: List<IFlexible<*>>) {
        hideProgressBar()
        adapter?.updateDataSet(extensions)

    }

    override fun downloadExtension(ext: SExtension) {
        UpdateDownloaderService.downloadUpdate(applicationContext!!, ext.url)
    }

}

