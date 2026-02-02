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

            if (isValid(getDocPath())) {
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
        String input = search_input.getText().trim(); // 질문

        Task<Map<String, Object>> task = new Task<>() {
            @Override
            protected Map<String, Object> call() {
                return SearchService.findPath(input);
            }
        };

        task.setOnSucceeded(e -> {
            Map<String, Object> result = task.getValue();
            
            if (result == null) {
                searchClear();
                resultArea.setText("검색 중 오류가 발생했습니다.");
                search_btn.setDisable(false);
                return;
            }
            
            // 에러 처리
            if (result.containsKey("error")) {
                searchClear();
                String errorMsg = (String) result.get("error");
                resultArea.setText(errorMsg);
                search_btn.setDisable(false);
                return;
            }
            
            // 성공 처리
            if (result.containsKey("success")) {
                searchClear();
                
                Object timeObj = result.get("taskTime");
                if (timeObj instanceof Double) {
                    double taskTime = (double) timeObj;
                    time.setText(String.format("%.1f sec", taskTime));
                } else {
                    time.setText("N/A");
                }

                Object successObj = result.get("success");
                if (successObj instanceof String[]) {
                    String[] parts = (String[]) successObj;
                    
                    if (parts.length > 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("✅ ").append(parts.length).append("개의 관련 문서를 찾았습니다.\n\n");
                        
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i].trim();
                            if (!part.isEmpty()) {
                                sb.append(String.format("%d. %s%s\n", 
                                        i + 1, 
                                        DOC_PATH, 
                                        part));
                            }
                        }
                        resultArea.setText(sb.toString());
                    } else {
                        resultArea.setText("관련 문서를 찾을 수 없습니다.\n\n" +
                                         "다음을 확인해주세요:\n" +
                                         "• 검색어를 다시 확인해주세요\n" +
                                         "• 폴더 경로가 올바른지 확인해주세요\n" +
                                         "• 해당 폴더에 문서가 있는지 확인해주세요");
                    }
                } else {
                    resultArea.setText("검색 결과 형식이 올바르지 않습니다.");
                }
            } else {
                searchClear();
                resultArea.setText("알 수 없는 응답 형식입니다.");
            }
            
            search_btn.setDisable(false);
        });

        task.setOnFailed(e -> {
            searchClear();
            Throwable exception = task.getException();
            
            String errorMessage = "검색 중 오류 발생";
            if (exception != null) {
                String exMsg = exception.getMessage();
                if (exMsg != null && !exMsg.isEmpty()) {
                    String[] lines = exMsg.split("\n");
                    errorMessage = lines.length > 0 ? lines[0] : exMsg;
                } else {
                    errorMessage = exception.getClass().getSimpleName();
                }
            }
            
            resultArea.setText(errorMessage + "\n\n잠시 후 다시 시도해주세요.");
            System.err.println("검색 실패: " + exception);
            
            if (exception != null) {
                exception.printStackTrace();
            }
            
            search_btn.setDisable(false);
        });
        
        return task;
    }

    private static Task<Void> getTaskIndex() {
        return new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    String docPathEnd = getDocPathEnd();
                    
                    if (docPathEnd == null || docPathEnd.trim().isEmpty()) {
                        updateMessage("경로가 설정되지 않았습니다.");
                        return null;
                    }
                    
                    if (IndexService.shouldRebuildIndex(docPathEnd)) {
                        updateMessage("인덱스 재구축 중...");
                        String result = IndexService.buildIndex(docPathEnd, null);
                        updateMessage(result);
                    } else {
                        updateMessage("기존 인덱스 로드 중...");
                        IndexService.loadIndex(docPathEnd, null);
                        updateMessage("인덱스 로드 완료");
                    }
                } catch (Exception e) {
                    updateMessage("인덱스 처리 실패: " + e.getMessage());
                    System.err.println("인덱스 작업 실패: " + e.getMessage());
                    e.printStackTrace();
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
            alert.getDialogPane().setPrefSize(250, 100);
            
            if (txt.isEmpty()) {
                flag = true;
                alert.setContentText("경로를 입력해주세요.");
                alert.showAndWait();
                return;
            }
            
            try {
                if (isFolder(txt)) {
                    flag = true;
                    alert.setContentText("유효하지 않은 폴더 경로입니다.\n실제 존재하는 폴더를 입력해주세요.");
                } else {
                    setDocPath(txt);
                    flag = false;
                    alert.setContentText("경로 설정 완료\n문서: " + DOCUMENTS.size() + "개");
                }
            } catch (Exception e) {
                flag = true;
                alert.setContentText("경로 확인 중 오류 발생:\n" + e.getMessage());
                System.err.println("경로 검증 오류: " + e.getMessage());
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
