package com.vitorbnc.hiper

import android.Manifest
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import android.content.pm.PackageManager
import android.content.Context
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.support.v4.app.ActivityCompat
import android.util.Log
import android.widget.Toast
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.*
import org.json.JSONObject
import java.nio.charset.Charset

/*
The app listens to user speech in continuous mode, and publishes what it understood to mqtt topic in json format: {stt:user-speech}
It will also speak incoming json messages from mqtt subscribed topic that follow this format: {tts:sentence-to-be-spoken}
 */

class MainActivity : VUICallback, IMqttActionListener, MqttCallback, AppCompatActivity() {

    private val LOG_TAG = "Hiper"
    private lateinit var vui : VUIManager

    private lateinit var  wakeLock:PowerManager.WakeLock

    private var appId = ""

    // Requesting permission to RECORD_AUDIO
    private var permissionToRecordAccepted = false
    private val permissions = arrayOf<String>(Manifest.permission.RECORD_AUDIO)
    private val REQUEST_RECORD_AUDIO_PERMISSION = 200

    private var volume = 0

    private lateinit var tts: TextToSpeech
    private var ttsIsReady = false

    private var isListening = false

    private var serverAddress = ""//"tcp://192.168.1.12"
    private var serverPort = "1883"

    private var pubSubTopic = ""

    private var mqttAndroidClient: MqttAndroidClient? = null
    private val mqttConnectOptions = MqttConnectOptions()

    private val handler = Handler()

    private var connectionSwitch = false


    //incremental utterance id to be used on absence of something better
    private var uttId = 0

    override fun onDestroy() {
        Log.d(LOG_TAG,"onDestroy")
        savePrefs()
        vui.destroy()
        super.onDestroy()
        if(wakeLock.isHeld)
            wakeLock.release()
    }

    fun loadPrefs(){
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        val addr = sharedPref.getString(getString(R.string.key_remote_address),txtRemoteIP.text.toString())
        val port = sharedPref.getString(getString(R.string.key_remote_port),txtRemotePort.text.toString())
        val topic = sharedPref.getString(getString(R.string.key_topic),txtTopic.text.toString())

        swCloudRecogtion.isChecked = sharedPref.getBoolean(getString(R.string.key_cloud_recognition),swCloudRecogtion.isChecked)
        txtRemoteIP.setText(addr)
        txtRemotePort.setText(port)
        txtTopic.setText(topic)
    }

    fun savePrefs(){
        val sharedPref = this.getPreferences(Context.MODE_PRIVATE)
        with(sharedPref!!.edit()){
            putString(getString(R.string.key_remote_address),txtRemoteIP.text.toString())
            putString(getString(R.string.key_remote_port),txtRemotePort.text.toString())
            putString(getString(R.string.key_topic),txtTopic.text.toString())
            putBoolean(getString(R.string.key_cloud_recognition),swCloudRecogtion.isChecked)
            commit()
        }
    }

    fun talkNShow(text:String){
        txtHiper.text= text.capitalize()
        vui.speak(text, (uttId++).toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,LOG_TAG)

        setContentView(R.layout.activity_main)
        loadPrefs()
        appId = Settings.Secure.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID)
        Log.d(LOG_TAG,"ANDROID_ID: "+appId)

        connectionSwitch = false

        //Without RECORD_AUDIO permission, recognizer will throw error 9

        //Enable this only on API 23 or higer
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

        wakeLock.acquire()

        val initRecogOffline = !swCloudRecogtion.isChecked
        vui = VUIManager(this,this,true,initRecogOffline)

        mqttConnectOptions.isAutomaticReconnect=true
        mqttConnectOptions.isCleanSession = true

        swCloudRecogtion.setOnCheckedChangeListener{ _, isChecked->
            vui.setOfflineRecogniton(!isChecked)
        }

