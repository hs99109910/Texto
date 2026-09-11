package com.texto.sms.extensions

import android.app.Activity
import android.view.LayoutInflater
import androidx.viewbinding.ViewBinding

/**
 * `private val binding by viewBinding(ActivityXBinding::inflate)`.
 *
 * Lazy, and deliberately not thread-safe: a binding is inflated from onCreate on the UI
 * thread, so the synchronisation a default `lazy` would take out is a lock nothing ever
 * contends. Inflating on first use rather than at construction is what lets the property sit
 * above onCreate in the file while still being built after the activity has a context.
 */
fun <T : ViewBinding> Activity.viewBinding(bindingInflater: (LayoutInflater) -> T): Lazy<T> =
    lazy(LazyThreadSafetyMode.NONE) { bindingInflater(layoutInflater) }
