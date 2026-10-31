package com.nvllz.stepsy.ui.announcement

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.fragment.app.FragmentActivity
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.components.PrimaryButton
import com.nvllz.stepsy.ui.components.SecondaryButton
import com.nvllz.stepsy.ui.theme.StepsyTheme
import kotlinx.coroutines.launch

enum class Kind { ANNOUNCEMENT, STAR }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementSheet(kind: Kind, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var locked by remember {
        mutableStateOf(
            kind == Kind.ANNOUNCEMENT && AnnouncementContent.current?.swipeDismiss == false
        )
    }
    val confirmValueChange = remember<(SheetValue) -> Boolean> {
        { value -> !locked || value != SheetValue.Hidden }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmValueChange,
    )

    fun close() {
        locked = false
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!locked) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !locked,
        dragHandle = null,
        containerColor = StepsyTheme.colors.dialogSurface,
    ) {
        BackHandler(enabled = locked) {  }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 24.dp),
        ) {
            BottomSheetDefaults.DragHandle()
            Spacer(Modifier.height(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(StepsyTheme.colors.accentOpaque),
            ) {
                when (kind) {
                    Kind.STAR -> Icon(
                        painterResource(R.drawable.ic_github),
                        contentDescription = null,
                        tint = StepsyTheme.colors.accent,
                        modifier = Modifier.size(48.dp),
                    )
                    Kind.ANNOUNCEMENT -> {
                        val emoji = AnnouncementContent.current?.badge?.ifBlank { null }
                        if (emoji != null) {
                            Text(text = emoji, fontSize = 40.sp)
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_notification),
                                contentDescription = null,
                                tint = StepsyTheme.colors.special,
                                modifier = Modifier.size(42.dp),
                            )
                        }
                    }
                }
            }

            when (kind) {
                Kind.ANNOUNCEMENT -> AnnouncementContent.current?.let { a ->
                    Text(
                        text = a.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Text(
                        text = a.body.asRichText(StepsyTheme.colors.accent),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    QuietAction(
                        icon = R.drawable.ic_language,
                        label = stringResource(R.string.translate),
                        onClick = {
                            val plain = if (a.bodyIsHtml) {
                                HtmlCompat.fromHtml(a.body, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
                            } else {
                                a.body
                            }
                            val text = "${a.title}\n\n$plain"
                            val url = Uri.parse("https://translate.google.com/")
                                .buildUpon()
                                .appendQueryParameter("sl", "auto")
                                .appendQueryParameter("text", text)
                                .build()

                            openUrl(context, url.toString())
                        },
                    )
                    val act = context as? FragmentActivity
                    if (a.actionLabel != null && a.onAction != null && act != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                        ) {
                            PrimaryButton(
                                text = a.actionLabel,
                                onClick = {
                                    a.onAction?.invoke(act)
                                    close()
                                },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                text = stringResource(R.string.announcement_dismiss),
                                onClick = { close() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        PrimaryButton(
                            text = stringResource(R.string.announcement_dismiss),
                            onClick = { close() },
                            modifier = Modifier
                                .padding(top = 20.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
                Kind.STAR -> {
                    Text(
                        text = stringResource(R.string.star_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    Text(
                        text = stringResource(R.string.star_body),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                    PrimaryButton(
                        text = stringResource(R.string.star_button),
                        onClick = {
                            openUrl(context, GITHUB_URL)
                            close()
                        },
                        icon = R.drawable.ic_star,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .fillMaxWidth(),
                    )
                    SecondaryButton(
                        text = stringResource(R.string.star_dismiss),
                        onClick = { close() },
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietAction(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(top = 14.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = StepsyTheme.colors.accent,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            fontSize = 13.sp,
            color = StepsyTheme.colors.accent,
        )
    }
}

private fun String.asRichText(linkColor: Color): AnnotatedString {
    val spanned = HtmlCompat.fromHtml(this, HtmlCompat.FROM_HTML_MODE_LEGACY) as Spanned
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            if (start >= end) return@forEach
            when (span) {
                is URLSpan -> {
                    addStyle(
                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
                        start, end,
                    )
                    addLink(
                        LinkAnnotation.Url(
                            url = span.url,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline
                                ),
                            ),
                        ),
                        start, end,
                    )
                }
                is StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD ->
                        addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    android.graphics.Typeface.ITALIC ->
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    android.graphics.Typeface.BOLD_ITALIC ->
                        addStyle(
                            SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                            start, end,
                        )
                }
                is TypefaceSpan ->
                    if (span.family?.endsWith("monospace") == true) {
                        addStyle(SpanStyle(fontFamily = FontFamily.Monospace), start, end)
                    }
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) { }
}

const val GITHUB_URL = "https://github.com/nvllz/stepsy"