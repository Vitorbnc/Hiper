package com.vitorbnc.hiper

import android.speech.SpeechRecognizer
import android.util.Log

class tmp internal constructor() {
    init {
        val TAG = ""
        val error = 0
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> Log.e(TAG, "ERROR_AUDIO")
            SpeechRecognizer.ERROR_CLIENT -> Log.e(TAG, "ERROR_CLIENT")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Log.e(TAG, "ERROR_INSUFFICIENT_PERMISSIONS")
            SpeechRecognizer.ERROR_NETWORK -> Log.e(TAG, "ERROR_NETWORK")
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Log.e(TAG, "ERROR_NETWORK_TIMEOUT")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Log.e(TAG, "ERROR_RECOGNIZER_BUSY")
            SpeechRecognizer.ERROR_SERVER -> Log.e(TAG, "ERROR_SERVER")
            SpeechRecognizer.ERROR_NO_MATCH -> Log.v(TAG, "ERROR_NO_MATCH")
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Log.v(TAG, "ERROR_SPEECH_TIMEOUT")
            else -> Log.v(TAG, "ERROR_UNKOWN")
        }
    }
}
