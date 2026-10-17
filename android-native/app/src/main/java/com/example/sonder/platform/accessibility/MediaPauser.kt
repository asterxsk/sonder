package com.example.sonder.platform.accessibility

/**
 * "Stop whatever is playing."
 *
 * The blocker is an opaque overlay, which covers the picture but not the sound — and on a
 * short-form surface it does not even cover the *playback*: the clip behind the wall keeps
 * advancing, so a reel the user was watching when the gate went up as good as plays itself
 * out behind the blocker and the app is still making noise at them from a screen that is
 * supposed to be shut.
 *
 * Implemented by [SonderAccessibilityService] by dispatching a media key to the focused
 * window, which is the app's own window: the overlay is deliberately not focusable, so the
 * pause lands on the app the user was watching.
 *
 * **The key is `KEYCODE_MEDIA_PLAY_PAUSE`, a toggle, so this does not mean "pause" — it means
 * "flip".** A session that is already paused *starts* when this is called, which is the
 * opposite of the name, and it is not a hypothetical: the gate dispatches this when the blocker
 * goes up and the player behind it stays paused until something moves it. That is why the
 * caller must dispatch it an odd number of times per episode and why no caller is entitled to
 * call it "to be sure" — see [SonderAccessibilityService.dispatchPauseIfFresh] for the debounce
 * that exists for exactly that reason. An app with no media session still ignores it.
 *
 * Nullable everywhere it is used, like every other capability here: with no connected service
 * there is nothing to dispatch with.
 */
fun interface MediaPauser {

    /**
     * Ask the app in front to pause.
     *
     * Takes no package name, and that is not an omission: a media key goes to whoever holds
     * the session, and the caller has already established that the app it is covering is the
     * one on screen. Naming a package here would imply a targeting the platform does not
     * offer, and a caller relying on it would be relying on nothing.
     */
    fun pauseMedia()
}
