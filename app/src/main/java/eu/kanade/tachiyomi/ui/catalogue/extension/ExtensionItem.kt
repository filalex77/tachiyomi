package eu.kanade.tachiyomi.ui.catalogue.extension

import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractSectionableItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.SExtension
import eu.kanade.tachiyomi.source.CatalogueSource

/**
 * Item that contains source information.
 *
 * @param source Instance of [CatalogueSource] containing source information.
 * @param header The header for this item.
 */
data class ExtensionItem(val extension: SExtension, val header: LangExtItem? = null) :
        AbstractSectionableItem<ExtensionHolder, LangExtItem>(header) {

    /**
     * Returns the layout resource of this item.
     */
    override fun getLayoutRes(): Int {
        return R.layout.extension_controller_card_item
    }

    /**
     * Creates a new view holder for this item.
     */
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<*>): ExtensionHolder {
        return ExtensionHolder(view, adapter as ExtensionAdapter)
    }

    /**
     * Binds this item to the given view holder.
     */
    override fun bindViewHolder(adapter: FlexibleAdapter<*>, holder: ExtensionHolder,
                                position: Int, payloads: List<Any?>?) {

        holder.bind(this)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ExtensionItem) {
            return extension.url == other.extension.url
        }
        return false
    }

    override fun hashCode(): Int {
        return extension.url.hashCode()
    }

}