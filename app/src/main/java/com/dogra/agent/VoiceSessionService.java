package com.dogra.agent;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.*;
import android.speech.*;
import android.speech.tts.*;
import java.util.*;

/** A visible, user-started mic session. No boot receiver or hidden wake-word listener. */
public class VoiceSessionService extends Service {
    static VoiceSessionService instance;
    private static final String CHANNEL = "voice_session";
    private static final int NOTIFICATION = 101;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech speech;
    private ActionExecutor executor;
    private boolean destroyed, ttsReady, handsFree, paused, processing;
    private int errors, recognizerGeneration, speechGeneration;
    private long handsFreeUntil;
    private final Runnable nextListen = this::listenAgain;
    private final Runnable sessionLimit = () -> pause("5 minute poore hue. Dobara shuru karne ke liye mic dabayein.");
    private final Runnable recognitionTimeout = () -> pause("Mic ka jawab nahi aaya. Mic dobara dabayein ya command type karein.");
    private final Runnable speechTimeout = this::finishSpeaking;
    private final BroadcastReceiver lockReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            pause("Phone lock hua. Unlock karke floating mic dabayein.");
            if (AgentAccessibilityService.instance != null) AgentAccessibilityService.instance.hideNumbers();
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        executor = new ActionExecutor(this);
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Agent microphone session", NotificationManager.IMPORTANCE_LOW));
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(lockReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED);
        else registerReceiver(lockReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        speech = new TextToSpeech(this, status -> handler.post(() -> {
            if (destroyed || speech == null || status != TextToSpeech.SUCCESS) return;
            ttsReady = configureVoice();
        }));
        speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { completeSpeech(id); }
            @Override public void onError(String id) { completeSpeech(id); }
        });
    }

    private void completeSpeech(String id) {
        handler.post(() -> {
            if (!destroyed && id.equals(Integer.toString(speechGeneration))) finishSpeaking();
        });
    }

    private boolean configureVoice() {
        String language = prefs().getString("language", "hi-IN");
        Locale locale = Locale.forLanguageTag(language);
        int result = speech.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return false;
        Set<Voice> voices = speech.getVoices();
        if (voices != null) for (Voice voice : voices) {
            if (voice.getLocale().getLanguage().equals(locale.getLanguage()) && !voice.isNetworkConnectionRequired()) {
                speech.setVoice(voice);
                return true;
            }
        }
        // Feedback is optional. Never send screen text to a network TTS voice.
        return false;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            SessionState.update("Agent app kholkar microphone permission dein.");
            stopSelf(); return START_NOT_STICKY;
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            else startForeground(NOTIFICATION, notification());
        } catch (RuntimeException error) {
            SessionState.update("Mic session start nahi hua. Agent app saamne kholkar dobara Start dabayein.");
            stopSelf(); return START_NOT_STICKY;
        }
        SessionState.active = true;
        handsFree = prefs().getBoolean("hands_free", false);
        SessionState.update(AgentAccessibilityService.instance == null
            ? "Mic ready. Floating mic ke liye Enable screen control karein."
            : "Floating mic ready. Kisi app mein mic dabakar command boliye.");
        if (intent != null && "listen".equals(intent.getAction())) listen();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, VoiceSessionService.class).setAction("stop"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_mic)
            .setContentTitle("Agent voice control is on")
            .setContentText("Tap floating mic to speak. Stop ends the session.")
            .setContentIntent(open).setOngoing(true).setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(new Notification.Action.Builder(null, "Stop", stop).build()).build();
    }

    private SharedPreferences prefs() { return getSharedPreferences("agent", MODE_PRIVATE); }

    void listen() {
        if (destroyed || !SessionState.active) return;
        AgentAccessibilityService access = AgentAccessibilityService.instance;
        if (access != null && access.hasConfirmation()) {
            SessionState.update("Pehle screen par Confirm ya Cancel dabayein."); return;
        }
        pauseInternal();
        paused = false;
        errors = 0;
        handsFree = prefs().getBoolean("hands_free", false);
        handsFreeUntil = SystemClock.elapsedRealtime() + 5 * 60_000L;
        if (handsFree) handler.postDelayed(sessionLimit, 5 * 60_000L);
        startListening();
    }

    private void startListening() {
        if (destroyed || paused || !SessionState.active || processing) return;
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (keyguard != null && keyguard.isKeyguardLocked()) { pause("Pehle phone unlock karein."); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { pause("Microphone permission chahiye. Agent app mein allow karein."); return; }
        destroyRecognizer();
        final int token = recognizerGeneration;
        boolean offline = prefs().getBoolean("offline", false);
        try {
            if (offline) {
                if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
                    pause("On-device speech is phone par available nahi hai. Command type karein, ya On-device speech only off karein."); return;
                }
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            } else {
                if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                    pause("Speech service nahi mili. Phone ki Google/Speech service enable karein. Tab tak command type karein."); return;
                }
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            }
            recognizer.setRecognitionListener(new RecognitionListener() {
                private boolean current() { return !destroyed && token == recognizerGeneration; }
                @Override public void onReadyForSpeech(Bundle params) { if (current()) SessionState.update("Sun raha hoon… command boliye."); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rms) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { if (current()) SessionState.update("Command samajh raha hoon…"); }
                @Override public void onEvent(int type, Bundle params) { }
                @Override public void onPartialResults(Bundle data) { }
                @Override public void onError(int code) {
                    if (!current()) return;
                    handler.removeCallbacks(recognitionTimeout);
                    SessionState.listening = false;
                    destroyRecognizer();
                    errors++;
                    boolean silence = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                    if (silence && handsFree && errors < 3 && !paused) {
                        SessionState.update("Awaaz nahi mili. Dobara boliye."); scheduleNext(1000);
                    } else pause(errorText(code));
                }
                @Override public void onResults(Bundle data) {
                    if (!current()) return;
                    handler.removeCallbacks(recognitionTimeout);
                    SessionState.listening = false;
                    ArrayList<String> alternatives = data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    destroyRecognizer();
                    if (alternatives == null || alternatives.isEmpty()) { pause("Command nahi suna. Mic dabakar dobara boliye."); return; }
                    errors = 0;
                    execute(alternatives.get(0));
                }
            });
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs().getString("language", "hi-IN"))
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            SessionState.listening = true;
            SessionState.update("Mic shuru ho raha hai…");
            handler.postDelayed(recognitionTimeout, 25_000);
            recognizer.startListening(request);
        } catch (RuntimeException error) {
            pause("Speech start nahi hua. Mic permission/language check karein; command type bhi kar sakte hain.");
        }
    }

    private String errorText(int code) {
        return switch (code) {
            case SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Awaaz saaf nahi mili. Mic dabakar dobara boliye.";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission dein, phir dobara mic dabayein.";
            case SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "Speech provider ko internet chahiye. Network check karein ya on-device speech use karein.";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Chuni hui language ka speech model nahi mila. Phone ki speech settings mein Hindi download karein ya English chunein.";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Mic busy hai. Dusri voice app band karke dobara dabayein.";
            default -> "Speech error " + code + ". Mic dobara dabayein ya command type karein.";
        };
    }

    void runCommand(String text) {
        pauseInternal();
        paused = false;
        // Typed commands never start listening automatically.
        handsFree = false;
        execute(text);
    }

    private void execute(String text) {
        if (destroyed) return;
        processing = true;
        SessionState.record(text);
        SessionState.update("Kar raha hoon…");
        executor.execute(text, message -> {
            if (destroyed) return;
            processing = false;
            SessionState.update(message);
            speak(message);
        });
    }

    private void speak(String message) {
        if (destroyed || paused) return;
        speechGeneration++;
        if (!ttsReady || speech == null) { scheduleNext(900); return; }
        if (speech.speak(message, TextToSpeech.QUEUE_FLUSH, null, Integer.toString(speechGeneration)) == TextToSpeech.ERROR) {
            scheduleNext(900); return;
        }
        handler.removeCallbacks(speechTimeout);
        handler.postDelayed(speechTimeout, 30_000);
    }

    private void finishSpeaking() {
        handler.removeCallbacks(speechTimeout);
        if (speech != null) speech.stop();
        scheduleNext(650);
    }

    private void scheduleNext(long delay) {
        handler.removeCallbacks(nextListen);
        if (!destroyed && handsFree && !paused) handler.postDelayed(nextListen, delay);
    }

    private void listenAgain() {
        if (SystemClock.elapsedRealtime() >= handsFreeUntil) { sessionLimit.run(); return; }
        if (handsFree && !paused) startListening();
    }

    void setHandsFree(boolean enabled) {
        handsFree = enabled;
        handler.removeCallbacks(nextListen);
        handler.removeCallbacks(sessionLimit);
        if (enabled) {
            handsFreeUntil = SystemClock.elapsedRealtime() + 5 * 60_000L;
            handler.postDelayed(sessionLimit, 5 * 60_000L);
        }
    }

    void resetRecognizer() { pause("Speech setting badli. Mic dabakar dobara boliye."); if (speech != null) ttsReady = configureVoice(); }
    void pause(String message) { pauseInternal(); SessionState.update(message); }

    void pauseForConfirmation() {
        paused = true;
        handler.removeCallbacks(nextListen);
        handler.removeCallbacks(sessionLimit);
        handler.removeCallbacks(recognitionTimeout);
        handler.removeCallbacks(speechTimeout);
        destroyRecognizer();
        SessionState.listening = false;
        if (speech != null) speech.stop();
        SessionState.update("Screen par action confirm karein.");
    }

    private void pauseInternal() {
        paused = true;
        processing = false;
        handler.removeCallbacks(nextListen);
        handler.removeCallbacks(sessionLimit);
        handler.removeCallbacks(recognitionTimeout);
        handler.removeCallbacks(speechTimeout);
        speechGeneration++;
        if (executor != null) executor.cancel();
        if (speech != null) speech.stop();
        destroyRecognizer();
        SessionState.listening = false;
    }

    private void destroyRecognizer() {
        recognizerGeneration++;
        if (recognizer != null) {
            SpeechRecognizer old = recognizer; recognizer = null;
            try { old.cancel(); old.destroy(); } catch (RuntimeException ignored) { }
        }
    }

    @Override public void onDestroy() {
        destroyed = true;
        pauseInternal();
        try { unregisterReceiver(lockReceiver); } catch (IllegalArgumentException ignored) { }
        if (speech != null) { speech.shutdown(); speech = null; }
        if (instance == this) instance = null;
        SessionState.active = false;
        SessionState.update("Session band hai. Mic dabakar phir shuru karein.");
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
