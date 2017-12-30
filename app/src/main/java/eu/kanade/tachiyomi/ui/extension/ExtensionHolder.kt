package eu.kanade.tachiyomi.ui.extension

import android.os.Build
import android.view.View
import android.view.ViewGroup
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.dpToPx
import eu.kanade.tachiyomi.util.getRound
import io.github.mthli.slice.Slice
import kotlinx.android.synthetic.main.extension_card_item.*
import java.util.*

class ExtensionHolder(view: View, private val adapter: ExtensionAdapter) :
        BaseFlexibleViewHolder(view, adapter) {

    private val slice = Slice(card).apply {
        setColor(adapter.cardBackground)
    }

    init {
        ext_button.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(adapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension
        setCardEdges(item)

        // Set source name
        ext_title.text = extension.name
        version.text = extension.versionName
        lang.text = when {
            extension.lang == "" -> itemView.context.getString(R.string.other_source)
            extension.lang == "all" -> itemView.context.getString(R.string.all_lang)
            else -> {
                val locale = Locale(extension.lang)
                locale.getDisplayName(locale).capitalize()
            }
        }
        itemView.post {
            image.setImageDrawable(image.getRound(extension.name.take(1).toUpperCase(), false))
        }
        bindButton(item)
    }

    fun bindButton(item: ExtensionItem) = with(ext_button) {
        isEnabled = true
        isClickable = true
        isActivated = false

        val installStep = item.installStep
        if (installStep != null) {
            setText(when (installStep) {
                InstallStep.Pending -> R.string.ext_pending
                InstallStep.Downloading -> R.string.ext_downloading
                InstallStep.Installing -> R.string.ext_installing
                InstallStep.Installed -> R.string.ext_installed
                InstallStep.Error -> R.string.action_retry
            })
            if (installStep != InstallStep.Error) {
                isEnabled = false
                isClickable = false
            }
        } else if (item.extension is Extension.Installed) {
            if (item.extension.hasUpdate) {
                isActivated = true
                setText(R.string.ext_update)
            } else {
                setText(R.string.ext_details)
            }
        } else {
            setText(R.string.ext_install)
        }
    }

    private fun setCardEdges(item: ExtensionItem) {
        // Position of this item in its header. Defaults to 0 when header is null.
        var position = 0

        // Number of items in the header of this item. Defaults to 1 when header is null.
        var count = 1

        if (item.header != null) {
            val sectionItems = adapter.getSectionItems(item.header)
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