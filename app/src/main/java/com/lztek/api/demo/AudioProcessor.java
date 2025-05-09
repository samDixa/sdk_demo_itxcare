
package com.lztek.api.demo;

import android.util.Log;

import org.jtransforms.fft.DoubleFFT_1D;

public class AudioProcessor {
    private static final int SAMPLE_RATE = 6000;

    public static double[] convertToDoubleArray(byte[] audioData) {
        int length = Math.min(audioData.length / 2, 128);
        double[] doubleData = new double[length];
        for (int i = 0; i < length; i++) {
            int sample = (audioData[i * 2] & 0xFF) | (audioData[i * 2 + 1] << 8);
            doubleData[i] = sample / 32768.0;
        }
        return doubleData;
    }

    public static byte[] convertToByteArray(double[] audioData) {
        byte[] byteData = new byte[audioData.length * 2];
        for (int i = 0; i < audioData.length; i++) {
            double value = Math.max(-1.0, Math.min(1.0, audioData[i]));
            short sample = (short) (value * 32767);
            byteData[i * 2] = (byte) (sample & 0xFF);
            byteData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return byteData;
    }

    public static double[] applyFFT(double[] audioData) {
        int n = Math.min(audioData.length, 128);
        DoubleFFT_1D fft = new DoubleFFT_1D(n);
        double[] transformed = new double[n * 2];
        System.arraycopy(audioData, 0, transformed, 0, n);
        fft.realForwardFull(transformed);
        return transformed;
    }

    public static double[] applyMedicalBandPassFilter(double[] fftData) {
        int n = fftData.length / 2;
        double frequencyResolution = SAMPLE_RATE / (double) fftData.length;
        double[] heartbeatBand = new double[fftData.length];
        System.arraycopy(fftData, 0, heartbeatBand, 0, fftData.length);
        for (int i = 0; i < n; i++) {
            double freq = i * frequencyResolution;
            if (freq < 20 || freq > 250) {
                heartbeatBand[2 * i] = 0;
                heartbeatBand[2 * i + 1] = 0;
            }
        }
        return heartbeatBand;
    }

    public static boolean isValidMedicalSignal(double[] filteredData) {
        int count = 0;
        for (double value : filteredData) {
            if (Math.abs(value) > 0.003) {
                count++;
            }
        }
        boolean isValid = count > filteredData.length * 0.03;
        Log.d("AudioProcessor", "Valid signal count: " + count + ", IsValid: " + isValid);
        return isValid;
    }

    public static double[] applyInverseFFT(double[] fftData) {
        int n = fftData.length / 2;
        DoubleFFT_1D fft = new DoubleFFT_1D(n);
        fft.realInverseFull(fftData, true);
        double[] timeData = new double[n];
        System.arraycopy(fftData, 0, timeData, 0, n);
        return timeData;
    }

    public static boolean containsNormalSounds(double[] fftData) {
        int n = fftData.length / 2;
        double frequencyResolution = SAMPLE_RATE / (double) fftData.length;
        double normalSoundEnergy = 0;
        double medicalSoundEnergy = 0;
        double totalEnergy = 0;
        for (int i = 0; i < n; i++) {
            double freq = i * frequencyResolution;
            double energy = fftData[2 * i] * fftData[2 * i] + fftData[2 * i + 1] * fftData[2 * i + 1];
            totalEnergy += energy;
            if (freq > 1000 && freq < 4000) {
                normalSoundEnergy += energy;
            }
            if (freq >= 20 && freq <= 250) {
                medicalSoundEnergy += energy;
            }
        }
        double normalSoundRatio = totalEnergy > 0 ? normalSoundEnergy / totalEnergy : 0;
        double medicalSoundRatio = totalEnergy > 0 ? medicalSoundEnergy / totalEnergy : 0;
        boolean hasNormalSounds = normalSoundRatio > 0.6 && medicalSoundRatio < 0.15;
        return hasNormalSounds;
    }
}