        handler.postDelayed({
            connect()
            startStopListening(this.btnListen)
        },2000)

    }

    fun toggleConnection(view: View){
        connectionSwitch = !connectionSwitch
        if(connectionSwitch)
            connect()
        else if(mqttAndroidClient!=null) {
            if (mqttAndroidClient!!.isConnected)
                mqttAndroidClient?.disconnect()
        }
    }

    fun connect(){
        serverAddress = txtRemoteIP.text.toString()
        serverPort = txtRemotePort.text.toString()
        val uri = serverAddress.plus(":").plus(serverPort)

        pubSubTopic = txtTopic.text.toString()

        mqttAndroidClient = MqttAndroidClient(applicationContext,uri,"android_app")
        mqttAndroidClient!!.setCallback(this)
        mqttAndroidClient!!.connect(mqttConnectOptions,this)
    }


    override fun messageArrived(topic: String?, message: MqttMessage?) {
        if(message!=null) {
            val payload:String = message?.payload.toString(Charset.defaultCharset())

            Log.d(LOG_TAG, "Incoming message: " + payload)

            val json = JSONObject(payload)

            if(!json.has("appId"))
                return
            else{
                val targetId = json.getString("appId")
                if(targetId!=appId){
                    Log.d(LOG_TAG,"Wrong appId")
                    return
                }
            }

            if(json.has("tts")) {
                val utterance = json.getString("tts")
                talkNShow(utterance)

            }
            if(json.has("app")){
                val cmd = json.getString("app")
                if(cmd=="forceUnmute") {
                    vui.forceUnmute()
                    talkNShow("Aumentando volume")
                }

            }
        }
    }

    override fun connectionLost(cause: Throwable?) {
        Log.d(LOG_TAG,"MQTT Connection lost. " +cause.toString())
        Toast.makeText(this,"Conexão MQTT Perdida",Toast.LENGTH_SHORT).show()
        btnConnection.text = getString(R.string.icon_disconnected)
        connectionSwitch = false
    }

    override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        Log.d(LOG_TAG,"Failed to initiate MQTT connection. " +exception!!.message.toString())
        Toast.makeText(this,"Falha ao conectar por MQTT"+ exception!!.message.toString(),Toast.LENGTH_SHORT).show()
        connectionSwitch = false
        btnConnection.text = getString(R.string.icon_disconnected)

    }

    override fun onSuccess(asyncActionToken: IMqttToken?) {
        Log.d(LOG_TAG,"MQTT Connection successful! ")
        Toast.makeText(this,"MQTT Conectado",Toast.LENGTH_SHORT).show()
        connectionSwitch = true
        btnConnection.text = getString(R.string.icon_connected)
        subscribe(pubSubTopic)
        //publish("hey guys, what's up?")
    }

    override fun deliveryComplete(token: IMqttDeliveryToken?) {}

    private fun publishEncoded(value: String,key:String = "stt", topic: String = pubSubTopic){
        val msg="{\"%s\":\"%s\",\"appId\":\"%s\"}".format(key,value,appId)
        Log.d("LOG_TAG", "Sending : %s".format(msg))
        publish(msg)

    }

    private fun publish(payload:String, topic:String=pubSubTopic) {
        if(mqttAndroidClient==null)
            return
        if(mqttAndroidClient!!.isConnected()) {
            val message = MqttMessage()
            message.payload = payload.toByteArray()
            mqttAndroidClient?.publish(topic, message)
        }
    }


    private fun subscribe(topic: String,qos:Int =0){
        if(mqttAndroidClient==null)
            handler.postDelayed ({mqttAndroidClient?.subscribe(topic,qos)},2000)
        else if (mqttAndroidClient!!.isConnected)
            mqttAndroidClient?.subscribe(topic,qos)
    }


    fun greet(){
        handler.postDelayed({
            vui.speak("Olá, usuário!","greet")
        },1000)

    }

    fun sendStop(view: View){
     publishEncoded("stop","cmd")
    }

    fun startStopListening(view: View){
        if(!isListening) {
            vui.startListening();
            isListening = true
            btnListen.text = getString(R.string.stop_listening)
            txtHiper.text = "Pode falar"
        }
        else{
            vui.cancelRecognition()
            isListening = false
            btnListen.text = getString(R.string.start_listening)
            //vui.speak("Já chega né","end")
        }
    }


    override fun onRecognitionError(errorCode: Int,errorMsg:String) {
        var txt = "Erro no reconhecimento. Código %d".format(errorCode)
        if(errorCode==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
            txt = "Por favor, abra as configurações do dispositivo e permita que o app grave áudio."
        else if(errorCode ==SpeechRecognizer.ERROR_NO_MATCH || errorCode==SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                errorCode==SpeechRecognizer.ERROR_SERVER || errorCode ==SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
            txt = "Fala, que eu te escuto."
            //vui.startListening()
        }
        else
            txt = "Erro: "+ errorMsg

        txtHiper.text= txt.capitalize()

    }

    override fun onRecognitionResults(results: ArrayList<String>, scores: FloatArray) {
        //tts.speak("Seu nome é "+ recognitionResults.get(0),TextToSpeech.QUEUE_FLUSH,null,"1")
        var topResult = results[0] // top result
        var txt: String = topResult

        /* //Uncomment this to show more results
        txt=""
        for (i in 0..results.size - 1) {
            if(i>1)
                break
            txt += results.get(i) + " " + scores.get(i) + "\n"

        }
        */
        txtUser.text = txt.capitalize()

        /*var json =  JSONObject()
        json.put("stt",topResult)
        var msg = json.toString()
        Log.d(LOG_TAG,msg)
        */
        publishEncoded(topResult)

        if (topResult.contains("Rotina",true) and topResult.contains("Teste",true)) {
            Log.d(LOG_TAG, "speaking")

            val hello = "O programa está funcionando corretamente!"
            talkNShow(hello)
        }
        else if(topResult.contains("sai",true) and (!topResult.contains("não,true")) and (topResult.contains("aplicativo",true) or topResult.contains("app",true))){
            talkNShow( "Adeus...")
            Log.d(LOG_TAG, "Closing app")
            finish() //System.exit(0) //Using system.exit will not call onDestroy and isn't recommended
        }
    }

    override fun onUtteranceDone(utteranceId: String){

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO_PERMISSION -> permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
        if (!permissionToRecordAccepted) finish()

    }



}
