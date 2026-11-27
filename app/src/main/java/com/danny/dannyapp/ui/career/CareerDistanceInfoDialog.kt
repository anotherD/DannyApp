package com.danny.dannyapp.ui.career

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
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
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val density = context.resources.displayMetrics.density
        val padH = context.resources.getDimensionPixelSize(R.dimen.info_dialog_padding_h)
        val padV = context.resources.getDimensionPixelSize(R.dimen.info_dialog_padding_v)

        val tvDescription = TextView(context).apply {
            text = context.getString(R.string.match_distance_info_description)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.info_dialog_body_size))
            setPadding(padH, padV, padH, padV)
            val typedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            if (typedValue.resourceId != 0) {
                setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
            }
        }
        rootLayout.addView(tvDescription)

        val tabLayout = TabLayout(context).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootLayout.addView(tabLayout)

        val viewPager = ViewPager2(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.info_dialog_pager_height)
            ).apply {
                topMargin = (8 * density).toInt()
            }
        }
        rootLayout.addView(viewPager)

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
            .setView(rootLayout)
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
                val tvCategory = TextView(context).apply {
                    text = context.getString(category.titleResId)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.info_dialog_header_size))
                    setTypeface(null, Typeface.BOLD)
                    val typedValue = TypedValue()
                    context.theme.resolveAttribute(android.R.attr.colorPrimary, typedValue, true)
                    if (typedValue.resourceId != 0) {
                        setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
                    }
                    setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
                }
                contentLayout.addView(tvCategory)

                val roundSequence = category.event.getRoundSequence()
                roundSequence.forEach { round ->
                    val rowLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, (2 * density).toInt(), 0, (2 * density).toInt())
                    }

                    val tvRoundName = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        text = TournamentUiHelper.getDisplayRoundName(round.name, context)
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.info_dialog_sub_size))
                        val typedValue = TypedValue()
                        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                        if (typedValue.resourceId != 0) {
                            setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
                        }
                    }

                    val distance = category.event.getMatchDistance(round, preset)
                    val distanceText = TournamentDetailMapper.formatMatchDistance(context, distance)

                    val tvDistance = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        text = distanceText
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.info_dialog_sub_size))
                        setTypeface(null, Typeface.BOLD)
                        val typedValue = TypedValue()
                        context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
                        if (typedValue.resourceId != 0) {
                            setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
                        }
                    }

                    rowLayout.addView(tvRoundName)
                    rowLayout.addView(tvDistance)
                    contentLayout.addView(rowLayout)
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
