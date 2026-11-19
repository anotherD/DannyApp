package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import com.danny.dannyapp.ui.model.MatchUiModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
     */
    fun showPrizeMoneyDialog(
        context: Context,
        prizeMoneyItems: List<PrizeMoneyItem>,
        event: TournamentEvent?,
        participantCount: Int = 0
    ) {
        if (prizeMoneyItems.isEmpty()) return
        val totalPrize = if (event != null && event.effectiveTotalPrizeFund > 0) {
            context.getString(R.string.tournament_total_prize_fund_format, event.effectiveTotalPrizeFund.toLong())
        } else {
            context.getString(R.string.tournament_qualifying_no_prize)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val tvHeader = TextView(context).apply {
            text = totalPrize
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(context, R.color.rot_haupt))
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvHeader)

        if (participantCount > 0) {
            val tvParticipants = TextView(context).apply {
                text = context.getString(R.string.tournament_info_participants_count, participantCount)
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.grau_text_gedaempft))
                setPadding(0, 0, 0, 8)
            }
            layout.addView(tvParticipants)
        }

        if (event == TournamentEvent.EUROPEAN_TOUR) {
            val tvExceptionEt = TextView(context).apply {
                text = context.getString(R.string.tournament_info_exception_et)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
                setPadding(0, 0, 0, 8)
            }
            layout.addView(tvExceptionEt)
        } else if (event in listOf(TournamentEvent.PLAYERS_CHAMPIONSHIP, TournamentEvent.UK_OPEN, TournamentEvent.EUROPEAN_OPEN)) {
            val tvExceptionPcUk = TextView(context).apply {
                text = context.getString(R.string.tournament_info_exception_pc_uk)
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
                setPadding(0, 0, 0, 8)
            }
            layout.addView(tvExceptionPcUk)
        }

        val rv = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PrizeMoneyAdapter().apply { submitList(prizeMoneyItems) }
        }
        layout.addView(rv)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.tournament_preview_prizes_title))
            .setView(layout)
            .setPositiveButton(context.getString(R.string.dialog_close), null)
            .show()
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
     * @param onSaveScore Suspending lambda to save score, returning error string if invalid or null if successful.
     */
    fun showManualScoreInputDialog(
        context: Context,
        matchModel: MatchUiModel,
        coroutineScope: CoroutineScope,
        onSaveScore: suspend (matchId: Long, scoreP1: Int, scoreP2: Int) -> String?
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
                    Toast.makeText(context, context.getString(R.string.user_match_invalid_numbers), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                coroutineScope.launch {
                    val error = onSaveScore(matchModel.match.id, p1, p2)
                    if (error != null) {
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                    } else {
                        dialog.dismiss()
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
