package com.nvllz.stepsy.ui.announcement

import android.content.ActivityNotFoundException
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.text.HtmlCompat
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.nvllz.stepsy.R

class AnnouncementSheet : BottomSheetDialogFragment() {

    enum class Kind { ANNOUNCEMENT, STAR }

    private val kind: Kind
        get() = Kind.valueOf(requireArguments().getString(ARG_KIND)!!)

    override fun getTheme(): Int = R.style.ThemeOverlay_stepsy_BottomSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isCancelable = when (kind) {
            Kind.ANNOUNCEMENT ->
                AnnouncementContent.current?.swipeDismiss ?: true
            Kind.STAR -> true
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_announcement, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        when (kind) {
            Kind.ANNOUNCEMENT -> {
                val a = AnnouncementContent.current
                if (a == null) { dismissAllowingStateLoss(); return }
                bindAnnouncement(view, a)
            }
            Kind.STAR -> bindStar(view)
        }
    }

    override fun onStart() {
        super.onStart()

        (dialog as? BottomSheetDialog)?.behavior?.apply {
            skipCollapsed = true
            isHideable = isCancelable
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        AnnouncementManager.markHandled(kind)
    }

    private fun bindAnnouncement(root: View, a: Announcement) {
        bindBadge(root, a.badge.ifBlank { null })

        root.findViewById<TextView>(R.id.sheet_title).text = a.title
        bindBody(root, a.body, a.bodyIsHtml)

        val primary = root.findViewById<MaterialButton>(R.id.sheet_primary)
        val secondary = root.findViewById<MaterialButton>(R.id.sheet_secondary)

        val action = a.onAction
        if (a.actionLabel != null && action != null) {
            primary.text = a.actionLabel
            primary.setOnClickListener {
                val act = requireActivity()
                dismiss()
                action(act)
            }
            secondary.apply {
                visibility = View.VISIBLE
                setText(R.string.announcement_dismiss)
                setOnClickListener { dismiss() }
            }
        } else {
            primary.setText(R.string.announcement_dismiss)
            primary.setOnClickListener { dismiss() }
            secondary.visibility = View.GONE
        }

        val translate = root.findViewById<MaterialButton>(R.id.sheet_translate)
        translate.visibility = View.VISIBLE
        translate.setOnClickListener {
            val plainBody = if (a.bodyIsHtml) {
                HtmlCompat.fromHtml(
                    a.body,
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                ).toString()
            } else {
                a.body
            }

            val text = "${a.title}\n\n$plainBody"

            val url = Uri.parse("https://translate.google.com/")
                .buildUpon()
                .appendQueryParameter("sl", "auto")
                .appendQueryParameter("text", text)
                .build()

            openUrl(requireActivity(), url.toString())
        }
    }

    private fun bindBadge(root: View, emoji: String? = null) {
        val icon = root.findViewById<ImageView>(R.id.sheet_badge_icon)
        val emojiView = root.findViewById<TextView>(R.id.sheet_badge_emoji)

        if (emoji != null) {
            icon.visibility = View.GONE
            emojiView.visibility = View.VISIBLE
            emojiView.text = emoji
        } else {
            emojiView.visibility = View.GONE
            icon.visibility = View.VISIBLE
        }
    }

    private fun bindStar(root: View) {
        val ctx = requireContext()

        bindBadge(root)
        root.findViewById<TextView>(R.id.sheet_title).setText(R.string.star_title)
        bindBody(root, getString(R.string.star_body), false)

        root.findViewById<MaterialButton>(R.id.sheet_primary).apply {
            setText(R.string.star_button)
            backgroundTintList =
                ContextCompat.getColorStateList(ctx, R.color.colorSpecial)
            setTextColor(ContextCompat.getColor(ctx, R.color.colorPrimary))

            setIconResource(R.drawable.ic_github)
            iconTint = ContextCompat.getColorStateList(ctx, R.color.colorPrimary)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = (8 * resources.displayMetrics.density).toInt()

            setOnClickListener {
                openUrl(requireActivity(), GITHUB_URL)
                dismiss()
            }
        }
        root.findViewById<MaterialButton>(R.id.sheet_secondary).apply {
            visibility = View.VISIBLE
            setText(R.string.star_dismiss)
            setOnClickListener { dismiss() }
        }
        root.findViewById<MaterialButton>(R.id.sheet_translate)
            .visibility = View.GONE
    }

    private fun bindBody(root: View, body: String, html: Boolean) {
        root.findViewById<TextView>(R.id.sheet_body).apply {
            visibility = View.VISIBLE
            if (html) {
                text = HtmlCompat.fromHtml(body, HtmlCompat.FROM_HTML_MODE_LEGACY)
                movementMethod = LinkMovementMethod.getInstance()
                setLinkTextColor(ContextCompat.getColor(context, R.color.colorAccent))
            } else {
                text = body
            }
        }
    }

    private fun openUrl(activity: FragmentActivity, url: String) {
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) { }
    }

    companion object {
        const val TAG = "announcement_sheet"
        private const val ARG_KIND = "kind"
        private const val GITHUB_URL = "https://github.com/nvllz/stepsy"

        fun show(fm: FragmentManager, kind: Kind) {
            AnnouncementSheet().apply {
                arguments = bundleOf(ARG_KIND to kind.name)
            }.show(fm, TAG)
        }
    }
}