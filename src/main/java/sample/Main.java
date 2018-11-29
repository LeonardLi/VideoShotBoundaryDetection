package sample;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {



    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception{
        this.primaryStage = primaryStage;
        initLayout();

    }

    private void initLayout(){
        try{
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/sample.fxml"));
            Parent root = loader.load();
            primaryStage.setTitle("Hello World");
            Scene scene = new Scene(root, 1024, 768);
            scene.getStylesheets().add(getClass().getResource("/scrollButton.css").toExternalForm());
            primaryStage.setScene(scene);
            Controller controller = loader.getController();
            controller.setMainApp(this);
            primaryStage.show();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }



    public static void main(String[] args) {
        launch(args);
    }
}
