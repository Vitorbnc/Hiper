package com.vitorbnc.hiper


/**
 * Created by vitor on 21/03/2018.
 */
interface VUICallback {
    fun onRecognitionError(errorCode: Int,errorMsg:String ="")
    fun onRecognitionResults(results: ArrayList<String>, scores:FloatArray)
    fun onUtteranceDone(utteranceId: String)

}