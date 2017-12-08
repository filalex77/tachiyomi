package eu.kanade.tachiyomi.ui.catalogue.extension

import android.support.v4.content.ContextCompat
import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.SExtension
import kotlinx.android.synthetic.main.extension_list_item.view.*

/**
 * Class used to hold the displayed data of a manga in the catalogue, like the cover or the title.
 * All the elements from the layout file "item_catalogue_list" are available in this class.
 *
 * @param view the inflated view for this holder.
 * @param adapter the adapter handling this holder.
 * @constructor creates a new catalogue holder.
 */
class ExtensionListHolder(private val view: View, adapter: FlexibleAdapter<*>) :
        ExtensionHolder(view, adapter) {

    /**
     * Method called from [CatalogueAdapter.onBindViewHolder]. It updates the data for this
     * holder with the given manga.
     *
     * @param manga the manga to bind.
     */
    override fun onSetValues(extension: SExtension) {
        view.title.text = extension.name
        view.version.text = extension.version

        if (!extension.installed) {
            view.install_status.text = view.resources.getString(R.string.not_installed)
            view.install_status.setBackgroundColor(ContextCompat.getColor(view.context, R.color.md_red_500))
        } else {
            if (extension.upToDate) {
                view.install_status.text = view.resources.getString(R.string.up_to_date)
                view.install_status.setBackgroundColor(ContextCompat.getColor(view.context, R.color.md_blue_A400))
            } else {
                view.install_status.text = view.resources.getString(R.string.out_of_date)
                view.install_status.setBackgroundColor(ContextCompat.getColor(view.context, R.color.md_teal_500))

            }
        }
    }

}