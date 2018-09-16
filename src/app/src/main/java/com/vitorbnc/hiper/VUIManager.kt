package com.vitorbnc.hiper

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import java.io.IOException
import java.net.InetAddress

/**
 * Created by vitor on 21/03/2018.
 */
class VUIManager(private val context: Context ,private val callback: VUICallback,private var continuousMode:Boolean=false, private val useOfflineRecognizer:Boolean = false, private val debug:Boolean = false):android.speech.RecognitionListener,android.speech.tts.UtteranceProgressListener() {

    var recognizerIntent: Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    private var recognizer: SpeechRecognizer? = null
    private var audioManager : AudioManager? = null
    private var volume = 0
    private val normalVolume = 11
    private var isMute = false
    private var isListening = false
    private var listeningQueued = false

    private val LOG_TAG = "VUI Manager"

    private var ttsIsReady = false
    private var tts: TextToSpeech? = null

    private val mainLooper = Looper.getMainLooper()

    private var recognizerLatency:Long =0
    private val maxRecognizerLatency:Long = 2000



    init {
        initializeRecognizerIntent()
        initializeRecognizer()
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager


        tts = TextToSpeech(context,TextToSpeech.OnInitListener {status->

            if(status == TextToSpeech.SUCCESS){
                tts?.setOnUtteranceProgressListener(this)
                ttsIsReady = true
                //tts.setSpeechRate(.975f)
                Log.d(LOG_TAG,"Iniciando TTS")
                Log.d(LOG_TAG,tts?.defaultVoice.toString())
                Log.d(LOG_TAG,tts?.voices.toString())

            }
            else if (status == TextToSpeech.ERROR) ttsIsReady = false
        }
        )

    }

