package com.eldenbingo.android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.eldenbingo.android.R
import com.eldenbingo.android.data.model.GameSound

class AndroidSoundPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val soundIds = mapOf(
        GameSound.SquareClaimedOther to soundPool.load(appContext, R.raw.square_claimed_other, 1),
        GameSound.SquareClaimedOwn to soundPool.load(appContext, R.raw.square_claimed_own, 1),
        GameSound.SquareUnclaimedOther to soundPool.load(appContext, R.raw.square_unclaimed_other, 1),
        GameSound.SquareUnclaimedOwn to soundPool.load(appContext, R.raw.square_unclaimed_own, 1),
        GameSound.Bingo to soundPool.load(appContext, R.raw.bingo, 1),
        GameSound.SquareSniped to soundPool.load(appContext, R.raw.square_sniped, 1)
    )

    fun play(sound: GameSound) {
        val soundId = soundIds[sound] ?: return
        soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
    }
}
