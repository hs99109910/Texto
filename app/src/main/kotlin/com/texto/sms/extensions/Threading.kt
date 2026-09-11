package com.texto.sms.extensions

import android.os.Looper

/** True when the caller is on the UI thread. */
fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()

/**
 * Runs [callback] off the UI thread, and inline when it is already off it.
 *
 * The inline half is the part that matters: this is called from code that is sometimes
 * already on a worker (a receiver, a Room query's own thread) and sometimes not, and
 * unconditionally spawning would put a second thread behind work that was going to be
 * sequential anyway. It is also why callers can rely on the work being *done* by the time
 * the call returns when they were already in the background.
 *
 * A bare Thread rather than a pool, matching what this replaces: the callers are one-shot
 * and long-ish -- provider sweeps, message inserts -- so there is nothing for a pool to
 * amortise, and a pool would need a lifecycle none of them has.
 */
fun ensureBackgroundThread(callback: () -> Unit) {
    if (isOnMainThread()) {
        Thread { callback() }.start()
    } else {
        callback()
    }
}
