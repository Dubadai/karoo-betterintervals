package com.daviddokakis.karoointerval

import kotlinx.coroutines.flow.StateFlow

/**
 * A way to reach the smart trainer for ERG: over Bluetooth ([ErgTrainer]: FTMS, or FE-C tunnelled
 * over Bluetooth) or over ANT+ FE-C ([AntFecTrainer]). [ErgController] picks one from the trainer
 * paired on the Karoo ([ErgController.trainerIn]).
 */
interface TrainerLink {
    /** How the link is doing, for the ERG pill. */
    val link: StateFlow<ErgLink>
    /** The trainer's own watts and when they came, for Match PM. */
    val watts: Int?
    val wattsAtMs: Long
    /** Why the link is [ErgLink.LOST], for the log. */
    val problem: String?

    /** Find and connect to [match]; reconnects by itself until [stop]. Same trainer again: nothing. */
    fun start(match: ErgTrainer.Match)
    /** Holds [watts] as soon as the trainer is ready (only the newest counts). */
    fun setTarget(watts: Int)
    /** Lets the trainer go (rides like a flat road) but stays connected. */
    fun release()
    /** Hands back and disconnects. */
    fun stop()
}

/** How ERG reaches the trainer: Settings › Trainer › Connection. */
enum class ErgConnection {
    /** The trainer the Karoo uses, reached over Bluetooth (FTMS or FE-C over Bluetooth). */
    BLUETOOTH,
    /** The app's own ANT+ FE-C connection; the Karoo leaves the trainer's ANT+ side to it. */
    ANT;

    companion object {
        fun decode(s: String?): ErgConnection = entries.firstOrNull { it.name == s } ?: BLUETOOTH
    }
}
