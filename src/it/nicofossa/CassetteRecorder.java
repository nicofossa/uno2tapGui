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

import java.io.File;

public class CassetteRecorder implements HardwareController.OnHardwareEventListener, Tape.OnTapeEventListener {
    private HardwareController controller;
    private Tape tape;
    private State state;

    private CasseteRecorderChangedListener casseteRecorderChangedListener;

    public CassetteRecorder(HardwareController controller) {
        this.controller = controller;
        controller.setOnHardwareEvent(this);
        controller.connect();
        state = State.EJECTED;
    }

    public void setTape(File tape) {
        if (state != State.EJECTED) {
            Log.write("Tried to put an other cassette in but old cassette not ejected!");
            return;
        }

        this.tape = new Tape(tape);
        this.tape.setOnTapeEventListener(this);
        state = State.STOPPED;
        updateState();
    }

    public void ejectTape() {
        if (state == State.EJECTED) {
            Log.write("A cassette must be inserted and must be stopped in order to eject it");
            return;
        }
        tape.close();
        tape = null;
        state = State.EJECTED;
        updateState();
    }

    public void dispose() {
        if (state == State.PLAYING) stop();
        while (state == State.PLAYING) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (state == State.STOPPED) ejectTape();
        updateState();
        controller.disconnect();
    }

    public void play() {
        if (state == State.STOPPED) {
            controller.play();
        }else{
            Log.write("A cassette must be inserted and must be stopped in order to play it!");
            return;
        }

    }

    public void stop() {
        if (state == State.PLAYING) {
            controller.stop();
        }else{
            Log.write("A cassette must be inserted and must be playing in order to be stopped!");
        }

    }

    private void updateState() {
        if (casseteRecorderChangedListener != null) {
            casseteRecorderChangedListener.onCassetteRecorderStateChanged(state);
        }
    }

    public void rewind() {
        if (state == State.EJECTED){
            Log.write("A cassette must be inserted in order to rewind it!");
            return;
        }
        if (state == State.PLAYING) stop();
        tape.rewind();
    }

    public void setTime(double time) {
        if (state == State.EJECTED) throw new IllegalStateException("No cassette inserted!");
        tape.setTime(time);
    }

    public double getPos() {
        if (state == State.EJECTED) throw new IllegalStateException("No cassette inserted!");
        return tape.getPos();
    }

    public Tape getTape() {
        return tape;
    }

    @Override
    public void onPlayStateChanged(boolean playing) {
        if (playing) {
            state = State.PLAYING;
        } else {
            state = State.STOPPED;
        }
        updateState();
    }

    @Override
    public void onPosUpdated(int pos, int lenght) {
        if (casseteRecorderChangedListener != null) {
            casseteRecorderChangedListener.onPosUpdated(pos, lenght);
        }
    }

    public enum State {STOPPED, PLAYING, RECORDING, EJECTED}

    public State getState() {
        return state;
    }

    public interface CasseteRecorderChangedListener {
        void onCassetteRecorderStateChanged(State state);
        void onPosUpdated(int pos, int lenght);
    }

    public void setCasseteRecorderChangedListener(CasseteRecorderChangedListener casseteRecorderChangedListener) {
        this.casseteRecorderChangedListener = casseteRecorderChangedListener;
    }
}
