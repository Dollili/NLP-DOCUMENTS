package rag.controller;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.text.Text;

import java.net.URL;
import java.util.ResourceBundle;

public class LoadingController implements Initializable {
    @FXML
    public Text loadingText;
    public Text progress;
    public Text emptyText;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

    }

    public void status(String msg) {
        Platform.runLater(() -> loadingText.setText(msg));
    }

    public void status2(String msg) {
        Platform.runLater(() -> emptyText.setText(msg));
    }

    public void update(Task<Void> task) {
        progress.textProperty().bind(task.messageProperty());
    }
}
