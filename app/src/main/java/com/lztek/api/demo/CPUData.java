package com.lztek.api.demo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class CPUData {
    public int header, Battery_percentage, charging_status, s_CPU, VITAL, SPK, LIGHT, backlight, ONE, TWO, vol_D, vol_up, MIC_D, MIC_up, FAN_on;

    public CPUData(byte[] bytes) {
        if (bytes.length < 60) { // Each int = 4 bytes, total 15 fields = 60 bytes
            throw new IllegalArgumentException("Invalid byte array size: " + bytes.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.LITTLE_ENDIAN); // C# Struct Uses Little Endian

        this.header = buffer.getInt();
        this.Battery_percentage = buffer.getInt();
        this.charging_status = buffer.getInt();
        this.s_CPU = buffer.getInt();
        this.VITAL = buffer.getInt();
        this.SPK = buffer.getInt();
        this.LIGHT = buffer.getInt();
        this.backlight = buffer.getInt();
        this.ONE = buffer.getInt();
        this.TWO = buffer.getInt();
        this.vol_D = buffer.getInt();
        this.vol_up = buffer.getInt();
        this.MIC_D = buffer.getInt();
        this.MIC_up = buffer.getInt();
        this.FAN_on = buffer.getInt();
    }

    @Override
    public String toString() {
        return "CPUData {" +
                "header=" + header +
                ", Battery%=" + Battery_percentage +
                ", charging=" + charging_status +
                ", CPU=" + s_CPU +
                ", VITAL=" + VITAL +
                ", SPK=" + SPK +
                ", LIGHT=" + LIGHT +
                ", backlight=" + backlight +
                ", ONE=" + ONE +
                ", TWO=" + TWO +
                ", vol_D=" + vol_D +
                ", vol_up=" + vol_up +
                ", MIC_D=" + MIC_D +
                ", MIC_up=" + MIC_up +
                ", FAN_on=" + FAN_on +
                '}';
    }
}
