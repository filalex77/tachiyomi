package eu.kanade.tachiyomi.ui.catalogue.extension

import android.os.Build
import android.support.v4.content.ContextCompat
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.dpToPx
import eu.kanade.tachiyomi.util.getRound
import io.github.mthli.slice.Slice
import kotlinx.android.synthetic.main.extension_controller_card_item.*

class ExtensionHolder(view: View, adapter: ExtensionAdapter) : BaseFlexibleViewHolder(view, adapter) {

    private val slice = Slice(card).apply {
        setColor(adapter.cardBackground)
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension
        setCardEdges(item)

        // Set source name
        title.text = extension.name
        version.text = extension.version
        itemView.post {
            image.setImageDrawable(image.getRound(extension.name.take(1).toUpperCase(), false))
        }
        if (!extension.installed) {
            install_status.text = containerView!!.resources.getString(R.string.ext_not_installed)
            install_status.setBackgroundColor(ContextCompat.getColor(containerView!!.context, R.color.md_red_500))
        } else {
            if (extension.upToDate) {
                install_status.text = containerView!!.resources.getString(R.string.ext_up_to_date)
                install_status.setBackgroundColor(ContextCompat.getColor(containerView!!.context, R.color.md_blue_A400))
            } else {
                install_status.text = containerView!!.resources.getString(R.string.ext_out_of_date)
                install_status.setBackgroundColor(ContextCompat.getColor(containerView!!.context, R.color.md_teal_500))

            }
        }
    }

    private fun setCardEdges(item: ExtensionItem) {
        // Position of this item in its header. Defaults to 0 when header is null.
        var position = 0

        // Number of items in the header of this item. Defaults to 1 when header is null.
        var count = 1

        if (item.header != null) {
            val sectionItems = mAdapter.getSectionItems(item.header)
            position = sectionItems.indexOf(item)
            count = sectionItems.size
        }

        when {
        // Only one item in the card
            count == 1 -> applySlice(2f, false, false, true, true)
        // First item of the card
            position == 0 -> applySlice(2f, false, true, true, false)
        // Last item of the card
            position == count - 1 -> applySlice(2f, true, false, false, true)
        // Middle item
            else -> applySlice(0f, false, false, false, false)
        }
    }

    private fun applySlice(radius: Float, topRect: Boolean, bottomRect: Boolean,
                           topShadow: Boolean, bottomShadow: Boolean) {

        slice.setRadius(radius)
        slice.showLeftTopRect(topRect)
        slice.showRightTopRect(topRect)
        slice.showLeftBottomRect(bottomRect)
        slice.showRightBottomRect(bottomRect)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            slice.showTopEdgeShadow(topShadow)
            slice.showBottomEdgeShadow(bottomShadow)
        }
        setMargins(margin, if (topShadow) margin else 0, margin, if (bottomShadow) margin else 0)
    }

    private fun setMargins(left: Int, top: Int, right: Int, bottom: Int) {
        val v = card
        if (v.layoutParams is ViewGroup.MarginLayoutParams) {
            val p = v.layoutParams as ViewGroup.MarginLayoutParams
            p.setMargins(left, top, right, bottom)
        }
    }

    companion object {
        val margin = 8.dpToPx
    }
}