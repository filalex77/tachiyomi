package eu.kanade.tachiyomi.ui.catalogue.extension

import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.SExtension

class ExtensionItem(val extension: SExtension) :
        AbstractFlexibleItem<ExtensionHolder>() {

    override fun getLayoutRes(): Int {
        return R.layout.extension_list_item
    }

    override fun createViewHolder(view: View, adapter: FlexibleAdapter<*>): ExtensionHolder {
        return ExtensionListHolder(view, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<*>,
                                holder: ExtensionHolder,
                                position: Int,
                                payloads: List<Any?>?) {

        holder.onSetValues(extension)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other is ExtensionItem) {
            return extension.url!! == other.extension.url!!
        }
        return false
    }

    override fun hashCode(): Int {
        return extension.url!!.hashCode()
    }


}