/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.nvllz.stepsy.ui


import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.nvllz.stepsy.R
import com.nvllz.stepsy.ui.cards.TextItem
import java.util.*

/**
 * An adapter for the [RecyclerView] that contains [TextItem].
 * It contains dynamically changing items that display statistics and activities.
 *
 *
 * Created by tiefensuche on 19.10.16.
 */
internal class TextItemAdapter : RecyclerView.Adapter<TextItemAdapter.ViewHolder>() {
    private val mDataset = ArrayList<TextItem>()

    internal fun addTop(item: TextItem) {
        add(item, 0)
    }

    internal fun add(item: TextItem, position: Int = mDataset.size) {
        mDataset.add(position, item)
        notifyItemInserted(position)
    }

    internal fun remove(index: Int) {
        mDataset.removeAt(index)
        notifyItemRemoved(index)
    }

    internal operator fun get(index: Int): TextItem {
        return mDataset[index]
    }

    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.cardview_text, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mDataset[position]
        holder.mTextViewDescription.text = item.description
        holder.mTextViewContent.text = item.content
        if (item.buttonClickListener != null) {
            holder.mImageButton.setOnClickListener(item.buttonClickListener)
            holder.mImageButton.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            holder.mImageButton.visibility = View.GONE
        }
        item.setUpdateListener(object : TextItem.UpdateListener {
            override fun update(text: String?) {
                holder.mTextViewContent.text = text
            }

            override fun updateIcon(id: Int) {
                holder.mImageButton.setImageResource(id)
            }
        })
    }

    override fun getItemCount(): Int {
        return mDataset.size
    }

    internal inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        internal val mTextViewDescription: TextView = v.findViewById(R.id.textViewDescription)
        internal val mTextViewContent: TextView = v.findViewById(R.id.textViewContent)
        internal val mImageButton: ImageButton = v.findViewById(R.id.imageButton)
    }

}