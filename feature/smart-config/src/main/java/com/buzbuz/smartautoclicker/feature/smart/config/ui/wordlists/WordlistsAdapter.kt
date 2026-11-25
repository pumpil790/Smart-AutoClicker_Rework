/*
 * Copyright (C) 2026 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.feature.smart.config.ui.wordlists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.buzbuz.smartautoclicker.core.domain.wordlists.WordlistInfo
import com.buzbuz.smartautoclicker.feature.smart.config.R
import com.buzbuz.smartautoclicker.feature.smart.config.databinding.ItemWordlistBinding

internal class WordlistsAdapter(
    private val onItemClicked: (WordlistInfo) -> Unit,
    private val onEditClicked: (WordlistInfo) -> Unit,
    private val onExportClicked: (WordlistInfo) -> Unit,
    private val onDeleteClicked: (WordlistInfo) -> Unit,
) : ListAdapter<WordlistInfo, WordlistsAdapter.WordlistViewHolder>(WordlistDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordlistViewHolder =
        WordlistViewHolder(
            ItemWordlistBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: WordlistViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class WordlistViewHolder(
        private val binding: ItemWordlistBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: WordlistInfo) {
            binding.apply {
                textWordlistName.text = "${item.name}.txt"
                textWordlistCount.text = root.context.getString(R.string.item_wordlist_entries_count, item.entryCount)
                wordlistInfoContainer.setOnClickListener { onItemClicked(item) }
                buttonEditWordlist.setOnClickListener { onEditClicked(item) }
                buttonExportWordlist.setOnClickListener { onExportClicked(item) }
                buttonDeleteWordlist.setOnClickListener { onDeleteClicked(item) }
            }
        }
    }
}

private object WordlistDiffCallback : DiffUtil.ItemCallback<WordlistInfo>() {
    override fun areItemsTheSame(oldItem: WordlistInfo, newItem: WordlistInfo): Boolean =
        oldItem.name == newItem.name

    override fun areContentsTheSame(oldItem: WordlistInfo, newItem: WordlistInfo): Boolean =
        oldItem == newItem
}
