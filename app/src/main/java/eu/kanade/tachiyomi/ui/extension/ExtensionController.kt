package eu.kanade.tachiyomi.ui.extension

import android.support.design.widget.Snackbar
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.visible
import kotlinx.android.synthetic.main.extension_controller.*


/**
 * Controller to manage the catalogues available in the app.
 */
open class ExtensionController : NucleusController<ExtensionPresenter>(),
        ExtensionAdapter.OnButtonClickListener,
        FlexibleAdapter.OnItemClickListener {

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
        ext_recycler.addItemDecoration(ExtensionDividerItemDecoration(view.context))
        ext_progress.visible()
    }

    override fun onDestroyView(view: View) {
        adapter = null
        super.onDestroyView(view)
        snack = null
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    fun onError(error: Throwable) {
//        Timber.e(error)
//        val adapter = adapter ?: return
//        adapter.onLoadMoreComplete(null)
//        hideProgressBar()
//
//        val message = view!!.context.getString(R.string.error_with_repository)
//
//        snack?.dismiss()
//        snack = ext_recycler.snack(message, Snackbar.LENGTH_INDEFINITE) {
//            setAction(R.string.action_retry) {
//                showProgressBar()
//                presenter.updateExtensions(true)
//            }
//        }
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

    override fun onButtonClick(position: Int) {
        val extension = (adapter?.getItem(position) as? ExtensionItem)?.extension ?: return

        when (extension) {
            is Extension.Installed -> {
                if (!extension.hasUpdate) {
                    //launch details page
//                router.pushController(SettingsExtensionDetailController(item.extension).withFadeTransaction())
                } else {
                    // Update dialog
                    presenter.updateExtension(extension)
                }
            }
            is Extension.Available -> {
                presenter.installExtension(extension)
            }
        }
    }

    override fun onItemClick(position: Int): Boolean {
        val extension = (adapter?.getItem(position) as? ExtensionItem)?.extension ?: return false
        if (extension is Extension.Installed) {
            presenter.uninstallExtension(extension)
        }

        return false
    }


    /**
     * Called to update adapter containing sources.
     */
    fun setExtensions(extensions: List<ExtensionItem>) {
        hideProgressBar()
        adapter?.updateDataSet(extensions)

    }

}

