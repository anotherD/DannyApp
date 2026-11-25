package com.danny.dannyapp.ui.navigation

import android.app.Activity
import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import androidx.core.content.ContextCompat
import com.danny.dannyapp.R
import com.danny.dannyapp.ui.MainActivity
import com.danny.dannyapp.ui.UIHelper
import com.danny.dannyapp.ui.career.CareerDetailActivity
import com.danny.dannyapp.ui.career.CareersActivity
import com.danny.dannyapp.ui.tournament.SeasonCalendarActivity
import com.danny.dannyapp.ui.oom.OrderOfMeritActivity
import com.danny.dannyapp.ui.profile.ProfilesActivity
import com.danny.dannyapp.ui.settings.SettingsActivity
import com.danny.dannyapp.ui.settings.SettingsPlayersActivity
import com.danny.dannyapp.ui.tournament.TournamentDetailActivity

enum class AppScreen(
    val activityClass: Class<out Activity>,
    val parent: AppScreen?,
    val titleResId: Int,
    val includeInBreadcrumb: Boolean = true,
    val showSettingsMenu: Boolean = true,
    val breadcrumbIconResId: Int? = null
) {
    HOME(MainActivity::class.java, parent = null, titleResId = R.string.app_title, includeInBreadcrumb = false),
    PROFILES(ProfilesActivity::class.java, parent = HOME, titleResId = R.string.profiles),
    CAREERS(CareersActivity::class.java, parent = HOME, titleResId = R.string.careers),
    CAREER_OVERVIEW(CareerDetailActivity::class.java, parent = CAREERS, titleResId = R.string.career_overview_header),
    SEASON_CALENDAR(SeasonCalendarActivity::class.java, parent = CAREER_OVERVIEW, titleResId = R.string.nav_calendar, includeInBreadcrumb = true, breadcrumbIconResId = R.drawable.ic_calendar),
    ORDER_OF_MERIT(OrderOfMeritActivity::class.java, parent = CAREER_OVERVIEW, titleResId = R.string.nav_oom),
    TOURNAMENT_DETAIL(TournamentDetailActivity::class.java, parent = SEASON_CALENDAR, titleResId = R.string.tournament_detail),
    SETTINGS(SettingsActivity::class.java, parent = null, titleResId = R.string.config_title, includeInBreadcrumb = false, showSettingsMenu = false),
    SETTINGS_PLAYERS(SettingsPlayersActivity::class.java, parent = SETTINGS, titleResId = R.string.config_players, showSettingsMenu = false);

    fun buildBreadcrumb(context: Context, dynamicLabels: Map<AppScreen, String> = emptyMap()): CharSequence? {
        val ancestors = mutableListOf<AppScreen>()
        var current = this.parent
        while (current != null) {
            if (current.includeInBreadcrumb) {
                ancestors.add(current)
            }
            current = current.parent
        }
        if (ancestors.isEmpty()) return null
        ancestors.reverse()

        val builder = SpannableStringBuilder()
        val density = context.resources.displayMetrics.density
        val sizePx = (14 * density).toInt()
        val tintColor = ContextCompat.getColor(context, R.color.grau_text_gedaempft)

        for ((index, screen) in ancestors.withIndex()) {
            if (index > 0) {
                builder.append(" > ")
            }
            if (screen.breadcrumbIconResId != null) {
                val start = builder.length
                builder.append(" ")
                val end = builder.length
                val drawable = ContextCompat.getDrawable(context, screen.breadcrumbIconResId)?.mutate()
                drawable?.setTint(tintColor)
                drawable?.setBounds(0, 0, sizePx, sizePx)
                if (drawable != null) {
                    val span = UIHelper.VerticalCenteredImageSpan(drawable)
                    builder.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else {
                val label = dynamicLabels[screen] ?: context.getString(screen.titleResId)
                builder.append(label)
            }
        }
        return builder
    }

    companion object {
        fun fromActivity(activity: Activity): AppScreen? =
            entries.find { it.activityClass == activity::class.java }
    }
}
