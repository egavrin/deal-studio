package com.offlineassistant.app.speech

import androidx.lifecycle.ViewModel

/** Keeps the speech gateway aligned with the Activity-scoped ChatViewModel lifetime. */
class AssistantSpeechRuntimeViewModel : ViewModel() {
    val gateway = AssistantSpeechGateway()

    override fun onCleared() {
        gateway.close()
    }
}
