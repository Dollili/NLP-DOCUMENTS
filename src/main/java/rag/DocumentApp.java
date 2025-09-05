package rag;

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import rag.controller.LoadingController;
import rag.model.Tracker;
import rag.service.IndexService;

import java.io.IOException;

public class DocumentApp extends Application {
    private final String TEXT = "문서 탐색기";
    private static final Tracker tracker = new Tracker(0);

    @Override
    public void start(Stage stage) throws IOException {
        Stage index = new Stage();
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/LoadingRoot.fxml"));
        Parent loadRoot = loader.load();
        Scene loadingScene = new Scene(loadRoot);

        LoadingController controller = loader.getController();

        Image image = new Image("/image/h_logo.png");
        index.getIcons().add(image);
        index.setTitle(TEXT);
        index.setScene(loadingScene);
        index.setResizable(false);
        index.show();

        task(index, stage, controller);

        index.setOnCloseRequest(event -> {
            System.exit(0);
        });
    }

    public void task(Stage stage, Stage mainStage, LoadingController controller) {
        Task<Void> task = getTask(controller);
        controller.update(task);

        task.setOnSucceeded(e -> {
            stage.close();
            try {
                FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/Root.fxml"));
                Parent root = fxmlLoader.load();
                Scene scene = new Scene(root);

                Image image = new Image("/image/h_logo.png");
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

        Thread thread = new Thread(task);
        thread.setDaemon(true); // 종료 시 task도 같이 종료
        thread.start();
    }

    private static Task<Void> getTask(LoadingController controller) {

        return new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (IndexService.shouldRebuildIndex()) {
                    controller.status("문서 인덱싱 중...");
                    controller.status2("처음 실행 시에만 수행되는 작업입니다.");
                    String result = IndexService.buildIndex(tracker, this::updateMessage);
                    controller.status2(result);
                } else {
                    controller.status("기존 인덱스 로드 중...");
                    IndexService.loadIndex(tracker, null);
                }
                return null;
            }
        };
    }

    public static void main(String[] args) {
        launch(args);
    }
}
