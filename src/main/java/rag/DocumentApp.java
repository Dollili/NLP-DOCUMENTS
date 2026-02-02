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
            stage.close();

            try {
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
                throw new RuntimeException(ex);
            }
        });

        task.setOnFailed(e -> {
            Platform.runLater(() -> {
                controller.status("오류 발생: " + e.toString());
            });
        });

        Thread thread = new Thread(task);
        thread.setDaemon(false); // 종료 시 task도 같이 종료
        thread.start();
    }

    private static Task<Void> getTaskIndex(LoadingController controller) {
        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (IndexService.shouldRebuildIndex(getDocPathEnd())) {
                    if (!getDocPathEnd().isEmpty()) {
                        controller.status("\uD83D\uDCC1 문서 인덱싱 중");
                        controller.status2("처음 실행 시에만 수행되는 작업입니다.");
                        String result = IndexService.buildIndex(getDocPathEnd(), this::updateMessage);
                        controller.status2(result);
                    } else {
                        controller.status("\uD83D\uDCC1 초기 경로 설정이 필요합니다.");
                    }
                } else {
                    controller.status("\uD83D\uDCC1 기존 인덱스 로드 중");
                    IndexService.loadIndex(getDocPathEnd(), null);
                }
                return null;
            }
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}
