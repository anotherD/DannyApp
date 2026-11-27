package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.danny.dannyapp.ui.UIHelper
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.MatchDistancePreset
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.usecase.ManualScoreResult
import com.danny.dannyapp.domain.usecase.ScoreValidationError
import com.danny.dannyapp.ui.model.EntrantGroupInfo
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
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
        openQualifierSlotsText: String? = null,
        matchDistancePreset: MatchDistancePreset = MatchDistancePreset.DEFAULT
    ) {
        if (prizeMoneyItems.isEmpty()) return

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_tournament_info, null)
        val tabLayout = dialogView.findViewById<TabLayout>(R.id.tabLayoutTournamentInfo)
        val layoutTabPrizes = dialogView.findViewById<LinearLayout>(R.id.layoutInfoTabPrizes)
        val tvHeader = dialogView.findViewById<TextView>(R.id.tvInfoPrizeHeader)
        val tvMatchDistance = dialogView.findViewById<TextView>(R.id.tvInfoMatchDistance)
        val tvException = dialogView.findViewById<TextView>(R.id.tvInfoException)
        val rvPrizeMoney = dialogView.findViewById<RecyclerView>(R.id.rvInfoPrizeMoney)

        val scrollTabParticipants = dialogView.findViewById<ScrollView>(R.id.scrollInfoTabParticipants)
        val tvRules = dialogView.findViewById<TextView>(R.id.tvInfoRules)
        val tvPreliminaryHint = dialogView.findViewById<TextView>(R.id.tvInfoPreliminaryHint)
        val layoutParticipantsContainer = dialogView.findViewById<LinearLayout>(R.id.layoutInfoParticipantsContainer)
        val dividerOpenSlots = dialogView.findViewById<View>(R.id.dividerInfoOpenSlots)
        val tvOpenSlots = dialogView.findViewById<TextView>(R.id.tvInfoOpenSlots)

        val totalPrize = if (event != null && event.effectiveTotalPrizeFund > 0) {
            context.getString(R.string.tournament_total_prize_fund_format, event.effectiveTotalPrizeFund.toLong())
        } else {
            context.getString(R.string.tournament_qualifying_no_prize)
        }
        tvHeader.text = totalPrize
        tvMatchDistance.text = context.getString(R.string.tournament_info_match_distance_format, context.getString(matchDistancePreset.labelResId))

        when {
            event == TournamentEvent.EUROPEAN_TOUR -> {
                tvException.visibility = View.VISIBLE
                tvException.text = context.getString(R.string.tournament_info_exception_et)
            }
            event in listOf(TournamentEvent.PLAYERS_CHAMPIONSHIP, TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN) -> {
                tvException.visibility = View.VISIBLE
                tvException.text = context.getString(R.string.tournament_info_exception_pc_uk)
            }
            else -> {
                tvException.visibility = View.GONE
            }
        }

        rvPrizeMoney.adapter = PrizeMoneyAdapter().apply { submitList(prizeMoneyItems) }
        UIHelper.attachVerticalEdgeFade(rvPrizeMoney)

        UIHelper.attachVerticalEdgeFade(scrollTabParticipants)

        if (event != null) {
            tvRules.visibility = View.VISIBLE
            tvRules.text = context.getString(getQualificationRulesResId(event))
        } else {
            tvRules.visibility = View.GONE
        }

        tvPreliminaryHint.visibility = if (!isParticipantsFrozen) View.VISIBLE else View.GONE

        TournamentDetailUiHelper.renderOpenDrawEntrants(
            context = context,
            container = layoutParticipantsContainer,
            groups = participantGroups,
            isPreliminary = !isParticipantsFrozen
        )

        if (!openQualifierSlotsText.isNullOrEmpty()) {
            dividerOpenSlots.visibility = View.VISIBLE
            tvOpenSlots.visibility = View.VISIBLE
            tvOpenSlots.text = openQualifierSlotsText
        } else {
            dividerOpenSlots.visibility = View.GONE
            tvOpenSlots.visibility = View.GONE
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 1) {
                    layoutTabPrizes.visibility = View.GONE
                    scrollTabParticipants.visibility = View.VISIBLE
                } else {
                    layoutTabPrizes.visibility = View.VISIBLE
                    scrollTabParticipants.visibility = View.GONE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.tournament_preview_prizes_title))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.dialog_close), null)
            .show()
    }

    private fun getQualificationRulesResId(event: TournamentEvent): Int {
        return when (event) {
            TournamentEvent.WORLD_MASTERS -> R.string.tournament_rules_world_masters
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
            TournamentEvent.QUALIFIER_WORLD_MASTERS -> R.string.tournament_rules_qualifier_world_masters
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

        MaterialAlertDialogBuilder(context)
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

        MaterialAlertDialogBuilder(context)
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
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_manual_score, null)
        val tvInfo = dialogView.findViewById<TextView>(R.id.tvManualScoreInfo)
        val tilScoreP1 = dialogView.findViewById<TextInputLayout>(R.id.tilScoreP1)
        val etScoreP1 = dialogView.findViewById<TextInputEditText>(R.id.etScoreP1)
        val tilScoreP2 = dialogView.findViewById<TextInputLayout>(R.id.tilScoreP2)
        val etScoreP2 = dialogView.findViewById<TextInputEditText>(R.id.etScoreP2)

        tvInfo.text = context.getString(
            R.string.user_match_manual_info_format,
            matchModel.player1.name,
            matchModel.player2.name,
            matchModel.roundDisplayName
        )
        tilScoreP1.hint = context.getString(R.string.user_match_player_score_hint, matchModel.player1.name)
        tilScoreP2.hint = context.getString(R.string.user_match_player_score_hint, matchModel.player2.name)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.user_match_option_manual_score))
            .setView(dialogView)
            .setPositiveButton(context.getString(R.string.dialog_save), null)
            .setNegativeButton(context.getString(R.string.dialog_cancel), null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
                val p1 = etScoreP1.text.toString().toIntOrNull() ?: -1
                val p2 = etScoreP2.text.toString().toIntOrNull() ?: -1
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
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.user_match_option_live_game))
            .setMessage(context.getString(R.string.user_match_live_placeholder))
            .setPositiveButton(context.getString(R.string.dialog_ok), null)
            .show()
    }
}
