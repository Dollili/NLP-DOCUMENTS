package rag.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import rag.service.SearchService;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

import static rag.config.AppConfig.DOCUMENTS;
import static rag.config.AppConfig.DOC_PATH;

public class RootController implements Initializable {

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

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        sumDoc.setText(String.valueOf(DOCUMENTS.size()));

        search_btn.setOnMouseClicked(event -> {
            search_btn.setDisable(true);
            resultArea.setText("조회 중...");
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
                    String taskTime = result.get("taskTime").toString();
                    time.setText(taskTime + "ms");
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

            Thread thread = new Thread(task);
            thread.setDaemon(true);
            thread.start();
        });
    }

    public void searchClear() {
        resultArea.clear();
    }
}
