package com.rubenrups.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** What the UI shows: the newest snapshot (kept when a later poll fails, so staleness shows) plus the last error. */
data class GlucoseState(
    val snapshot: GlucoseSnapshot? = null,
    val error: String? = null,
    val loading: Boolean = false,
)

class GlucoseStore {
    private val client = GlucoseClient()
    private val _state = MutableStateFlow(GlucoseState())
    val state: StateFlow<GlucoseState> = _state.asStateFlow()

    /** The login that LibreLinkUp refused; it is not tried again until the user changes it. */
    private var rejectedLogin: String? = null

    suspend fun refresh(email: String, password: String) {
        val login = "$email\n$password"
        if (login == rejectedLogin) return
        _state.value = _state.value.copy(loading = true)
        val result = withContext(Dispatchers.IO) { runCatching { client.fetch(email, password) } }
        _state.value = result.fold(
            onSuccess = { GlucoseState(snapshot = it) },
            onFailure = {
                if (it is GlucoseAuthException) rejectedLogin = login
                val message = (it as? GlucoseException)?.message ?: "Can't reach LibreLinkUp (${it.javaClass.simpleName})."
                GlucoseState(snapshot = _state.value.snapshot, error = message)
            },
        )
    }

    fun clear() {
        rejectedLogin = null
        _state.value = GlucoseState()
    }
}
