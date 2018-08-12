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

public abstract class HardwareController {
    private boolean playing;
    private OnHardwareEventListener onHardwareEvent;

    HardwareController(String name) {
    }

    abstract void connect();

    abstract void disconnect();

    abstract boolean isConnected();

    abstract void play();

    abstract void stop();

    abstract void setRecorder(CassetteRecorder recorder);

    public interface OnHardwareEventListener {
        void onPlayStateChanged(boolean state);
    }

    public boolean isPlaying() {
        return playing;
    }

    protected void setPlaying(boolean playing) {
        this.playing = playing;
        if (onHardwareEvent != null) {
            onHardwareEvent.onPlayStateChanged(playing);
        }
    }

    public void setOnHardwareEvent(OnHardwareEventListener onHardwareEvent) {
        this.onHardwareEvent = onHardwareEvent;
    }
}
