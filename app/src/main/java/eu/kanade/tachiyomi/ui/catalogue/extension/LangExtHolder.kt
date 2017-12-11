package eu.kanade.tachiyomi.ui.catalogue.extension

import android.view.View
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import kotlinx.android.synthetic.main.extension_controller_card.*
import java.util.*

class LangExtHolder(view: View, adapter: FlexibleAdapter<*>) :
        BaseFlexibleViewHolder(view, adapter, true) {

    fun bind(item: LangExtItem) {
        title.text = when {
            item.code == "" -> itemView.context.getString(R.string.other_source)
            else -> {
                val locale = Locale(item.code)
                locale.getDisplayName(locale).capitalize()
            }
        }
    }
}