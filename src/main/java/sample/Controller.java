package sample;

import com.xuggle.mediatool.*;
import com.xuggle.mediatool.event.IVideoPictureEvent;
import com.xuggle.xuggler.*;
import com.xuggle.xuggler.demos.*;
import com.xuggle.xuggler.demos.DecodeAndPlayAudioAndVideo;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.File;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Controller implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    public static final double SECONDS_BETWEEN_FRAMES = 0.04;
    private static final String outputFilePrefix = "snapshots/";
    private static final String LEFT_ICON = "/Actions-arrow-left-icon.png";
    private static final String RIGHT_ICON = "/Actions-arrow-right-icon.png";
    private static final double speed = 0.10;

    private static int mVideoStreamIndex = -1;
    private static long mLastPtsWrite = Global.NO_PTS;
    public static final long MICRO_SECONDS_BETWEEN_FRAMES = (long)(Global.DEFAULT_PTS_PER_SECOND * SECONDS_BETWEEN_FRAMES);
    public SnapShot[]snapShots = new SnapShot[4000];

    private double[] sds = new double[4000];

    private double Tb = 0.0f;
    private double Ts = 0.0f;
    private int Tor = 2;


    private HBox hBox;

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
    @FXML
    private AnchorPane button_box;

    @FXML
    private ImageView mediaplayer;

    private Thread thread;
    private List<VideoPlayer> threads = new ArrayList<>();
    private VideoPlayer videoPlayer;




    private HashMap<Integer,Integer> Cuts = new HashMap<>();
    private HashMap<Integer,Integer> Fade = new HashMap<>();
    private HashMap<Integer,Integer> FinalFade = new HashMap<>();

    private Main mainApp;

    public void setMainApp(Main mainApp) {
        this.mainApp = mainApp;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //DecodeAndCaptureFrames capturer = new DecodeAndCaptureFrames();
        //DecodeAndPlayAudioAndVideo.main(getClass().getResource("/20020924_juve_dk_02a.avi").getPath());
        //DecodeAndPlayVideo
        IMediaReader mediaReader = ToolFactory.makeReader(getClass().getResource("/20020924_juve_dk_02a.avi").getPath());
        //MediaPlayer player = new MediaPlayer();

        IContainer container = IContainer.make();
        int result = container.open(getClass().getResource("/20020924_juve_dk_02a.mpg").getPath(),IContainer.Type.READ,null);
        if (result < 0) throw new RuntimeException("Failed to open media file");


        int numStreams = container.getNumStreams(); // query how many streams the call to open found
        long duration = container.getDuration();// query for the total duration
        long fileSize = container.getFileSize();// query for the file size
        long bitRate = container.getBitRate();  // query for the bit rate
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
        }

        label_filename.setText("20020924_juve_dk_02a.avi");
        label_streams.setText(numStreams+"");
        label_durations.setText(duration+"");
        label_filesize.setText(fileSize+"");
        label_bitrate.setText(bitRate+"");
        text_streaminfo.appendText(textArea);

        hBox = new HBox();
        hBox.setSpacing(10);
        //tilePane = new TilePane();


        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setFitToHeight(true);
        scrollPane.setContent(hBox);
        //addButtons(scrollPane, button_box);

        try{
            FileInputStream fis = new FileInputStream(getClass().getResource("/snapshots/frame_39987900.png").getPath());
            Image image = new Image(fis);
            mediaplayer.setImage(image);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


        Platform.runLater(()->{
            //set bufferedimages created in 24bit color space
            mediaReader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
            mediaReader.addListener(new ImageSnapListener());
            while (mediaReader.readPacket() == null);
            calculateCut();
            updateGallery(snapShots);

        });
        videoPlayer = new VideoPlayer(mediaplayer);
        thread = new Thread(videoPlayer);

    }

    private void calculateCut() {
        //calculate sds
        for (int i = 0; i < 3999; i++) {
            for (int j = 0; j < 25; j++) {
                sds[i] += Math.abs(snapShots[i+1].getIntensityHistogram()[j] - snapShots[i].getIntensityHistogram()[j]);
            }
        }

        Mean mean = new Mean();
        StandardDeviation std = new StandardDeviation();
        double stdValue = std.evaluate(sds);
        double meanValue = mean.evaluate(sds);

        // Tb
        Tb = meanValue + 11 * stdValue;

        // Ts
        Ts = 2 * meanValue;

        // Determine Cs,Ce,Fs,Fe
        int current_start = 0;
        int current_tor = 0;
        boolean inLoop = false;
        for (int i = 0; i < 3999; i++) {
            if(sds[i] >= Tb) {
                Cuts.put(i, i+1);
                if (inLoop) {
                    inLoop = false;
                    if (current_start != i-1)
                    Fade.put(current_start,i-1);
                }
            }
            if (sds[i] < Tb && sds[i] >= Ts) {
                if (!inLoop){
                    inLoop = true;
                    current_start = i;
                }

            }
            if (sds[i] < Ts) {
                if (inLoop){
                        current_tor++;
                        if (current_tor >= Tor){
                            current_tor = 0;
                            inLoop = false;
                            Fade.put(current_start, i);
                        }
                }

            }
        }

        for (int key : Fade.keySet()) {
            int start = key;
            int end = Fade.get(key);
            double sum = 0l;
            for (int i = start; i <= end; i++) {
                sum+=sds[i];
            }
            if (sum >= Tb){
                FinalFade.put(key, Fade.get(key));
            }
        }

        List<Integer> keys = new ArrayList<>(Cuts.keySet());
        List<Integer> keys2 = new ArrayList<>(FinalFade.keySet());
        Collections.sort(keys);
        Collections.sort(keys2);
//        //Cut
//        System.out.println("Ce:");
//        keys.forEach(key -> {
//            System.out.println(key+1000+"-"+(Cuts.get(key)+1000));
//        });
//
//        //Fade
//        System.out.println("Fs:");
//        keys2.forEach(key -> {
//            System.out.println(key+1000+"-"+(FinalFade.get(key)+1000));
//        });

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

                if ((double)event.getTimeStamp() / Global.DEFAULT_PTS_PER_SECOND >= 39.96/*40.88,40*/ && (double)event.getTimeStamp()/Global.DEFAULT_PTS_PER_SECOND <= 199.92/*200.84,199.960*/) {
                    if ((double)event.getTimeStamp() - mLastPtsWrite >= MICRO_SECONDS_BETWEEN_FRAMES) {
                        //String outputFilename = dumpImageToFile(event.getImage(), event.getTimeStamp());
                        //String outputFilename = dumpImageToMemory(event.getImage());
                        snapShots[count] = new SnapShot(event.getImage(),event.getTimeStamp(), count);
                        count++;
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

        Platform.runLater(() -> {

            for (int i  = 0; i < imageObjects.length; i++) {
                if ( Cuts.keySet().contains(i) || FinalFade.keySet().contains(i-1)){
                    System.out.println(i);
                    System.out.println(imageObjects[i].getId());
                    ImageView imageView = createImageView(imageObjects[i]);
                    imageView.setFitHeight(160);
                    imageView.setPreserveRatio(true);
                    imageView.setSmooth(true);
                    imageView.setCache(true);
                    hBox.getChildren().addAll(imageView);
                }

            }
        });

    }

    private ImageView createImageView(final SnapShot imageObject) {
        ImageView imageView;

        final Image image = SwingFXUtils.toFXImage(imageObject.getBufferedImage(), null);

        imageView = new ImageView(image);
        imageView.setFitHeight(150);
        //imageView.setPreserveRatio(true);

        imageView.setOnMouseClicked(event -> {
            //play the video
            if (event.getButton().equals(MouseButton.PRIMARY)) {
                if (event.getClickCount() == 2) {
                    int start;
                    int end;
                    //System.out.println("=========");
                    //System.out.println(imageObject.getFilename().substring(6,14));
                    System.out.println(imageObject.getId());
                    if (Cuts.containsKey(imageObject.getId())){
                        start = imageObject.getId();
                        end = imageObject.getId()+1;
                    } else {
                        start = imageObject.getId() - 1;
                        end = FinalFade.get(imageObject.getId() - 1);
                    }

                    System.out.println(thread.getId()+thread.getState().toString());
                    if (thread.getState() == Thread.State.RUNNABLE || thread.getState() == Thread.State.TIMED_WAITING) {
                        //playing other shots
                        threads.forEach(thread -> thread.terminate());

                    }
                    videoPlayer = new VideoPlayer(mediaplayer);
                    videoPlayer.setDurationAndStatus(start, end);
                    thread = new Thread(videoPlayer);
                    threads.add(videoPlayer);
                    thread.start();

                }

            }

        });


        return imageView;
    }

    private void addButtons(final ScrollPane scrollPane, final AnchorPane buttonBox) {
        Button right = new Button();
        right.setPrefSize(50, 150);
        right.setGraphic(new ImageView(new Image(RIGHT_ICON)));
        //Making the scroll move right
        right.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent arg0) {
                scrollPane.setHvalue(scrollPane.getHvalue() + speed);
            }
        });

        Button left = new Button("Left");
        left.setPrefSize(50, 150);
        left.setGraphic(new ImageView(new Image(LEFT_ICON)));
        //Making the scroll move left
        left.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent arg0) {
                scrollPane.setHvalue(scrollPane.getHvalue() - speed);
            }
        });


        buttonBox.setLeftAnchor(left, 1.0);
        buttonBox.setRightAnchor(right, 1.0);
        buttonBox.getChildren().addAll(left,right);
    }

    private void createVideo(int start, int end){
        final IMediaWriter writer = ToolFactory.makeWriter(start+"_"+end);
        long startTime = System.nanoTime();
        for(int index = start; index < end ; index++) {
            BufferedImage bgr = convertToType(snapShots[index].getBufferedImage(), BufferedImage.TYPE_3BYTE_BGR);
            writer.encodeVideo(0, bgr, System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            try {
                Thread.sleep((long) (1000/25));
            } catch (InterruptedException e) {

            }
        }
        writer.close();
    }

    private BufferedImage convertToType(BufferedImage sourceImage, int targetType) {
        BufferedImage image;

        if (sourceImage.getType() == targetType) {
            image = sourceImage;
        } else {
            image = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(), targetType);
            image.getGraphics().drawImage(sourceImage, 0, 0, null);
        }
        return image;
    }

    private class VideoPlayer implements Runnable{
        ImageView view;
        private volatile boolean running = true;
        int start;
        int end;
        public VideoPlayer(ImageView view){
            this.view = view;
        }
        public void terminate() {
            this.running = false;
        }

        @Override
        public void run() {
            int i = start;
            while (running) {
                try{
                    //play the video
                    synchronized (view){
                        view.setImage(SwingFXUtils.toFXImage(snapShots[i++].getBufferedImage(), null));
                        if (i == end) i = start;
                        view.setCache(true);
                        Thread.sleep(40);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            System.out.println("Thread "+Thread.currentThread().getId()+" stopped");
        }

        public void setDurationAndStatus(int start, int end){
            this.start = start;
            this.end = end;
            this.running = true;
        }

    }
}
