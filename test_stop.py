import paho.mqtt.client as mqtt
import json

mqtt_broker_host = '127.0.0.1' #"127.0.0.1" 
mqtt_broker_port = 1883
mqtt_keepalive_secs = 60

subTopic = 'hiper'
pubTopic = 'hiper'

def publish(msg,topic=pubTopic):
	mqttc.publish(topic,msg,qos=0,retain=False)

#handle commands
def handle_cmd(cmd,appId):
	if cmd=='stop':
		response = {"tts":"Comando de parada recebido!","appId":appId}
		#generate json-fortmatted str from python dict
		j = json.dumps(response)
		publish(j)

#handle speech to text user input
def handle_stt(txt,appId):
	pass

#mqtt callback, called upon mqtt message arrival
def on_message(mqttc, obj, msg):
	#msg is binary, we'll decode it with accent support 
	payload=msg.payload.decode('utf-8')
	print(payload)
	#Important! Forgetting quotes will raise JSONDecodeError: Expecting value
	j = json.loads(payload)

	if not "appId" in j:
		return
	appId = j["appId"]

	if "cmd" in j:
		cmd = j["cmd"]
		handle_cmd(cmd,appId)

	if "stt" in j:
		stt = j["stt"]
		handle_stt(stt,appId)



if __name__=='__main__':
	mqttc = mqtt.Client()

	mqttc.on_message = on_message

	mqttc.connect(mqtt_broker_host, mqtt_broker_port, mqtt_keepalive_secs)

	mqttc.subscribe(subTopic, 0)

	mqttc.loop_forever()
