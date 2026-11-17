package com.danny.dannyapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.model.TournamentUiModel

class TournamentCalendarAdapter(
    private val onTournamentClick: (TournamentUiModel) -> Unit,
    private val onStartTournamentClick: (TournamentUiModel) -> Unit,
    private val onSimulateCompleteClick: (TournamentUiModel) -> Unit
) : ListAdapter<TournamentUiModel, TournamentCalendarAdapter.TournamentViewHolder>(TournamentDiffCallback) {

    var simulatingTournamentId: Long? = null
        set(value) {
            val oldId = field
            if (oldId != value) {
                field = value
                if (oldId != null) {
                    val oldIndex = currentList.indexOfFirst { it.tournament.id == oldId }
                    if (oldIndex != -1) notifyItemChanged(oldIndex)
                }
                if (value != null) {
                    val newIndex = currentList.indexOfFirst { it.tournament.id == value }
                    if (newIndex != -1) notifyItemChanged(newIndex)
                }
            }
        }

    class TournamentViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TournamentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tournament, parent, false)
        return TournamentViewHolder(view)
    }

    override fun onBindViewHolder(holder: TournamentViewHolder, position: Int) {
        val model = getItem(position)
        TournamentUiHelper.bindTournamentTile(holder.itemView, model, onTournamentClick, onStartTournamentClick, onSimulateCompleteClick, simulatingTournamentId)
    }

    private object TournamentDiffCallback : DiffUtil.ItemCallback<TournamentUiModel>() {
        override fun areItemsTheSame(oldItem: TournamentUiModel, newItem: TournamentUiModel): Boolean {
            return oldItem.tournament.id == newItem.tournament.id
        }

        override fun areContentsTheSame(oldItem: TournamentUiModel, newItem: TournamentUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
