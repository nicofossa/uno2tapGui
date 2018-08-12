/*
  This program is free software: you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program.  If not, see <http://www.gnu.org/licenses/>.

  Based on original work by Mike Dawson (https://gp2x.org/uno2tap/)
  */
package it.nicofossa;

import java.io.*;
import java.util.Arrays;

import static java.lang.Math.abs;

public class Tape {
    private int tap_size;
    private int tap_pos;
    private byte[] tap_buf;

    private OnTapeEventListener onTapeEventListener;
    private int oldPos;

    public Tape(File file) {
        if (!file.exists()) throw new IllegalArgumentException("File does not exist.");
        try {
            read_tap(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid file!", e);
        }
        tap_pos = 0x14;

        onTapeEventListener = null;
    }


    public void rewind() {
        tap_pos = 0x14;
        updateListener();
    }

    public void setTime(double time) {
        tap_pos = time2pos(time);
        if (onTapeEventListener != null) onTapeEventListener.onPosUpdated(tap_pos, tap_size);
    }

    public double getTime() {
        return pos2time(tap_pos);
    }

    public void setPos(int pos) {
        tap_pos = pos;
        updateListener();
    }

    private void updateListener() {
        if (onTapeEventListener != null && abs(tap_pos - oldPos) > 1000) {
            onTapeEventListener.onPosUpdated(tap_pos, tap_size);
            oldPos = tap_pos;
        }
    }

    public int getPos() {
        return tap_pos;
    }

    public int getSize() {
        return tap_size;
    }


    public void close() {

    }

    private void read_tap(File tapfile) throws IOException {
        tap_size = (int) tapfile.length();
        tap_buf = new byte[tap_size];

        InputStream input = new BufferedInputStream(new FileInputStream(tapfile));
        int totalBytesRead = 0;
        while (totalBytesRead < tap_size) {
            int bytesRemaining = tap_size - totalBytesRead;
            int bytesRead = input.read(tap_buf, totalBytesRead, bytesRemaining);
            if (bytesRead > 0) {
                totalBytesRead = totalBytesRead + bytesRead;
            }
        }
        input.close();
    }


    private double pos2time(int pos) {
        double time = 0.0;
        int xpos;
        for (xpos = 0x14; xpos < pos; xpos++) {
            if (xpos >= tap_size) break;
            int tap_data = (int) tap_buf[xpos];
            if (tap_data != 0) {
                time += (tap_data * 8) / 985248.0;
            } else {
                int d1 = tap_buf[xpos + 1];
                int d2 = tap_buf[xpos + 2];
                int d3 = tap_buf[xpos + 3];
                time += ((d3 * 256 * 256) + (d2 * 256) + d1) / 985248.0;
                xpos += 3;
            }
        }
        return time;
    }

    // return tap position (including header) for given time
    private int time2pos(double time) {
        double xtime = 0.0;
        int xpos;
        for (xpos = 0x14; xpos < tap_size; xpos++) {
            if (xtime >= time) break;
            int tap_data = (int) tap_buf[xpos];
            if (tap_data != 0) {
                xtime += (tap_data * 8) / 985248.0;
            } else {
                int d1 = tap_buf[xpos + 1];
                int d2 = tap_buf[xpos + 2];
                int d3 = tap_buf[xpos + 3];
                xtime += ((d3 * 256 * 256) + (d2 * 256) + d1) / 985248.0;
                xpos += 3;
            }
        }
        return xpos;
    }


    byte[] getNByte(int howMany) {
        byte[] buffer = Arrays.copyOfRange(tap_buf, tap_pos, tap_pos + howMany);

        tap_pos += howMany;

        updateListener();
        return buffer;
    }

    public void setOnTapeEventListener(OnTapeEventListener onTapeEventListener) {
        this.onTapeEventListener = onTapeEventListener;
    }

    public interface OnTapeEventListener {
        public void onPosUpdated(int pos, int lenght);
    }

}
