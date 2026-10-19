package com.onurguner.ogauto.input

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/** Standard transport keys target the current media session, if supported. */
object PhoneKeys {
    fun playPause(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    fun previous(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    fun next(context: Context) = send(context, KeyEvent.KEYCODE_MEDIA_NEXT)

    private fun send(context: Context, code: Int) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }
}
