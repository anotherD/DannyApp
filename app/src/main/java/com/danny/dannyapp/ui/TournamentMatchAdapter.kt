package com.danny.dannyapp.ui

import android.graphics.Typeface
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.data.db.TournamentMatch
import com.google.android.material.card.MaterialCardView

data class MatchUiModel(
    val match: TournamentMatch,
    val player1Name: String,
    val player1Seed: Int?,
    val player1Flag: String,
    val player1CountryCode: String,
    val player1Skill: Int,
    val player2Name: String,
    val player2Seed: Int?,
    val player2Flag: String,
    val player2CountryCode: String,
    val player2Skill: Int,
    val isUserMatch: Boolean,
    val isCompleted: Boolean,
    val winnerId: Long?,
    val scoreText: String,
    val roundDisplayName: String
)

class TournamentMatchAdapter(
    private val onMatchClick: (MatchUiModel) -> Unit
) : ListAdapter<MatchUiModel, TournamentMatchAdapter.ViewHolder>(MatchDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardMatch: MaterialCardView = view.findViewById(R.id.cardMatch)
        val tvMatchRoundIndex: TextView = view.findViewById(R.id.tvMatchRoundIndex)
        val tvPlayer1Flag: TextView = view.findViewById(R.id.tvPlayer1Flag)
        val tvPlayer1Name: TextView = view.findViewById(R.id.tvPlayer1Name)
        val tvPlayer1Score: TextView = view.findViewById(R.id.tvPlayer1Score)
        val tvPlayer2Flag: TextView = view.findViewById(R.id.tvPlayer2Flag)
        val tvPlayer2Name: TextView = view.findViewById(R.id.tvPlayer2Name)
        val tvPlayer2Score: TextView = view.findViewById(R.id.tvPlayer2Score)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tournament_match, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val match = item.match
        val context = holder.itemView.context

        val groupPrefix = if (!match.groupName.isNullOrBlank()) "Gruppe ${match.groupName} • " else ""
        holder.tvMatchRoundIndex.text = "${groupPrefix}#${match.matchIndex + 1}"

        bindPlayerFlag(context, holder.tvPlayer1Flag, item.player1CountryCode, item.player1Flag)
        bindPlayerName(holder.tvPlayer1Name, item.player1Name, item.player1Seed, isLeft = true)

        bindPlayerFlag(context, holder.tvPlayer2Flag, item.player2CountryCode, item.player2Flag)
        bindPlayerName(holder.tvPlayer2Name, item.player2Name, item.player2Seed, isLeft = false)

        if (item.isCompleted) {
            holder.tvPlayer1Score.text = match.player1Score.toString()
            holder.tvPlayer2Score.text = match.player2Score.toString()

            if (item.winnerId == match.player1Id) {
                holder.tvPlayer1Name.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer1Score.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Score.setTypeface(null, Typeface.NORMAL)
            } else if (item.winnerId == match.player2Id) {
                holder.tvPlayer2Name.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer2Score.setTypeface(null, Typeface.BOLD)
                holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer1Score.setTypeface(null, Typeface.NORMAL)
            } else {
                holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
                holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
            }
        } else {
            holder.tvPlayer1Score.text = "-"
            holder.tvPlayer2Score.text = "-"
            holder.tvPlayer1Name.setTypeface(null, Typeface.NORMAL)
            holder.tvPlayer2Name.setTypeface(null, Typeface.NORMAL)
        }

        if (item.isCompleted) {
            holder.cardMatch.setCardBackgroundColor(ContextCompat.getColor(context, R.color.anthrazit_flaeche))
        } else {
            holder.cardMatch.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface_grau_heller))
        }

        if (item.isUserMatch) {
            holder.cardMatch.strokeColor = ContextCompat.getColor(context, R.color.rot_hell)
            holder.cardMatch.strokeWidth = (2 * context.resources.displayMetrics.density).toInt()
        } else {
            holder.cardMatch.strokeColor = ContextCompat.getColor(context, R.color.anthrazit_container_hoechst)
            holder.cardMatch.strokeWidth = (1 * context.resources.displayMetrics.density).toInt()
        }

        holder.itemView.setOnClickListener {
            onMatchClick(item)
        }
    }

    private fun bindPlayerFlag(context: android.content.Context, tvFlag: TextView, countryCode: String, fallbackEmoji: String) {
        val flagResId = Country.getFlagResId(countryCode)
        if (flagResId != null) {
            val spanBuilder = android.text.SpannableStringBuilder(" ")
            val drawable = ContextCompat.getDrawable(context, flagResId)!!
            val heightPx = (tvFlag.textSize * 1.17f).toInt()
            val widthPx = if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                (heightPx * drawable.intrinsicWidth.toFloat() / drawable.intrinsicHeight.toFloat()).toInt()
            } else {
                (heightPx * 1.5f).toInt()
            }
            drawable.setBounds(0, 0, widthPx, heightPx)
            val imageSpan = VerticalCenteredImageSpan(drawable, verticalOffsetPx = 9)
            spanBuilder.setSpan(imageSpan, 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvFlag.text = spanBuilder
        } else {
            tvFlag.text = fallbackEmoji
        }
    }

    private class VerticalCenteredImageSpan(
        drawable: android.graphics.drawable.Drawable,
        private val verticalOffsetPx: Int = 3
    ) : android.text.style.ImageSpan(drawable, ALIGN_BASELINE) {
        override fun draw(
            canvas: android.graphics.Canvas,
            text: CharSequence?,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: android.graphics.Paint
        ) {
            val b = drawable
            canvas.save()
            canvas.translate(x, (y - b.bounds.height() + verticalOffsetPx).toFloat())
            b.draw(canvas)
            canvas.restore()
        }
    }

    private fun bindPlayerName(tv: TextView, name: String, seed: Int?, isLeft: Boolean) {
        val context = tv.context
        tv.text = if (seed != null) {
            if (isLeft) context.getString(R.string.player_with_seed, seed, name)
            else context.getString(R.string.player_with_seed_right, name, seed)
        } else {
            name
        }
        tv.doOnPreDraw {
            val width = tv.width - tv.paddingStart - tv.paddingEnd
            if (width <= 0) return@doOnPreDraw
            val paint = tv.paint
            val seedText = if (seed != null) {
                if (isLeft) "($seed) " else " ($seed)"
            } else ""

            val fullText = tv.text.toString()
            if (paint.measureText(fullText) <= width) {
                return@doOnPreDraw
            }

            val abbrName = abbreviateName(name)
            val abbrText = if (seed != null) {
                if (isLeft) context.getString(R.string.player_with_seed, seed, abbrName)
                else context.getString(R.string.player_with_seed_right, abbrName, seed)
            } else abbrName

            if (paint.measureText(abbrText) <= width) {
                tv.text = abbrText
                return@doOnPreDraw
            }

            val seedWidth = paint.measureText(seedText)
            val availableForName = (width - seedWidth).coerceAtLeast(0f)
            val ellipsizedName = TextUtils.ellipsize(name, paint, availableForName, TextUtils.TruncateAt.END).toString()
            val finalEllipsizedText = if (seed != null) {
                if (isLeft) context.getString(R.string.player_with_seed, seed, ellipsizedName)
                else context.getString(R.string.player_with_seed_right, ellipsizedName, seed)
            } else ellipsizedName

            tv.text = finalEllipsizedText
        }
    }

    private fun abbreviateName(name: String): String {
        val parts = name.trim().split("\\s+".toRegex())
        if (parts.size <= 1) return name
        val firstName = parts[0]
        val lastName = parts.subList(1, parts.size).joinToString(" ")
        return "${firstName.first()}. $lastName"
    }

    private object MatchDiffCallback : DiffUtil.ItemCallback<MatchUiModel>() {
        override fun areItemsTheSame(oldItem: MatchUiModel, newItem: MatchUiModel): Boolean {
            return oldItem.match.id == newItem.match.id
        }

        override fun areContentsTheSame(oldItem: MatchUiModel, newItem: MatchUiModel): Boolean {
            return oldItem == newItem
        }
    }
}
