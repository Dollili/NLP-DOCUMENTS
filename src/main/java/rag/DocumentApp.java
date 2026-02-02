package rag;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import rag.controller.LoadingController;
import rag.service.IndexService;

import java.io.IOException;

import static rag.config.AppConfig.getDocPathEnd;

public class DocumentApp extends Application {
    private final String TEXT = "탐색기";

    @Override
    public void start(Stage stage) throws IOException {
        Stage index = new Stage();
        FXMLLoader loader = new FXMLLoader(DocumentApp.class.getResource("/view/LoadingRoot.fxml"));
        Parent loadRoot = loader.load();
        Scene loadingScene = new Scene(loadRoot);

        LoadingController controller = loader.getController();

        Image image = new Image("/image/h_logo.png");
        index.getIcons().add(image);
        index.setTitle(TEXT);
        index.setScene(loadingScene);
        index.show();

        task(index, stage, controller);

        index.setOnCloseRequest(event -> {
            System.exit(0);
        });
    }

    public void task(Stage stage, Stage mainStage, LoadingController controller) {
        Task<Void> task = getTaskIndex(controller);
        controller.update(task);

        task.setOnSucceeded(e -> {
            try {
                stage.close();

                FXMLLoader fxmlLoader = new FXMLLoader(DocumentApp.class.getResource("/view/Root.fxml"));
                Parent root = fxmlLoader.load();
                Scene scene = new Scene(root);

                Image image = new Image("/image/h_logo.png");
                mainStage.initStyle(StageStyle.DECORATED);
                mainStage.getIcons().add(image);
                mainStage.setTitle(TEXT);
                mainStage.setScene(scene);
                mainStage.setResizable(false);
                mainStage.show();

                mainStage.setOnCloseRequest(event -> {
                    System.exit(0);
                });
            } catch (IOException ex) {
                System.err.println("메인 화면 로드 실패: " + ex.getMessage());
                ex.printStackTrace();
                
                Platform.runLater(() -> {
                    controller.status("화면 로드 실패");
                    controller.status2("프로그램을 다시 시작해주세요: " + ex.getMessage());
                });
            } catch (Exception ex) {
                System.err.println("예상치 못한 오류: " + ex.getMessage());
                ex.printStackTrace();
                
                Platform.runLater(() -> {
                    controller.status("오류 발생");
                    controller.status2(ex.getMessage());
                });
            }
        });

        task.setOnFailed(e -> {
            Throwable exception = task.getException();
            String errorMsg = "작업 실패";
            
            if (exception != null) {
                errorMsg = exception.getMessage() != null ? 
                          exception.getMessage() : 
                          exception.getClass().getSimpleName();
                System.err.println("작업 실패: " + errorMsg);
                exception.printStackTrace();
            }
            
            final String finalErrorMsg = errorMsg;
            Platform.runLater(() -> {
                controller.status("오류 발생");
                controller.status2(finalErrorMsg);
            });
        });

        Thread thread = new Thread(task);
        thread.setDaemon(false);
        thread.setUncaughtExceptionHandler((t, ex) -> {
            System.err.println("스레드 예외 발생: " + ex.getMessage());
            ex.printStackTrace();
            
            Platform.runLater(() -> {
                controller.status("심각한 오류 발생");
                controller.status2("프로그램을 다시 시작해주세요.");
            });
        });
        thread.start();
    }

    private static Task<Void> getTaskIndex(LoadingController controller) {
        return new Task<Void>() {
            @Override
            protected Void call() {
                try {
                    String docPathEnd = getDocPathEnd();
                    
                    if (docPathEnd == null || docPathEnd.isEmpty()) {
                        updateMessage("⚠️ 초기 경로 설정이 필요합니다.");
                        controller.status("⚠️ 초기 경로 설정이 필요합니다.");
                        controller.status2("프로그램 실행 후 경로를 설정해주세요.");
                        return null;
                    }
                    
                    if (IndexService.shouldRebuildIndex(docPathEnd)) {
                        updateMessage("📁 문서 인덱싱 중...");
                        controller.status("📁 문서 인덱싱 중");
                        controller.status2("처음 실행 시에만 수행되는 작업입니다.");
                        
                        String result = IndexService.buildIndex(docPathEnd, this::updateMessage);
                        
                        updateMessage(result);
                        controller.status2(result);
                    } else {
                        updateMessage("📂 기존 인덱스 로드 중...");
                        controller.status("📂 기존 인덱스 로드 중");
                        
                        IndexService.loadIndex(docPathEnd, null);
                        
                        updateMessage("✅ 인덱스 로드 완료");
                        controller.status2("인덱스 로드 완료");
                    }
                } catch (Exception e) {
                    String errorMsg = "인덱스 처리 실패: " + e.getMessage();
                    updateMessage(errorMsg);
                    controller.status("❌ 오류 발생");
                    controller.status2(errorMsg);
                    System.err.println(errorMsg);
                    e.printStackTrace();
                }
                return null;
            }
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}
