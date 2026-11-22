package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.danny.dannyapp.ui.UIHelper
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.usecase.ManualScoreResult
import com.danny.dannyapp.domain.usecase.ScoreValidationError
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Helper object providing dialogs for tournament detail views, including prize money overview,
 * user match option selection, manual score input, set winner options, and engine placeholders.
 */
object TournamentDialogs {

    /**
     * Shows a dialog displaying the total prize fund and round-by-round prize money breakdown.
     *
     * @param context Host context for building dialog.
     * @param prizeMoneyItems List of prize money items per round.
     * @param event Current tournament event metadata.
     * @param participantGroups List of entrant groups grouped by qualification type.
     * @param isParticipantsFrozen Indicates whether participant list is frozen.
     * @param openQualifierSlotsText Text describing open qualifier slots if applicable.
     */
    fun showPrizeMoneyDialog(
        context: Context,
        prizeMoneyItems: List<PrizeMoneyItem>,
        event: TournamentEvent?,
        participantGroups: List<EntrantGroupInfo> = emptyList(),
        isParticipantsFrozen: Boolean = false,
        openQualifierSlotsText: String? = null
    ) {
        if (prizeMoneyItems.isEmpty()) return

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tabLayout = TabLayout(context).apply {
            addTab(newTab().setText(context.getString(R.string.tournament_info_tab_prizes)))
            addTab(newTab().setText(context.getString(R.string.tournament_info_tab_participants)))
        }
        rootLayout.addView(tabLayout)

        val tab0Layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val totalPrize = if (event != null && event.effectiveTotalPrizeFund > 0) {
            context.getString(R.string.tournament_total_prize_fund_format, event.effectiveTotalPrizeFund.toLong())
        } else {
            context.getString(R.string.tournament_qualifying_no_prize)
        }

        val tvHeader = TextView(context).apply {
            text = totalPrize
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.rot_haupt))
            setPadding(0, 0, 0, 16)
        }
        tab0Layout.addView(tvHeader)

        if (event == TournamentEvent.EUROPEAN_TOUR) {
            val tvExceptionEt = TextView(context).apply {
                text = context.getString(R.string.tournament_info_exception_et)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
                setPadding(0, 0, 0, 8)
            }
            tab0Layout.addView(tvExceptionEt)
        } else if (event in listOf(TournamentEvent.PLAYERS_CHAMPIONSHIP, TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN)) {
            val tvExceptionPcUk = TextView(context).apply {
                text = context.getString(R.string.tournament_info_exception_pc_uk)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
                setPadding(0, 0, 0, 8)
            }
            tab0Layout.addView(tvExceptionPcUk)
        }

        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PrizeMoneyAdapter().apply { submitList(prizeMoneyItems) }
        }
        tab0Layout.addView(rv)
        rootLayout.addView(tab0Layout)

        val tab1ScrollView = ScrollView(context).apply {
            visibility = View.GONE
        }
        val tab1Layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        if (event != null) {
            val tvRules = TextView(context).apply {
                text = context.getString(getQualificationRulesResId(event))
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                val typedValue = TypedValue()
                context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
                if (typedValue.resourceId != 0) {
                    setTextColor(ContextCompat.getColor(context, typedValue.resourceId))
                }
                setPadding(0, 0, 0, 16)
            }
            tab1Layout.addView(tvRules)
        }

        if (!isParticipantsFrozen) {
            val density = context.resources.displayMetrics.density
            val tvPreliminaryHint = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (16 * density).toInt()
                }
                text = context.getString(R.string.tournament_info_participants_preliminary)
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(context, R.color.grau_text_hell))
                background = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(context, R.color.warning_banner_bg))
                    setStroke((1 * density).toInt(), ContextCompat.getColor(context, R.color.warning_banner_stroke))
                    cornerRadius = 8 * density
                }
                setPadding(24, 16, 24, 16)
            }
            tab1Layout.addView(tvPreliminaryHint)
        }

        val participantsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        TournamentDetailUiHelper.renderOpenDrawEntrants(
            context = context,
            container = participantsContainer,
            groups = participantGroups,
            isPreliminary = !isParticipantsFrozen
        )
        tab1Layout.addView(participantsContainer)

        if (!openQualifierSlotsText.isNullOrEmpty()) {
            val divider = MaterialDivider(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (12 * context.resources.displayMetrics.density).toInt()
                    bottomMargin = (12 * context.resources.displayMetrics.density).toInt()
                }
            }
            tab1Layout.addView(divider)

            val tvOpenSlots = TextView(context).apply {
                text = openQualifierSlotsText
                textSize = 13f
                setTypeface(null, Typeface.ITALIC)
                setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
            }
            tab1Layout.addView(tvOpenSlots)
        }

        tab1ScrollView.addView(tab1Layout)
        rootLayout.addView(tab1ScrollView)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    tab0Layout.visibility = View.GONE
                    tab1ScrollView.visibility = View.VISIBLE
                } else {
                    tab0Layout.visibility = View.VISIBLE
                    tab1ScrollView.visibility = View.GONE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.tournament_preview_prizes_title))
            .setView(rootLayout)
            .setPositiveButton(context.getString(R.string.dialog_close), null)
            .show()
    }

    private fun getQualificationRulesResId(event: TournamentEvent): Int {
        return when (event) {
            TournamentEvent.PLAYERS_CHAMPIONSHIP -> R.string.tournament_rules_players_championship
            TournamentEvent.EUROPEAN_TOUR -> R.string.tournament_rules_european_tour
            TournamentEvent.UK_OPEN -> R.string.tournament_rules_uk_open
            TournamentEvent.EUROPEAN_OPEN -> R.string.tournament_rules_european_open
            TournamentEvent.WORLD_MATCHPLAY -> R.string.tournament_rules_world_matchplay
            TournamentEvent.WORLD_GRAND_PRIX -> R.string.tournament_rules_world_grand_prix
            TournamentEvent.EUROPEAN_CHAMPIONSHIP -> R.string.tournament_rules_european_championship
            TournamentEvent.GRAND_SLAM_OF_DARTS -> R.string.tournament_rules_grand_slam
            TournamentEvent.PLAYERS_CHAMPIONSHIP_FINALS -> R.string.tournament_rules_players_championship_finals
            TournamentEvent.WORLD_CHAMPIONSHIP -> R.string.tournament_rules_world_championship
            TournamentEvent.QUALIFIER_ET -> R.string.tournament_rules_qualifier_et
            TournamentEvent.QUALIFIER_GRAND_SLAM -> R.string.tournament_rules_qualifier_grand_slam
            TournamentEvent.QUALIFIER_WM -> R.string.tournament_rules_qualifier_wm
        }
    }

    /**
     * Shows a selection dialog for user match actions (Set Winner, Manual Score, Live Game).
     *
     * @param context Host context.
     * @param matchModel Active match model.
     * @param debugMode Flag indicating if debug options (Set Winner) are enabled.
     * @param onSetWinner Callback when set winner option is chosen.
     * @param onManualScore Callback when manual score option is chosen.
     * @param onLiveGame Callback when live game engine option is chosen.
     */
    fun showUserMatchOptionsDialog(
        context: Context,
        matchModel: MatchUiModel,
        debugMode: Boolean,
        onSetWinner: (MatchUiModel) -> Unit,
        onManualScore: (MatchUiModel) -> Unit,
        onLiveGame: () -> Unit
    ) {
        val setWinnerOption = context.getString(R.string.user_match_option_set_winner)
        val manualScoreOption = context.getString(R.string.user_match_option_manual_score)
        val liveGameOption = context.getString(R.string.user_match_option_live_game)

        val options = if (debugMode) {
            arrayOf(setWinnerOption, manualScoreOption, liveGameOption)
        } else {
            arrayOf(manualScoreOption, liveGameOption)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.user_match_control_title))
            .setItems(options) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    setWinnerOption -> onSetWinner(matchModel)
                    manualScoreOption -> onManualScore(matchModel)
                    liveGameOption -> onLiveGame()
                }
            }
            .setNegativeButton(context.getString(R.string.dialog_cancel), null)
            .show()
    }

    /**
     * Shows a quick-selection dialog to set win or loss for the user match.
     *
     * @param context Host context.
     * @param matchModel Selected match.
     * @param onSetWinner Callback receiving match ID and Boolean flag (true = win, false = loss).
     */
    fun showSetWinnerDialog(
        context: Context,
        matchModel: MatchUiModel,
        onSetWinner: (matchId: Long, userWins: Boolean) -> Unit
    ) {
        val choices = arrayOf(
            context.getString(R.string.user_match_choice_win),
            context.getString(R.string.user_match_choice_loss)
        )

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.user_match_set_winner_title))
            .setItems(choices) { _, which ->
                val userWins = (which == 0)
                onSetWinner(matchModel.match.id, userWins)
            }
            .setNegativeButton(context.getString(R.string.dialog_cancel), null)
            .show()
    }

    /**
     * Shows a manual score entry dialog with score validation and error handling.
     *
     * @param context Host context.
     * @param matchModel Target match model.
     * @param coroutineScope Coroutine scope for running validation and DB update.
     * @param onSaveScore Suspending lambda to save score, returning [ManualScoreResult].
     */
    fun showManualScoreInputDialog(
        context: Context,
        matchModel: MatchUiModel,
        coroutineScope: CoroutineScope,
        onSaveScore: suspend (matchId: Long, scoreP1: Int, scoreP2: Int) -> ManualScoreResult
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }

        val tvInfo = TextView(context).apply {
            text = context.getString(
                R.string.user_match_manual_info_format,
                matchModel.player1.name,
                matchModel.player2.name,
                matchModel.roundDisplayName
            )
            setPadding(0, 0, 0, 20)
            textSize = 14f
        }

        val etP1 = EditText(context).apply {
            hint = context.getString(R.string.user_match_player_score_hint, matchModel.player1.name)
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val etP2 = EditText(context).apply {
            hint = context.getString(R.string.user_match_player_score_hint, matchModel.player2.name)
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(tvInfo)
        layout.addView(etP1)
        layout.addView(etP2)

        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.user_match_option_manual_score))
            .setView(layout)
            .setPositiveButton(context.getString(R.string.dialog_save), null)
            .setNegativeButton(context.getString(R.string.dialog_cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p1 = etP1.text.toString().toIntOrNull() ?: -1
                val p2 = etP2.text.toString().toIntOrNull() ?: -1
                if (p1 < 0 || p2 < 0) {
                    UIHelper.showSnackbar(dialog.window!!.decorView, context.getString(R.string.user_match_invalid_numbers), isError = true, duration = Snackbar.LENGTH_LONG)
                    return@setOnClickListener
                }
                coroutineScope.launch {
                    try {
                        when (val result = onSaveScore(matchModel.match.id, p1, p2)) {
                            is ManualScoreResult.ValidationError -> {
                                val errorMessage = when (val error = result.error) {
                                    is ScoreValidationError.NegativeScore -> context.getString(R.string.score_validation_negative)
                                    is ScoreValidationError.DrawNotAllowed -> context.getString(R.string.score_validation_draw)
                                    is ScoreValidationError.InvalidTwoClearLegs -> context.getString(
                                        R.string.score_validation_two_clear_legs,
                                        error.targetLegs,
                                        error.tiebreakWinnerLegs,
                                        error.tiebreakLoserLegs
                                    )
                                    is ScoreValidationError.InvalidBestOfLegs -> context.getString(
                                        R.string.score_validation_best_of_legs,
                                        error.bestOf,
                                        error.targetLegs
                                    )
                                    is ScoreValidationError.InvalidBestOfSets -> context.getString(
                                        R.string.score_validation_best_of_sets,
                                        error.bestOf,
                                        error.targetSets
                                    )
                                }
                                UIHelper.showSnackbar(dialog.window!!.decorView, errorMessage, isError = true, duration = Snackbar.LENGTH_LONG)
                            }
                            is ManualScoreResult.Success -> {
                                dialog.dismiss()
                            }
                        }
                    } catch (e: Exception) {
                        UIHelper.showSnackbar(dialog.window!!.decorView, e.message ?: context.getString(R.string.error_unknown), isError = true, duration = Snackbar.LENGTH_LONG)
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * Shows a placeholder dialog for live game engine integration.
     *
     * @param context Host context.
     */
    fun showLiveGameEnginePlaceholder(context: Context) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.user_match_option_live_game))
            .setMessage(context.getString(R.string.user_match_live_placeholder))
            .setPositiveButton(context.getString(R.string.dialog_ok), null)
            .show()
    }
}
