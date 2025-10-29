package github.oftx.smsforwarder.ui

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A simple event bus using SharedFlow to handle activity recreation events.
 * This replaces the deprecated LocalBroadcastManager.
 */
object RecreateHandler {
    private val _recreateEvent = MutableSharedFlow<Unit>()
    val recreateEvent = _recreateEvent.asSharedFlow()

    suspend fun triggerRecreate() {
        _recreateEvent.emit(Unit)
    }
}
