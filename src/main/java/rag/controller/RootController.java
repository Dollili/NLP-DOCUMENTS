package rag.controller;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rag.service.IndexService;
import rag.service.SearchService;

import java.net.URL;
import java.util.Map;
import java.util.ResourceBundle;

import static rag.config.AppConfig.*;
import static rag.util.FileUtils.isFolderInvalid;
import static rag.util.FileUtils.openFolderInExplorer;

public class RootController implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(RootController.class);
    
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
    public ListView<String> resultList;  // TextArea -> ListView로 변경
    @FXML
    public ProgressBar searchProgressBar;  // ProgressBar 추가
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

        // ListView 클릭 이벤트 추가
        setupResultListClickHandler();

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
                    sumDoc.setText(String.valueOf(DOCUMENTS.size()));
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

    /**
     * ListView 아이템 클릭 시 폴더 열기
     */
    private void setupResultListClickHandler() {
        resultList.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {  // 더블 클릭
                String selectedItem = resultList.getSelectionModel().getSelectedItem();
                if (selectedItem != null && !selectedItem.isEmpty()) {
                    // "1. 경로" 형식에서 경로만 추출
                    String path = extractPathFromListItem(selectedItem);
                    if (path != null) {
                        logger.info("폴더 열기 시도: {}", path);
                        boolean success = openFolderInExplorer(path);
                        if (!success) {
                            logger.error("폴더 열기 실패: {}", path);
                            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                            errorAlert.setTitle("오류");
                            errorAlert.setHeaderText(null);
                            errorAlert.setContentText("폴더를 열 수 없습니다:\n" + path);
                            errorAlert.showAndWait();
                        }
                    }
                }
            }
        });
    }

    /**
     * ListView 아이템에서 실제 경로 추출
     * "1. C:\path\to\folder ::: 0.95" 형식에서 경로만 추출
     */
    private String extractPathFromListItem(String item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        
        // "1. " 제거
        int dotIndex = item.indexOf(". ");
        if (dotIndex >= 0) {
            item = item.substring(dotIndex + 2).trim();
        }
        
        // " :::" 로 유사도 부분 제거
        int separatorIndex = item.indexOf(" :::");
        if (separatorIndex >= 0) {
            item = item.substring(0, separatorIndex).trim();
        }
        
        return item.isEmpty() ? null : item;
    }

    private void startSearch() {
        search_btn.setDisable(true);
        resultList.getItems().clear();
        
        // ProgressBar 표시 및 무한 진행 모드로 설정
        searchProgressBar.setVisible(true);
        searchProgressBar.setProgress(-1.0);
        
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
            // ProgressBar 숨기기
            searchProgressBar.setVisible(false);
            
            Map<String, Object> result = task.getValue();
            
            if (result == null) {
                searchClear();
                resultList.getItems().add("검색 중 오류가 발생했습니다.");
                search_btn.setDisable(false);
                return;
            }
            
            // 에러 처리
            if (result.containsKey("error")) {
                searchClear();
                String errorMsg = (String) result.get("error");
                resultList.getItems().add(errorMsg);
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
                        resultList.getItems().add(parts.length + "개의 관련 문서를 찾았습니다. (클릭하여 폴더 열기)");

                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i].trim();
                            if (!part.isEmpty()) {
                                String fullPath = DOC_PATH + part;
                                resultList.getItems().add(String.format("%d. %s", i + 1, fullPath));
                            }
                        }
                    } else {
                        resultList.getItems().add("관련 문서를 찾을 수 없습니다.");
                        resultList.getItems().add("");
                        resultList.getItems().add("다음을 확인해주세요:");
                        resultList.getItems().add("  • 검색어를 다시 확인해주세요");
                        resultList.getItems().add("  • 폴더 경로가 올바른지 확인해주세요");
                        resultList.getItems().add("  • 해당 폴더에 문서가 있는지 확인해주세요");
                    }
                } else {
                    resultList.getItems().add("검색 결과 형식이 올바르지 않습니다.");
                }
            } else {
                searchClear();
                resultList.getItems().add("알 수 없는 응답 형식입니다.");
            }
            
            search_btn.setDisable(false);
        });

        task.setOnFailed(e -> {
            // ProgressBar 숨기기
            searchProgressBar.setVisible(false);
            
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
            
            resultList.getItems().add(errorMessage);
            resultList.getItems().add("");
            resultList.getItems().add("잠시 후 다시 시도해주세요.");
            
            logger.error("검색 실패", exception);
            
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
                    logger.error("인덱스 작업 실패", e);
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
        resultList.getItems().clear();
    }

    private void pathChoice(Alert alert) {
        path_btn.setOnMouseClicked(event -> {
            String txt = path_input.getText().trim();
            
            alert.setTitle("알림");
            alert.setHeaderText(null);
            alert.getDialogPane().setPrefSize(300, 120);
            
            if (txt.isEmpty()) {
                flag = true;
                alert.setContentText("경로를 입력해주세요.");
                alert.showAndWait();
                return;
            }
            
            try {
                if (isFolderInvalid(txt)) {
                    flag = true;
                    alert.setContentText("유효하지 않은 폴더 경로입니다.\n실제 존재하는 폴더를 입력해주세요.");
                    alert.showAndWait();
                } else {
                    // 경로 설정
                    setDocPath(txt);
                    flag = false;
                    
                    // 진행 알림용 새 Alert 생성
                    Alert progressAlert = new Alert(Alert.AlertType.INFORMATION);
                    progressAlert.setTitle("알림");
                    progressAlert.setHeaderText(null);
                    progressAlert.getDialogPane().setPrefSize(300, 120);
                    progressAlert.setContentText("경로 설정 완료!\n인덱싱을 시작합니다...");
                    progressAlert.show();
                    
                    // 버튼 비활성화
                    path_btn.setDisable(true);
                    search_btn.setDisable(true);
                    
                    Task<Void> indexTask = getTaskIndex();
                    
                    indexTask.setOnSucceeded(e -> {
                        // UI 업데이트
                        sumDoc.setText(String.valueOf(DOCUMENTS.size()));
                        
                        // 버튼 활성화
                        path_btn.setDisable(false);
                        search_btn.setDisable(false);
                        
                        // 진행 알림 닫기
                        progressAlert.close();
                        
                        // 완료 알림 (새 Alert)
                        Alert completionAlert = new Alert(Alert.AlertType.INFORMATION);
                        completionAlert.setTitle("알림");
                        completionAlert.setHeaderText(null);
                        completionAlert.getDialogPane().setPrefSize(300, 120);
                        completionAlert.setContentText("인덱싱 완료!\n문서: " + DOCUMENTS.size() + "개");
                        completionAlert.showAndWait();
                    });
                    
                    indexTask.setOnFailed(e -> {
                        Throwable exception = indexTask.getException();
                        
                        // 버튼 활성화
                        path_btn.setDisable(false);
                        search_btn.setDisable(false);
                        
                        // 진행 알림 닫기
                        progressAlert.close();
                        
                        // 오류 알림 (새 Alert)
                        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                        errorAlert.setTitle("오류");
                        errorAlert.setHeaderText(null);
                        errorAlert.getDialogPane().setPrefSize(300, 120);
                        String errorMsg = exception != null ? exception.getMessage() : "알 수 없는 오류";
                        errorAlert.setContentText("인덱싱 실패\n" + errorMsg);
                        errorAlert.showAndWait();
                        
                        logger.error("인덱싱 실패", exception);
                    });
                    
                    // 백그라운드에서 인덱싱 실행
                    Thread thread = new Thread(indexTask);
                    thread.setDaemon(false);
                    thread.start();
                }
            } catch (Exception e) {
                flag = true;
                alert.setContentText("경로 확인 중 오류 발생:\n" + e.getMessage());
                logger.error("경로 검증 오류", e);
                alert.showAndWait();
            }
        });
    }

    private void clearButton() {
        clear_btn.setOnMouseClicked(event -> {
            resultList.getItems().clear();
        });
    }
}
