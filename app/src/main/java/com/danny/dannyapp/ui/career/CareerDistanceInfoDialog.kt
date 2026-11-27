package com.danny.dannyapp.ui.career

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.MatchDistancePreset
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.tournament.TournamentDetailMapper
import com.danny.dannyapp.ui.tournament.TournamentUiHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Dialog displaying an overview of match distance rules across all tournament categories
 * for each [MatchDistancePreset] using a [TabLayout] and [ViewPager2].
 */
object CareerDistanceInfoDialog {

    private data class TournamentCategoryInfo(
        val titleResId: Int,
        val event: TournamentEvent
    )

    /**
     * Displays the match distance information dialog with the specified initial preset selected.
     */
    fun show(context: Context, initialPreset: MatchDistancePreset = MatchDistancePreset.ORIGINAL) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_distance_info, null)
        val tabLayout = dialogView.findViewById<TabLayout>(R.id.tabLayoutDistancePresets)
        val viewPager = dialogView.findViewById<ViewPager2>(R.id.viewPagerDistancePresets)

        val presets = MatchDistancePreset.entries
        val adapter = CareerDistancePresetPagerAdapter(context, presets)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = context.getString(presets[position].labelResId)
        }.attach()

        val initialIndex = presets.indexOf(initialPreset).coerceAtLeast(0)
        viewPager.setCurrentItem(initialIndex, false)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.match_distance_info_title))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.dialog_close), null)
            .show()
    }

    private class CareerDistancePresetPagerAdapter(
        private val context: Context,
        private val presets: List<MatchDistancePreset>
    ) : RecyclerView.Adapter<CareerDistancePresetPagerAdapter.PresetViewHolder>() {

        private var sharedScrollY: Int = 0
        private val activeScrollViews = mutableSetOf<ScrollView>()
        private var isSyncingScroll = false

        private val categories = listOf(
            TournamentCategoryInfo(R.string.career_distance_category_world_masters, TournamentEvent.WORLD_MASTERS),
            TournamentCategoryInfo(R.string.career_distance_category_players_championship, TournamentEvent.PLAYERS_CHAMPIONSHIP),
            TournamentCategoryInfo(R.string.career_distance_category_european_tour, TournamentEvent.EUROPEAN_TOUR),
            TournamentCategoryInfo(R.string.career_distance_category_qualifier, TournamentEvent.QUALIFIER_ET),
            TournamentCategoryInfo(R.string.career_distance_category_uk_open_european_open, TournamentEvent.UK_OPEN),
            TournamentCategoryInfo(R.string.career_distance_category_world_matchplay, TournamentEvent.WORLD_MATCHPLAY),
            TournamentCategoryInfo(R.string.career_distance_category_world_grand_prix, TournamentEvent.WORLD_GRAND_PRIX),
            TournamentCategoryInfo(R.string.career_distance_category_euro_championship_pc_finals, TournamentEvent.EUROPEAN_CHAMPIONSHIP),
            TournamentCategoryInfo(R.string.career_distance_category_grand_slam, TournamentEvent.GRAND_SLAM_OF_DARTS),
            TournamentCategoryInfo(R.string.career_distance_category_world_championship, TournamentEvent.WORLD_CHAMPIONSHIP)
        )

        class PresetViewHolder(val scrollView: ScrollView) : RecyclerView.ViewHolder(scrollView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PresetViewHolder {
            val scrollView = ScrollView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isFillViewport = true
                setOnScrollChangeListener { sourceView, _, scrollY, _, _ ->
                    if (!isSyncingScroll) {
                        sharedScrollY = scrollY
                        isSyncingScroll = true
                        try {
                            activeScrollViews.forEach { otherView ->
                                if (otherView !== sourceView) {
                                    otherView.scrollTo(0, scrollY)
                                }
                            }
                        } finally {
                            isSyncingScroll = false
                        }
                    }
                }
            }
            UIHelper.attachVerticalEdgeFade(scrollView)
            return PresetViewHolder(scrollView)
        }

        override fun onBindViewHolder(holder: PresetViewHolder, position: Int) {
            activeScrollViews.add(holder.scrollView)
            val preset = presets[position]
            val density = context.resources.displayMetrics.density
            val padH = context.resources.getDimensionPixelSize(R.dimen.info_dialog_padding_h)
            val padV = context.resources.getDimensionPixelSize(R.dimen.info_dialog_padding_v)
            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padH, padV, padH, padV)
            }

            categories.forEach { category ->
                val categoryView = LayoutInflater.from(context).inflate(R.layout.item_distance_category, contentLayout, false)
                val tvCategory = categoryView.findViewById<TextView>(R.id.tvDistanceCategoryTitle)
                tvCategory.text = context.getString(category.titleResId)
                contentLayout.addView(categoryView)

                val roundSequence = category.event.getRoundSequence()
                roundSequence.forEach { round ->
                    val rowView = LayoutInflater.from(context).inflate(R.layout.item_distance_row, contentLayout, false)
                    val tvRoundName = rowView.findViewById<TextView>(R.id.tvDistanceRoundName)
                    val tvDistance = rowView.findViewById<TextView>(R.id.tvDistanceValue)

                    tvRoundName.text = TournamentUiHelper.getDisplayRoundName(round.name, context)
                    val distance = category.event.getMatchDistance(round, preset)
                    tvDistance.text = TournamentDetailMapper.formatMatchDistance(context, distance)

                    contentLayout.addView(rowView)
                }
            }
            holder.scrollView.removeAllViews()
            holder.scrollView.addView(contentLayout)

            holder.scrollView.post {
                isSyncingScroll = true
                try {
                    holder.scrollView.scrollTo(0, sharedScrollY)
                } finally {
                    isSyncingScroll = false
                }
            }
        }

        override fun onViewRecycled(holder: PresetViewHolder) {
            super.onViewRecycled(holder)
            activeScrollViews.remove(holder.scrollView)
        }

        override fun getItemCount(): Int = presets.size
    }
}
