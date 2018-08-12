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

import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SerialHardwareController extends HardwareController {
    private static final int TIME_OUT = 2000;
    private static final int DATA_RATE = 115200;
    private static final int CMD_NOP = 'Z';
    private static final int CMD_PING = 'P';
    private static final int CMD_PLAY = 'R';
    private static final int CMD_RECORD = 'W';
    private static final int CMD_STOP = 'r';
    private static final int CMD_SENSE_ON = 'S';
    private static final int CMD_SENSE_OFF = 's';
    private static final int buf_size = 48;


    private SerialPort serial_port;
    private InputStream serial_input;
    private OutputStream serial_output;
    private boolean connected;
    private String deviceName;
    private CassetteRecorder recorder;
    //private boolean playing;
    private boolean cassette_motor_on;

    private ConcurrentLinkedQueue<Integer> commandsQueue;
    private int last_status;

    public SerialHardwareController(String name) {
        super(name);
        deviceName = name;
        commandsQueue = new ConcurrentLinkedQueue<Integer>();
    }

    @Override
    public void connect() {
        if (isConnected()) throw new IllegalStateException("Already connected!");
        serialConnect(deviceName);
        new CommunicationThread().start();
    }

    @Override
    public void disconnect() {
        if (!isConnected()) return;
        connected = false;

        try {
            if (serial_input != null) serial_input.close();
            if (serial_output != null) serial_output.close();
        } catch (Exception ignored) {
        }
        serial_input = null;
        serial_output = null;

        if (serial_port != null) serial_port.close();
        serial_port = null;

    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    private void serialConnect(String devname) {
        CommPortIdentifier portId = null;
        Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();

        while (portEnum.hasMoreElements()) {
            CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
            if (devname != null) {
                if (currPortId.getName().equals(devname)) {
                    portId = currPortId;
                    break;
                }
            }
        }

        if (portId == null) {
            Log.write("Could not find serial port.");
            return;
        }

        try {
            // open serial port, and use class name for the appName.
            serial_port = (SerialPort) portId.open(this.getClass().getName(), TIME_OUT);

            // set port parameters
            serial_port.setSerialPortParams(DATA_RATE,
                    SerialPort.DATABITS_8,
                    SerialPort.STOPBITS_1,
                    SerialPort.PARITY_NONE);

            // open the streams
            serial_input = serial_port.getInputStream();
            serial_output = serial_port.getOutputStream();

            connected = true;
            Log.write("Connected to serial device: " + portId.getName());
            send_command(CMD_SENSE_OFF);
        } catch (Exception e) {
            System.err.println(e.toString());
        }
    }

    @Override
    public void play() {
        if (isPlaying()) return;
        send_command(CMD_SENSE_ON);
        send_command(CMD_PLAY);
        //playing = true;
    }

    @Override
    public void stop() {
        if (!isPlaying()) return;
        send_command(CMD_STOP);
        send_command(CMD_SENSE_OFF);
        //playing = false;
    }

    private void send_command(int cmd) {
        commandsQueue.add(cmd);
    }

    public class CommunicationThread extends Thread {
        @Override
        public void run() {
            Log.write("Communication thread started.");
            while (connected) {
                try {
                    process_input();
                    communicate_commands();
                } catch (Exception e) {
                    //e.printStackTrace();
                }
            }
            Log.write("Communication thread terminated.");
            connected = false;
        }
    }

    private void communicate_commands() {
        if (commandsQueue.peek() != null) {
            try {
                int command = commandsQueue.poll();
                Log.write("Sending command " + ((char) command) + "...");

                serial_output.write(command);
                if (isPlaying()) {
                    for (int i = 0; i < buf_size - 1; i++) serial_output.write(0);
                }

                if (command == CMD_PLAY) {
                    setPlaying(true);
                }
                if (command == CMD_STOP) {
                    setPlaying(false);
                }
            } catch (Exception e) {
                e.printStackTrace();

            }
        }

        if (!isPlaying()) {

            last_status++;
            if (last_status > 5) {

                send_command(CMD_SENSE_OFF);
                last_status = 0;
            }
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void process_input() throws Exception {
        byte[] serial_buf = new byte[1];
        while (serial_input.available() > 0) {
            int readed = serial_input.read(serial_buf, 0, 1);
            if (readed != 1) {
                Log.write("No char received!");
                return;
            }

            switch (serial_buf[0]) {
                // request for tap data
                case 'N':
                    tap_data_send();
                    break;
                // motor control on
                case 'M':
                    Log.write("Cassette motor on");
                    cassette_motor_on = true;
                    break;
                // motor control off
                case 'm':
                    Log.write("Cassette motor off");
                    cassette_motor_on = false;
                    break;
                // ping reply
                case 'P':
                    Log.write("Ping response received");
                    break;
                // error
                case 'E':
                    Log.write("Error");
                    break;
                default:
                    Log.write("Unrecognised command: " + serial_buf[0]);
                    break;
            }
        }
    }

    private void tap_data_send() {
        if (recorder.getTape().getPos() >= recorder.getTape().getSize()) {
            Log.write("Tape end reached");
            try {
                Thread.sleep(100);
            } catch (Exception e) {
                e.printStackTrace();
            }
            stop();
            recorder.getTape().rewind();
            return;
        }

        int tap_remaining = recorder.getTape().getSize() - recorder.getTape().getPos();
        int write_size;
        if (tap_remaining >= buf_size - 1) {
            write_size = buf_size - 1;
        } else {
            write_size = tap_remaining;
        }

        try {
            serial_output.write(CMD_NOP);
            serial_output.write(recorder.getTape().getNByte(write_size), 0, write_size);

            while (write_size < buf_size - 1) {
                serial_output.write(0x20);
                write_size++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setRecorder(CassetteRecorder recorder) {
        this.recorder = recorder;
    }


    public static String[] getAllowedDeviceNames() {
        ArrayList<String> list = new ArrayList<>();
        Enumeration portEnum = CommPortIdentifier.getPortIdentifiers();

        while (portEnum.hasMoreElements()) {
            CommPortIdentifier currPortId = (CommPortIdentifier) portEnum.nextElement();
            list.add(currPortId.getName());
        }

        return list.toArray(new String[0]);
    }
}
