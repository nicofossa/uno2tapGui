
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

  Completely based on original work by Mike Dawson (https://gp2x.org/uno2tap/)

  It worked on my Commodore, but USE IT AT YOUR OWN RISK.
*/

#define PIN_MOTOR 6 //MOTOR RED (green is VCC), add a resistor of about 100K to GND in order to let the capacitor to fully discharge, otherwise the reading would be meaningless
#define PIN_READ 3  //READ WHITE
#define PIN_WRITE 4 //WRITE BROWN
#define PIN_SENSE 5 //SENSE BLUE
#define PIN_DBG 13

void setup() {
  pinMode(PIN_MOTOR, INPUT);
  pinMode(PIN_READ, OUTPUT);
  pinMode(PIN_WRITE, INPUT);
  pinMode(PIN_SENSE, OUTPUT);
  pinMode(PIN_DBG, OUTPUT);

  digitalWrite(PIN_SENSE, 1);

  Serial.begin(115200);
}

// because of overhead we have to transfer
//  multiple bytes at a time to keep the
//  average time per byte down
// the PC sends into the arduino system buffer
//  while we buffer up copies of that
// the arduino serial buffer is 63 bytes so
//  max transfer is 63 and must be the same on the PC
#define XFR_SIZE 48
#define NUM_XFRS 8
#define BUF_SIZE (XFR_SIZE*NUM_XFRS)
int buf[BUF_SIZE];
int buf_pos, xfr_pos, buf_entries;

int playing;
int pre_buffering;
int recording;
int recording_start;

unsigned long pulse_length;
unsigned long last_pulse;
int reading_extended;
unsigned long extended_data[3];

void execute(int cmd) {
  switch (cmd) {
    // nop
    case 'Z':
      break;
    // ping
    case 'P':
      Serial.write('P');
      break;
    // play
    case 'R':
      playing = 1;
      pre_buffering = 1;
      buf_pos = 0;
      buf_entries = 0;
      xfr_pos = -1;
      Serial.write('N');
      break;
    // stop
    case 'r':
      playing = 0;
      recording = 0;
      break;
    // sense on
    case 'S':
      digitalWrite(PIN_SENSE, 0);
      break;
    // sense off
    case 's':
      digitalWrite(PIN_SENSE, 1);
      break;
    // record on
    case 'W':
      recording = 1;
      recording_start = 1;
      break;
  }
}

//int motor_on=1;
int xmotor_on;

void read_motor() {
  xmotor_on = digitalRead(PIN_MOTOR);
  digitalWrite(PIN_DBG, xmotor_on);
}

void send_tap_interval(unsigned long interval) {
  unsigned long tap_interval = interval / 8.12;
  if (tap_interval == 0) tap_interval++;
  if (tap_interval < 256) {
    Serial.write(tap_interval);
    return;
  }
  Serial.write(0);
  Serial.write(interval & 0xff);
  Serial.write((interval & 0xff00) >> 8);
  Serial.write((interval & 0xff0000) >> 16);
}

int last_write_val;
unsigned long last_write_time;

void record() {
  int write = digitalRead(PIN_WRITE);
  if (write != last_write_val) {
    last_write_val = write;
    if (write == HIGH) {
      unsigned long time_now = micros();
      if (recording_start) {
        recording_start = 0;
      } else {
        unsigned long interval = time_now - last_write_time;
        send_tap_interval(interval);
      }
      last_write_time = time_now;
    }
  }
}

void buffer_next() {
  // copy the arduino library serial buffer into our buffer
  // Serial.read() is slow, so don't copy all at once
  // then request the next XFR_SIZE of data
  //  and hope that receiving the serial data in the background
  //  doesn't slow loop() too much

  if (xfr_pos == -1) {
    if (Serial.available() < XFR_SIZE) return;
    else xfr_pos = 0;
    return;
  }

  if (xfr_pos == XFR_SIZE) {
    buf_entries++;
    Serial.write('N');
    xfr_pos = -1;
    return;
  }

  int pos_base = buf_pos - (buf_pos % XFR_SIZE);
  int buf_base = buf_entries * XFR_SIZE;
  for (int i = 0; i < 8; i++) {
    int index = (pos_base + buf_base + xfr_pos) % BUF_SIZE;
    buf[index] = Serial.read();
    xfr_pos++;
    if (xfr_pos == XFR_SIZE) break;
  }
}

// when playing, loop() must never take longer to run than the pulse length it's sending
//  (actually you can get away with it on a long pulse, loader permitting)
void loop() {
  read_motor();

  if (recording) {
    record();
    if (Serial.available() > 0) {
      int cmd = Serial.read();
      execute(cmd);
    }
    return;
  }

  if (!playing) {
    delay(100);
    if (Serial.available() > 0) {
      int cmd = Serial.read();
      execute(cmd);
    }
    return;
  }

  if(!xmotor_on) return;

  // prepare buffer
  if (buf_pos >= BUF_SIZE) buf_pos = 0;
  if (buf_entries < NUM_XFRS) buffer_next();
  else pre_buffering = 0;
  if (pre_buffering) return;
  // first byte is control
  if (buf_pos % XFR_SIZE == 0) {
    execute(buf[buf_pos++]);
    if (!playing) return;
  }

  unsigned long tap_data = (unsigned long)buf[buf_pos++];
  //Serial.write(tap_data);
  if (buf_pos % XFR_SIZE == 0) buf_entries--;
  if (buf_entries < 1) Serial.write('E');

  // calculate the pulse interval
  if (reading_extended) {
    reading_extended--;
    extended_data[reading_extended] = tap_data;
    if (reading_extended) {
      return;
    } else {
      pulse_length = (extended_data[0] << 16) + (extended_data[1] << 8) + extended_data[2];
      pulse_length *= 1.015;
    }
  } else if (tap_data == 0) {
    reading_extended = 3;
    return;
  } else {
    pulse_length = tap_data * 8.12;
  }

  // send a 1 at half interval
  //if(micros()>(last_pulse+(pulse_length/2))) Serial.write('E');
  while (micros() < (last_pulse + (pulse_length / 2)));
  digitalWrite(PIN_READ, 1);
  // send a 0 at full interval
  while (micros() < (last_pulse + pulse_length));
  digitalWrite(PIN_READ, 0);

  last_pulse = micros();
}


