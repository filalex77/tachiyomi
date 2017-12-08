package eu.kanade.tachiyomi.ui.catalogue.extension

import android.support.design.widget.Snackbar
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.IFlexible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.updater.UpdateDownloaderService
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.ui.base.controller.NucleusController
import eu.kanade.tachiyomi.ui.catalogue.browse.ProgressItem
import eu.kanade.tachiyomi.util.gone
import eu.kanade.tachiyomi.util.snack
import eu.kanade.tachiyomi.util.visible
import kotlinx.android.synthetic.main.extension_controller.view.*
import timber.log.Timber
import uy.kohesive.injekt.injectLazy

/**
 * Controller to manage the catalogues available in the app.
 */
open class ExtensionController :
        NucleusController<ExtensionPresenter>(),
        FlexibleAdapter.OnItemClickListener {

    /**
     * Preferences helper.
     */
    private val preferences: PreferencesHelper by injectLazy()

    /**
     * Adapter containing the list of manga from the catalogue.
     */
    private var adapter: FlexibleAdapter<IFlexible<*>>? = null

    /**
     * Snackbar containing an error message when a request fails.
     */
    private var snack: Snackbar? = null


    /**
     * Recycler view with the list of results.
     */
    private var recycler: RecyclerView? = null

    /**
     * Drawer listener to allow swipe only for closing the drawer.
     */
    private var drawerListener: DrawerLayout.DrawerListener? = null


    /**
     * Endless loading item.
     */
    private var progressItem: ProgressItem? = null

    init {
        setHasOptionsMenu(true)
    }

    override fun getTitle(): String? {
        if (resources != null) {
            return resources!!.getString(R.string.pref_category_extensions)
        }
        return ""
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
        adapter = FlexibleAdapter(null, this)
        setupRecycler(view)

        view.progress?.visible()
    }

    override fun onDestroyView(view: View) {
        super.onDestroyView(view)
        adapter = null
        snack = null
        recycler = null
    }

    private fun setupRecycler(view: View) {

        var oldPosition = RecyclerView.NO_POSITION
        val oldRecycler = view.extension_view?.getChildAt(1)
        if (oldRecycler is RecyclerView) {
            oldPosition = (oldRecycler.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            oldRecycler.adapter = null

            view.extension_view?.removeView(oldRecycler)
        }

        val recycler = RecyclerView(view.context).apply {
            id = R.id.recycler
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
        recycler.setHasFixedSize(true)
        recycler.adapter = adapter

        view.extension_view.addView(recycler, 1)

        if (oldPosition != RecyclerView.NO_POSITION) {
            recycler.layoutManager.scrollToPosition(oldPosition)
        }
        this.recycler = recycler
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
        snack = view?.extension_view?.snack(message, Snackbar.LENGTH_INDEFINITE) {
            setAction(R.string.action_retry) {
                // If not the first page, show bottom progress bar.
                if (adapter.mainItemCount > 0) {
                    val item = progressItem ?: return@setAction
                    adapter.addScrollableFooterWithDelay(item, 0, true)
                } else {
                    showProgressBar()
                }
                presenter.requestAgain()
            }
        }
    }


    /**
     * Returns the view holder for the given manga.
     *
     * @param manga the manga to find.
     * @return the holder of the manga or null if it's not bound.
     */
    private fun getHolder(extension: SExtension): ExtensionHolder? {
        val adapter = adapter ?: return null

        adapter.allBoundViewHolders.forEach { holder ->
            val item = adapter.getItem(holder.adapterPosition) as? ExtensionItem
            if (item != null && item.extension.url!! == extension.url!!) {
                return holder as ExtensionHolder
            }
        }

        return null
    }

    /**
     * Shows the progress bar.
     */
    private fun showProgressBar() {
        view?.progress?.visible()
        snack?.dismiss()
        snack = null
    }

    /**
     * Hides active progress bars.
     */
    private fun hideProgressBar() {
        view?.progress?.gone()
    }

    /**
     * Called when a extension is clicked.
     *
     * @param position the position of the element clicked.
     * @return true if the item should be selected, false otherwise.
     */
    override fun onItemClick(position: Int): Boolean {
        val item = adapter?.getItem(position) as? ExtensionItem ?: return false
        val appContext = applicationContext
        if (appContext != null) {
            UpdateDownloaderService.downloadUpdate(appContext, item.extension.url)
        }

        return false
    }
}

