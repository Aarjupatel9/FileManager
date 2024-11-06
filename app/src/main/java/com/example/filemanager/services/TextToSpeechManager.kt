package com.example.filemanager.services

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import android.speech.tts.TextToSpeech.SUCCESS
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale

class TextToSpeechManager(private val context: Context, private val text: String) : OnInitListener {

    private lateinit var textToSpeech: TextToSpeech

    init {
        textToSpeech = TextToSpeech(context, this)
        textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onDone(utteranceId: String?) {
                Log.d("TextToSpeechManager", "speeck complited")
                destroy()
            }

            override fun onError(utteranceId: String?) {
                // Called when an error occurred while speaking text
                Log.d("TextToSpeechManager", "Error occurred while speaking text: $text")
                destroy()
            }

            override fun onStart(utteranceId: String?) {
                Log.d("TextToSpeechManager", "speeck started")
                // Called when speaking of text starts
            }
        })
    }

    private fun speak() {
        val params = Bundle()
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utteranceId")

        val result = textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, "utteranceId")
        if (result != SUCCESS) {
//            destroy()
            Log.e("TextToSpeechManager", "Failed to speak text: $text")
        } else {
//            destroy()
            Log.d("TextToSpeechManager", "success to speak text: $text")
        }
    }

    override fun onInit(status: Int) {
        if (status == SUCCESS) {
            Log.d("TextToSpeechManager", "Text-to-Speech initialization success")

            val availableLanguages = textToSpeech.availableLanguages
            val availableVoices = textToSpeech.voices

//            for (tmpVoice in availableVoices) {
//                Log.d("TextToSpeechManager", "availableVoices name: ${tmpVoice.features} , local: ${tmpVoice.locale}")
//            }

            if (availableLanguages.contains(Locale.US) && availableVoices.any { it.locale == Locale.US }) {
                val locale = Locale.US // Change to your preferred locale
                val voice = Voice(
                    "MyVoiceName",           // String name: A unique identifier for the voice
                    Locale.US,               // Locale locale: The locale of the voice (e.g., Locale.US for English)
                    Voice.QUALITY_HIGH,      // int quality: The quality of the voice (e.g., Voice.QUALITY_HIGH)
                    Voice.LATENCY_NORMAL,    // int latency: The latency of the voice (e.g., Voice.LATENCY_NORMAL)
                    true,                    // boolean requiresNetworkConnection: Whether the voice requires a network connection
                    null                     // Set<String> features: Additional features of the voice (can be null)
                )

                textToSpeech.voice = voice
                textToSpeech.language = locale
            }

            textToSpeech.setSpeechRate(1.0f) // Normal speech rate
            textToSpeech.setPitch(1.0f) // Normal pitch

            speak()
        } else {
            Log.e("TextToSpeechManager", "Text-to-Speech initialization failed")
        }
    }

    fun destroy() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}
