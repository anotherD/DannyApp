package com.danny.dannyapp.ui.config

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.ui.BaseActivity
import kotlinx.coroutines.launch

class ConfigPlayersActivity : BaseActivity() {

    private lateinit var playerRepository: PlayerRepository
    private lateinit var playerAdapter: ConfigPlayerAdapter
    private lateinit var rvPlayers: RecyclerView
    private lateinit var tvEmptyPlayers: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_players)
        setupToolbar(
            titleText = getString(R.string.config_players),
            subtitleText = getString(R.string.config_title),
            showBack = true,
            rootViewId = R.id.playersMain,
        )

        playerRepository = PlayerRepository(this)

        rvPlayers = findViewById(R.id.rvPlayers)
        tvEmptyPlayers = findViewById(R.id.tvEmptyPlayers)

        rvPlayers.layoutManager = LinearLayoutManager(this)
        playerAdapter = ConfigPlayerAdapter()
        rvPlayers.adapter = playerAdapter

        observePlayers()
    }

    private fun observePlayers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerRepository.allPlayers.collect { players ->
                    playerAdapter.submitList(players)
                    if (players.isEmpty()) {
                        rvPlayers.visibility = RecyclerView.GONE
                        tvEmptyPlayers.visibility = TextView.VISIBLE
                    } else {
                        rvPlayers.visibility = RecyclerView.VISIBLE
                        tvEmptyPlayers.visibility = TextView.GONE
                    }
                }
            }
        }
    }
}
