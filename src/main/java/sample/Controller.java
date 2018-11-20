package sample;

import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.Global;
import com.xuggle.xuggler.IContainer;
import com.xuggle.xuggler.IStream;
import com.xuggle.xuggler.IStreamCoder;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class Controller implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public static final double SECONDS_BETWEEN_FRAMES = 0.04;
    private static final String outputFilePrefix = "snapshots/";
    private static int mVideoStreamIndex = -1;
    private static long mLastPtsWrite = Global.NO_PTS;
    public static final long MICRO_SECONDS_BETWEEN_FRAMES = (long)(Global.DEFAULT_PTS_PER_SECOND * SECONDS_BETWEEN_FRAMES);
    public SnapShot[]snapShots = new SnapShot[4000];


    private TilePane tilePane;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private Button button_choose;

    @FXML
    private TextArea text_streaminfo;

    @FXML
    private TextField text_framestart;

    @FXML
    private TextField text_frameend;

    @FXML
    private Label label_filename;
    @FXML
    private Label label_filesize;
    @FXML
    private Label label_streams;
    @FXML
    private Label label_durations;
    @FXML
    private Label label_bitrate;

    int[] sd = new int[4000];

    private Main mainApp;

    public void setMainApp(Main mainApp) {
        this.mainApp = mainApp;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //MediaPlayer player = new MediaPlayer(new Media(getClass().getResource("20020924_juve_dk_02a.mpg").toExternalForm()));
        //MediaView mediaView = new MediaView(player);
        IContainer container = IContainer.make();

        int result = container.open(getClass().getResource("/20020924_juve_dk_02a.mpg").getPath(),IContainer.Type.READ,null);

        // query how many streams the call to open found
        if (result < 0)
            throw new RuntimeException("Failed to open media file");
        label_filename.setText("20020924_juve_dk_02a.mpg");
        int numStreams = container.getNumStreams();
        label_streams.setText(numStreams+"");
        // query for the total duration
        long duration = container.getDuration();
        label_durations.setText(duration+"");

        // query for the file size
        long fileSize = container.getFileSize();
        label_filesize.setText(fileSize+"");
        // query for the bit rate
        long bitRate = container.getBitRate();
        label_bitrate.setText(bitRate+"");
//        System.out.println("Number of streams: " + numStreams);
//        System.out.println("Duration (ms): " + duration);
//        System.out.println("File Size (bytes): " + fileSize);
//        System.out.println("Bit Rate: " + bitRate);
        String textArea = "";
        for (int i = 0; i < numStreams; i++) {
            IStream stream = container.getStream(i);
            IStreamCoder coder = stream.getStreamCoder();
            textArea += "stream : " + i + "\n" +
                    "type: " + coder.getCodecType() +"\n"+
                    "codec: "+ coder.getCodecID() +"\n"+
                    "durarion: "+ stream.getDuration() + "\n"+
                    "start time: "+ container.getStartTime()+"\n"+
                    "timebase: "+ stream.getTimeBase().getNumerator()+"/"+ stream.getTimeBase().getDenominator()+"\n"+
                    "format: "+ coder.getPixelType()+"\n"+
                    "frame-rate: "+ coder.getFrameRate().getDouble()+"\n";


//            System.out.println("*** Start of Stream Info ***");
//            System.out.printf("stream : %d\n", i);
//            System.out.printf("type: %s;\n", coder.getCodecType());
//            System.out.printf("codec: %s\n", coder.getCodecID());
//            System.out.printf("durarion: %s\n", stream.getDuration());
//
//            System.out.printf("start time: %s;\n", container.getStartTime());
//            System.out.printf("timebase: %d/%d;\n", stream.getTimeBase().getNumerator(), stream.getTimeBase().getDenominator());
//            System.out.printf("coder tb: %d/%d;\n", coder.getTimeBase().getNumerator(), coder.getTimeBase().getDenominator());
//            System.out.println();
//            System.out.printf("width: %d;\n", coder.getWidth());
//            System.out.printf("height: %d;\n", coder.getHeight());
//            System.out.printf("format: %s;\n", coder.getPixelType());
//            System.out.printf("frame-rate: %5.2f;\n", coder.getFrameRate().getDouble());
//            System.out.println();
//            System.out.println("*** End of Stream Info ***");
        }
        text_streaminfo.appendText(textArea);

        Platform.runLater(()->{
            IMediaReader mediaReader = ToolFactory.makeReader(getClass().getResource("/20020924_juve_dk_02a.mpg").getPath());
            //set bufferedimages created in 24bit color space
            mediaReader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
            mediaReader.addListener(new ImageSnapListener());
            while (mediaReader.readPacket() == null);
            updateGallery(snapShots);
        });

        tilePane = new TilePane();
        //scrollPane.setStyle("-fx-background-color: DAE6F3;");
        tilePane.setPadding(new Insets(15, 15, 15, 15));
        tilePane.setHgap(15);
        tilePane.setVgap(15);
        tilePane.setPrefRows(1);



        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setContent(tilePane);
        scrollPane.setOnScroll(event -> {
            if (event.getDeltaX() == 0 && event.getDeltaY() != 0)
                scrollPane.setHvalue(scrollPane.getHvalue() - event.getDeltaY() / this.tilePane.getWidth());
        });


    }

    private class ImageSnapListener extends MediaListenerAdapter {
        int count = 0;
        @Override
        public void onVideoPicture(IVideoPictureEvent event) {
            if (event.getStreamIndex() != mVideoStreamIndex){
                if(mVideoStreamIndex == -1) mVideoStreamIndex = event.getStreamIndex();
                else return;
            }
            if (mVideoStreamIndex == 0){
                if (mLastPtsWrite == Global.NO_PTS)
                    mLastPtsWrite = event.getTimeStamp() - MICRO_SECONDS_BETWEEN_FRAMES;
                if (event.getTimeStamp() / Global.DEFAULT_PTS_PER_SECOND >= 40 && event.getTimeStamp()/Global.DEFAULT_PTS_PER_SECOND <=199.960) {
                    if (event.getTimeStamp() - mLastPtsWrite >= MICRO_SECONDS_BETWEEN_FRAMES) {
                        //String outputFilename = dumpImageToFile(event.getImage(), event.getTimeStamp());
                        //String outputFilename = dumpImageToMemory(event.getImage());
                        snapShots[count++] = new SnapShot(event.getImage(),event.getTimeStamp());
                        //double seconds= ((double) event.getTimeStamp())/ Global.DEFAULT_PTS_PER_SECOND;
                        //System.out.printf("at elapsed time of %6.3f seconds wrote: %s\n", seconds, outputFilename);
                        mLastPtsWrite += MICRO_SECONDS_BETWEEN_FRAMES;
                    }
                }
            } else {
                // drop audio
            }


        }

        private String dumpImageToMemory(BufferedImage image){

            return null;
        }

        private String dumpImageToFile(BufferedImage image, long timeStamp){
            try{
                String outputFilename = outputFilePrefix + "frame_"+timeStamp+".png";
                ImageIO.write(image, "png", new File(outputFilename));
                return outputFilename;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

    }

    private void updateGallery(SnapShot[] imageObjects) {

        //if (tilePane.getChildren().size() != 0) tilePane.getChildren().removeAll(tilePane.getChildren());
        Platform.runLater(() -> {
            int amount = 1;
            for (final SnapShot object : imageObjects) {
                //if (object.getFilename().equals(this.previewImageObject.getFilename())) continue;
                amount++;
                if (amount%100 == 0){
                    BorderPane borderPane = new BorderPane();
                    //AnchorPane pane = new AnchorPane();
                    CheckBox checkBox = new CheckBox();

                    ImageView imageView;
                    Label label = new Label(object.getFilename());
                    label.setMaxWidth(50);
                    imageView = createImageView(object);
                    borderPane.setCenter(imageView);
                    HBox hBox = new HBox();

                    hBox.getChildren().add(checkBox);
                    hBox.getChildren().add(label);
                    hBox.setSpacing(10);
                    hBox.setPadding(new Insets(2, 0, 2, 0));
                    borderPane.setBottom(hBox);
                    tilePane.getChildren().addAll(borderPane);
                }

            }
        });

    }

    private ImageView createImageView(final SnapShot imageObject) {
        ImageView imageView = null;

        final Image image = SwingFXUtils.toFXImage(imageObject.getBufferedImage(), null);

        imageView = new ImageView(image);
        imageView.setFitHeight(150);
        //imageView.setPreserveRatio(true);

        imageView.setOnMouseClicked(event -> {
            if (event.getButton().equals(MouseButton.PRIMARY)) {

//                if (event.getClickCount() == 2) {
//                    try {
//                        BorderPane borderPane = new BorderPane();
//                        ImageView imageViewPreview = new ImageView();
//                        Image orinalImage = new Image(new FileInputStream(imageObject.getImagePath()));
//                        imageViewPreview.setImage(orinalImage);
//                        //imageView.setStyle("-fx-background-color: BLACK");
//                        imageViewPreview.setFitHeight(mainApp.getPrimaryStage().getHeight() / 2 + 20);
//                        imageViewPreview.setFitWidth(mainApp.getPrimaryStage().getWidth() / 2 + 20);
//                        imageViewPreview.setPreserveRatio(true);
//                        imageViewPreview.setSmooth(true);
//                        imageViewPreview.setCache(true);
//                        borderPane.setCenter(imageViewPreview);
//                        borderPane.setStyle("-fx-background-color: BLACK");
//                        Stage newStage = new Stage();
//                        //newStage.setWidth(image.getWidth() + 20);
//                        //newStage.setHeight(image.getHeight() + 20);
//                        newStage.setTitle(imageObject.getFilename());
//                        Scene scene = new Scene(borderPane, Color.BLACK);
//                        newStage.setScene(scene);
//                        newStage.show();
//                    } catch (FileNotFoundException e) {
//                        e.printStackTrace();
//                    }
//
//                }
            }

        });


        return imageView;
    }

}
