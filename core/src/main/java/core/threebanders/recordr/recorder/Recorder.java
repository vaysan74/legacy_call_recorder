/*
 * Copyright (C) 2019 Eugen Rădulescu <synapticwebb@gmail.com> - All rights reserved.
 *
 * You may use, distribute and modify this code only under the conditions
 * stated in the SW Call Recorder license. You should have received a copy of the
 * SW Call Recorder license along with this file. If not, please write to <synapticwebb@gmail.com>.
 */

package core.threebanders.recordr.recorder;

import static android.media.MediaRecorder.AudioSource.MIC;
import static android.media.MediaRecorder.AudioSource.VOICE_CALL;
import static android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION;
import static android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION;

import android.content.Context;
import android.content.SharedPreferences;

import org.acra.ACRA;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import core.threebanders.recordr.Cache;
import core.threebanders.recordr.Core;
import core.threebanders.recordr.CrLog;
import core.threebanders.recordr.data.Recording;

@SuppressWarnings("CatchMayIgnoreException")
public class Recorder {
    private File audioFile;
    private Thread recordingThread;
    private long startingTime;
    private final String format;
    private final String mode;
    private Cache cache;
    public static final String WAV_FORMAT = "wav";
    public static final String AAC_HIGH_FORMAT = "aac_hi";
    public static final String AAC_MEDIUM_FORMAT = "aac_med";
    public static final String AAC_BASIC_FORMAT = "aac_bas";
    static final String MONO = "mono";
    private int source;
    private boolean hasError = false;
    private Context context;

    private static final String ACRA_FORMAT = "format";
    private static final String ACRA_MODE = "mode";
    private static final String ACRA_SAVE_PATH = "save_path";


    public Recorder(Context context) {
        this.context = context;
        cache = Core.getInstance().getCache();
        format = cache.format();
        mode = cache.mode();
    }

    public long getStartingTime() {
        return startingTime;
    }

    public String getAudioFilePath() {
        return audioFile.getAbsolutePath();
    }

    public void setRecordsCached(List<Recording> recordings,SharedPreferences sharedPref) {
        cache.setRecodingsList(Cache.RECODINGS_LIST, recordings,sharedPref);
    }

    public List<Recording> getRecordsCached(SharedPreferences sharedPref) {
        return cache.getRecodingsListList(sharedPref);
    }

    public void startRecording(String phoneNumber) throws RecordingException {
        if (phoneNumber == null)
            phoneNumber = "";

        if (isRunning())
            stopRecording();
        String extension = format.equals(WAV_FORMAT) ? ".wav" : ".aac";
        File recordingsDir;

        if (Core.getInstance().getCache().storage().equals("private"))
            recordingsDir = context.getFilesDir();
        else {
            String filePath = Core.getInstance().getCache().storagePath();
            recordingsDir = (filePath == null) ? context.getExternalFilesDir(null) : new File(filePath);
            if (recordingsDir == null)
                recordingsDir = context.getFilesDir();
        }

        phoneNumber = phoneNumber.replaceAll("[()/.,* ;+]", "_");
        String fileName = "Recording" + phoneNumber + new SimpleDateFormat("-d-MMM-yyyy-HH-mm-ss", Locale.US).
                format(new Date(System.currentTimeMillis())) + extension;
        audioFile = new File(recordingsDir, fileName);
        CrLog.log(CrLog.DEBUG, String.format("Recording session started. Format: %s. Mode: %s. Save path: %s",
                format, mode, audioFile.getAbsolutePath()));
        //This data is cleared in RecorderService::onDestroy().
        try {
            ACRA.getErrorReporter().putCustomData(ACRA_FORMAT, format);
            ACRA.getErrorReporter().putCustomData(ACRA_MODE, mode);
            ACRA.getErrorReporter().putCustomData(ACRA_SAVE_PATH, audioFile.getAbsolutePath());
        } catch (IllegalStateException exc) {
        }

        if (format.equals(WAV_FORMAT))
            recordingThread = new Thread(new RecordingThreadWav(context, mode, this));
        else
            recordingThread = new Thread(new RecordingThreadAac(context, audioFile, format, mode, this));

        recordingThread.start();
        startingTime = System.currentTimeMillis();
    }

    public void stopRecording() {
        if (recordingThread != null) {
            CrLog.log(CrLog.DEBUG, "Recording session ended.");
            recordingThread.interrupt();
            recordingThread = null;
            if (format.equals(WAV_FORMAT)) {
                //în cazul în care a apărut o eroare în RecordingThreadWav și fișierul temporar nu există, această
                //condiție va fi detectată în bucla try a CopyPcmToWav.run() și va fi raportată o eroare.
                Thread copyPcmToWav = new Thread(new RecordingThreadWav.CopyPcmToWav(context, audioFile, mode, this));
                copyPcmToWav.start();
            }
        }
    }

    public boolean isRunning() {
        return recordingThread != null && recordingThread.isAlive();
    }

    public String getFormat() {
        return format;
    }

    public String getMode() {
        return mode;
    }

    public String getSource() {
        switch (source) {
            case VOICE_RECOGNITION:
                return "Voice recognition";
            case VOICE_COMMUNICATION:
                return "Voice communication";
            case VOICE_CALL:
                return "Voice call";
            case MIC:
                return "Microphone";
            default:
                return "Source unrecognized";
        }
    }

    public void setSource(int source) {
        this.source = source;
    }

    public boolean hasError() {
        return hasError;
    }

    void setHasError() {
        this.hasError = true;
    }
}
