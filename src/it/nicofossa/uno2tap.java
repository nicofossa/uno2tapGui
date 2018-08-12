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

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class uno2tap extends Application implements CassetteRecorder.CasseteRecorderChangedListener {


    @FXML
    private Button playstopButton;

    @FXML
    private TextArea debugTextArea;

    @FXML
    private ProgressBar cassetteProgress;

    @FXML
    private Button connectdisconnectButton;

    @FXML
    private Button openButton;

    @FXML
    private Button rewindButton;

    private CassetteRecorder cassetteRecorder;

    private Stage stage;


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        stage = primaryStage;
        Parent root = FXMLLoader.load(getClass().getClassLoader().getResource("uno2tap.fxml"));

        Scene scene = new Scene(root);

        primaryStage.setTitle("uno2tap");
        primaryStage.setScene(scene);
        primaryStage.show();

        connectdisconnectButton = (Button) scene.lookup("#connectdisconnectButton");
        playstopButton = (Button) scene.lookup("#playstopButton");
        openButton = (Button) scene.lookup("#openButton");
        cassetteProgress = (ProgressBar) scene.lookup("#cassetteProgress");
        debugTextArea = (TextArea) scene.lookup("#debugTextArea");
        rewindButton = (Button) scene.lookup("#rewindButton");

        connectdisconnectButton.setOnMouseClicked((event) -> {
            connectdisconnectButtonClick();
        });

        playstopButton.setOnMouseClicked(event -> playstopButtonClicked());

        openButton.setOnMouseClicked(event -> openButtonClick());
        rewindButton.setOnMouseClicked(event -> rewindButton());

        primaryStage.setOnCloseRequest((windowEvent) -> {

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Close");
            alert.setContentText("Are you sure you want to exit?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (((Optional) result).get() == ButtonType.OK) {
                    if (cassetteRecorder != null) cassetteRecorder.dispose();
                } else {
                    windowEvent.consume();
                }
            } else {
                windowEvent.consume();
            }
        });

        //debugTextArea.setEditable(false);
        Log.setTextArea(debugTextArea);

        openButton.setDisable(true);
        playstopButton.setDisable(true);
        rewindButton.setDisable(true);

    }

    private void rewindButton() {
        if (cassetteRecorder == null) return;

        cassetteRecorder.rewind();
    }

    private void openButtonClick() {
        if (cassetteRecorder == null) return;

        if (cassetteRecorder.getState() == CassetteRecorder.State.EJECTED) {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select a TAP file...");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("C64 TAP file", "*.tap"));
            File file = fileChooser.showOpenDialog(stage);
            if (file != null && file.exists()) {
                cassetteRecorder.setTape(file);
            }
        }


    }

    private void playstopButtonClicked() {
        if (cassetteRecorder == null) return;

        if (cassetteRecorder.getState() == CassetteRecorder.State.PLAYING) {
            cassetteRecorder.stop();
            return;
        }

        if (cassetteRecorder.getState() == CassetteRecorder.State.STOPPED) {
            cassetteRecorder.play();
            //playstopButton.setText("Stop");
            return;
        }
    }

    private void connectdisconnectButtonClick() {
        if (cassetteRecorder == null) {
            TextInputDialog alert = new TextInputDialog("COM3");


            alert.setTitle("COM port");
            alert.setContentText("Insert the serial port name");

            Optional<String> result = alert.showAndWait();
            if (result.isPresent()) {
                HardwareController hardwareController = new SerialHardwareController(result.get());
                cassetteRecorder = new CassetteRecorder(hardwareController);
                hardwareController.setRecorder(cassetteRecorder);

                if (!hardwareController.isConnected()){
                    return;
                }
                connectdisconnectButton.setText("Disconnect");
                cassetteRecorder.setCasseteRecorderChangedListener(this);
                openButton.setDisable(false);
                playstopButton.setDisable(false);
                rewindButton.setDisable(false);
            }




        } else {
            try {
                cassetteRecorder.dispose();
            }catch (IllegalStateException ex){
                Log.write("Already disconnected!");
            }
            cassetteRecorder = null;
            connectdisconnectButton.setText("Connect");
            openButton.setDisable(true);
            playstopButton.setDisable(true);
            rewindButton.setDisable(true);
        }

    }


    @Override
    public void onPosUpdated(final int pos, final int lenght) {
        Platform.runLater(() -> cassetteProgress.setProgress(((double) pos) / lenght));

    }

    @Override
    public void onCassetteRecorderStateChanged(CassetteRecorder.State state) {
        Platform.runLater(() -> {
                    openButton.setDisable(false);
                    connectdisconnectButton.setDisable(false);
                    playstopButton.setDisable(false);
                    rewindButton.setDisable(false);


                    switch (state) {
                        case EJECTED:
                            playstopButton.setDisable(true);
                            rewindButton.setDisable(true);
                            playstopButton.setText("Play");
                            break;
                        case STOPPED:
                            playstopButton.setText("Play");
                            break;
                        case PLAYING:
                            connectdisconnectButton.setDisable(true);
                            openButton.setDisable(true);
                            rewindButton.setDisable(true);
                            playstopButton.setText("Stop");
                            break;
                        case RECORDING:
                            //NOT IMPLEMENTED FOR NOW
                            break;
                    }
                }
        );
    }
}
