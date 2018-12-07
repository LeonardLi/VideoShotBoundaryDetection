package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {



    private Stage primaryStage;
    private Scene primaryScene;
    private Controller controller;

    @Override
    public void start(Stage primaryStage) throws Exception{
        this.primaryStage = primaryStage;
        initLayout();

    }

    @Override
    public void stop() throws Exception {
        if(controller.getAudioPlayer() != null) {
            controller.getVideoPlayer().terminate();
        }
        if (controller.getVideoPlayer() != null) {
            controller.getAudioPlayer().terminate();
        }

        controller.closeJavaSound();
        super.stop();

    }

    private void initLayout(){
        try{
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/sample.fxml"));
            Parent root = loader.load();
            primaryStage.setTitle("Video Shots Detection");
            primaryScene = new Scene(root, 1024, 768);
            //primaryScene.getStylesheets().add(getClass().getResource("/scrollButton.css").toExternalForm());
            primaryStage.setScene(primaryScene);
            controller = loader.getController();
            controller.setMainApp(this);
            primaryStage.show();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public Scene getPrimaryScene() {
        return primaryScene;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
