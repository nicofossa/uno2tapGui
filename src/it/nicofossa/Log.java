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

import javafx.application.Platform;
import javafx.scene.control.TextArea;

public class Log {
    private Log() {
    }

    private static TextArea textArea;


    public static void write(String message) {
        if (textArea != null) {
            Platform.runLater(() -> textArea.appendText(message + "\n"));
        } else {
            System.out.println(message);
        }
    }

    public static void setTextArea(TextArea _textArea) {
        textArea = _textArea;
    }
}