    fun initializeRecognizerIntent(){
        //recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH) // disable this if needed

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)//RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,useOfflineRecognizer)
    }

    fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.d(LOG_TAG,"Initializing Speech Recognizer")
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(this)
        }
        else
            Log.e(LOG_TAG,"Speech Recognition not available")

    }

    fun speak(utterance:String, id:String){
        if(ttsIsReady) {
            Log.d(LOG_TAG, "Uttering:".plus(utterance))
            if(isListening) {
                cancelRecognition()
                listeningQueued = true
            }
            unmute()
            tts?.speak(utterance, TextToSpeech.QUEUE_FLUSH, null, id)
        }
    }

    fun setOfflineRecogniton(offline: Boolean){
        stopListening()
        recognizerIntent.removeExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline)
        val handler = Handler(mainLooper)
        handler.postDelayed({startListening()},1000)
    }

    fun startListening(){
        if(continuousMode)
            mute()
        isListening = true
        listeningQueued = false
        recognizer?.startListening(recognizerIntent);
    }

    private fun startListeningDelayed(delay_ms:Long=1000){
        listeningQueued = false
        val handler = Handler(mainLooper)
        handler.postDelayed({
            startListening()
        },delay_ms)

    }

    fun mute(){
        val volumeNow = audioManager!!.getStreamVolume(AudioManager.STREAM_MUSIC)
        Log.d(LOG_TAG,"Stream volume is "+volumeNow.toString())
        if(volumeNow !=0){
            audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
            volume = volumeNow
        }
        isMute = true
        Log.d(LOG_TAG,"Muting. Volume was "+volume.toString())
    }

    fun unmute(){
        if(isMute) {
            audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
            isMute = false
            Log.d(LOG_TAG,"Restoring volume to "+volume.toString())

        }
    }

    fun forceUnmute(){
        audioManager!!.setStreamVolume(AudioManager.STREAM_MUSIC, normalVolume, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE)
        volume = normalVolume
        isMute = false
        Log.d(LOG_TAG,"Forcing volume to "+normalVolume.toString())
    }

    fun stopListening() {
        //This will stop voice input and call onResults
        unmute()
        recognizer?.stopListening()
        isListening = false
        listeningQueued = false
    }

    fun cancelRecognition() {
        //This will cancel recognition. This shouldn't call onResults (but eventually it could)
        unmute()
        recognizer?.cancel()
        isListening = false
        listeningQueued = false
    }

    fun destroy(){
        cancelRecognition()
        if (tts !=null) tts?.shutdown()
        if (recognizer!=null) recognizer?.destroy()
    }

    // RecognitionListener Interface Implementation ------------------------------------

    override fun onReadyForSpeech(var1: Bundle){
        Log.d(LOG_TAG,"onReadyForSpeech")

    }

    override fun onBeginningOfSpeech(){
        Log.d(LOG_TAG,"onBeginningOfSpeech")
    }

    override fun onRmsChanged(var1: Float){
        //Log.d(LOG_TAG,"onRmsChanged")
    }

    override fun onBufferReceived(var1: ByteArray){}

    //Called before recognition results
    override fun onEndOfSpeech(){
        Log.d(LOG_TAG,"onEndOfSpeech")
        recognizerLatency=System.currentTimeMillis()
    }

    fun recognizerRecovery(){
        recognizer?.destroy()
        initializeRecognizer()
        if(continuousMode)
        //startListeningDelayed(1000)
            startListening()
    }

    fun recognizerSoftRecovery(){
        cancelRecognition()
        if(continuousMode)
        //startListeningDelayed(1000)
            startListening()
    }

    fun getRecognizerError(error: Int):String{
            Log.v(LOG_TAG, ">>> onError : " + error);
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> return( "ERROR_AUDIO")
            SpeechRecognizer.ERROR_CLIENT -> return( "ERROR_CLIENT")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> return( "ERROR_INSUFFICIENT_PERMISSIONS")
            SpeechRecognizer.ERROR_NETWORK -> return( "ERROR_NETWORK")
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> return( "ERROR_NETWORK_TIMEOUT")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> return( "ERROR_RECOGNIZER_BUSY")
            SpeechRecognizer.ERROR_SERVER -> return( "ERROR_SERVER")
            SpeechRecognizer.ERROR_NO_MATCH -> return( "ERROR_NO_MATCH")
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> return( "ERROR_SPEECH_TIMEOUT")
            else -> return( "ERROR_UNKOWN")
        }

    }

    override fun onError(errorCode: Int){
        recognizerLatency = System.currentTimeMillis()-recognizerLatency
        if(errorCode==SpeechRecognizer.ERROR_RECOGNIZER_BUSY  || errorCode==SpeechRecognizer.ERROR_NO_MATCH) {
            recognizerSoftRecovery()
        }
        else if (errorCode==SpeechRecognizer.ERROR_SPEECH_TIMEOUT || errorCode==SpeechRecognizer.ERROR_SERVER ){
                recognizerRecovery()
        }
        callback.onRecognitionError(errorCode, getRecognizerError(errorCode))
    }

    override fun onResults(resultsBundle: Bundle){
        val confidenceScores = resultsBundle.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
        val results = resultsBundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

        recognizerLatency = System.currentTimeMillis()-recognizerLatency
        Log.d(LOG_TAG,"onResults: ".plus(results.toString()))
        Log.d(LOG_TAG,"latency(ms): "+recognizerLatency.toString())

        callback.onRecognitionResults(results,confidenceScores)

        if(continuousMode and !listeningQueued) {
            //cancelRecognition()
            startListening()
        }
    }

    override fun onPartialResults(partialResultsBundle: Bundle){
        Log.d(LOG_TAG,"onPartialResults")

    }

    override fun onEvent(var1: Int, var2: Bundle){
        Log.d(LOG_TAG,"onEvent")

    }
//---------------------------------------------------------------------------------

    //TTS UtteranceProgressListener Interface Implementation
    override fun onDone(utteranceId: String){
        Log.d(LOG_TAG,"Utterance done. ID:".plus(utteranceId))
        callback.onUtteranceDone(utteranceId)
        if(continuousMode and listeningQueued) {
            val handler = Handler(mainLooper)
            handler.post({
                startListening()
            })
        }

    }
    override fun onError(utteranceId: String){}
    override fun onStart(utteranceId: String){}
//----------------------------------------------------------------------------------

}