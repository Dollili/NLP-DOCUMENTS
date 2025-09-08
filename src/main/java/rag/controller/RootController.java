package rag.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import rag.service.IndexService;
import rag.service.SearchService;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

import static rag.config.AppConfig.*;
import static rag.util.FileUtils.isFolder;

public class RootController implements Initializable {
    //base 경로
    @FXML
    public Button path_btn;
    @FXML
    public TextField path_input;

    @FXML
    public Button search_btn;
    @FXML
    public TextField search_input;
    @FXML
    public Text time;
    @FXML
    public TextArea resultArea;
    @FXML
    public Text sumDoc;
    @FXML
    public Button clear_btn;

    public static boolean flag = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        clearButton();

        String firstPath = DOC_PATH;
        path_input.setText(DOC_PATH);
        sumDoc.setText(String.valueOf(DOCUMENTS.size()));

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);

        pathChoice(alert);
        search_btn.setOnMouseClicked(event -> {
            alert.setTitle("알림");
            alert.setHeaderText(null);

            if (flag) {
                alert.getDialogPane().setPrefSize(150, 100);
                alert.setContentText("경로를 확인해주세요.");
                alert.showAndWait();
                return;
            }

            if (isValid()) {
                alert.getDialogPane().setPrefSize(150, 100);
                alert.setContentText("검색값이 존재하지 않습니다.");
                alert.showAndWait();
                return;
            }

            if (isValid(firstPath)) {
                alert.getDialogPane().setPrefSize(350, 100);
                alert.setContentText("경로가 변경되어 재탐색합니다.");
                alert.showAndWait();

                Task<Void> reIndex = getTaskIndex();
                reIndex.setOnSucceeded(e -> {
                    startSearch();
                });

                Thread thread = new Thread(reIndex);
                thread.setDaemon(false);
                thread.start();
            } else {
                startSearch();
            }
        });

    }

    private void startSearch() {
        search_btn.setDisable(true);
        resultArea.setText("조회 중...");
        Task<Map<String, Object>> task = getMapTask();

        Thread thread = new Thread(task);
        thread.setDaemon(false);
        thread.start();
    }

    //질문 조회 task
    private Task<Map<String, Object>> getMapTask() {
        sumDoc.setText(String.valueOf(DOCUMENTS.size()));
        String input = search_input.getText(); // 질문

        Task<Map<String, Object>> task = new Task<>() {
            @Override
            protected Map<String, Object> call() throws Exception {
                return SearchService.findPath(input);
            }
        };

        task.setOnSucceeded(e -> {
            Map<String, Object> result = task.getValue();
            if (result.get("success") != null) {
                searchClear();
                double taskTime = (double) result.get("taskTime");
                time.setText(String.format("%.1f sec", taskTime));
                String[] parts = (String[]) result.get("success");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    sb.append(DOC_PATH).append(part).append("\n");
                }
                resultArea.appendText(sb.toString());
            }
            search_btn.setDisable(false);
        });

        task.setOnFailed(e -> {
            searchClear();
            resultArea.setText(task.getException().getMessage().split(":")[0] + " ::: 재시도 필요");
            search_btn.setDisable(false);
        });
        return task;
    }

    private static Task<Void> getTaskIndex() {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (IndexService.shouldRebuildIndex(getDocPathEnd())) {
                    IndexService.buildIndex(getDocPathEnd(), null);
                } else {
                    IndexService.loadIndex(getDocPathEnd(), null);
                }
                return null;
            }
        };
    }

    private boolean isValid() {
        return path_input.getText().isEmpty() || search_input.getText().isEmpty();
    }

    private boolean isValid(String path) {
        return !path_input.getText().equals(path);
    }

    private void searchClear() {
        resultArea.clear();
    }

    private void pathChoice(Alert alert) {
        path_btn.setOnMouseClicked(event -> {
            String txt = path_input.getText().trim();
            alert.setTitle("알림");
            alert.setHeaderText(null);
            if (isFolder(txt)) {
                flag = true;
                alert.getDialogPane().setPrefSize(150, 100);
                alert.setContentText("폴더 경로를 입력해주세요.");
            } else {
                setDocPath(txt);
                flag = false;
                alert.setContentText("경로 설정 완료");
            }
            alert.showAndWait();
        });
    }

    private void clearButton() {
        clear_btn.setOnMouseClicked(event -> {
            resultArea.setText("");
        });
    }
}
