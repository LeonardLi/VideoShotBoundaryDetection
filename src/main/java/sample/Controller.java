package sample;

import com.xuggle.mediatool.IMediaWriter;
import com.xuggle.mediatool.ToolFactory;
import com.xuggle.xuggler.*;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.*;


public class Controller implements Initializable {
    private static final Logger logger = LoggerFactory.getLogger(Controller.class);

    private static SourceDataLine mLine;

    private static final String outputFilePrefix = "snapshots/";

    private static int FRAME_START;
    private static int FRAME_END;


    public SnapShot[]snapShots;

    private double[] sds;
    private double Tb = 0.0f;
    private double Ts = 0.0f;
    private int Tor = 2;

    @FXML
    private HBox hBox;

    private ProgressBar pb;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private Button button_choose;

    @FXML
    private Button button_play;

    @FXML
    private Button button_pause;

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
    private ImageView mediaplayer;

    @FXML
    private ProgressBar progressbar_videoPlay;

    private Thread videoThread;
    private Thread audioThread;
    private List<VideoPlayer> videoThreads = new ArrayList<>();
    private List<AudioPlayer> audioThreads = new ArrayList<>();
    private VideoPlayer videoPlayer;
    private AudioPlayer audioPlayer;

    private HashMap<Integer,Integer> Cuts;
    private HashMap<Integer,Integer> Fade;
    private HashMap<Integer,Integer> FinalFade;
    private LinkedList<Integer> Starters;

    private Main mainApp;
    private IStreamCoder audioCoder;
    private IStreamCoder videoCoder;
    private int audioStreamId = -1;
    private int videoStreamId = -1;
    private IContainer container;

    private Map<Long,IAudioSamples> audioSamplesMap;
    private List<Long> audioTimeStamps;

    public void setMainApp(Main mainApp) {
        this.mainApp = mainApp;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        //DecodeAndCaptureFrames capturer = new DecodeAndCaptureFrames();
        //DecodeAndPlayAudioAndVideo.main(getClass().getResource("/20020924_juve_dk_02a.avi").getPath());

        //IMediaReader mediaReader = ToolFactory.makeReader(getClass().getResource("/20020924_juve_dk_02a.avi").getPath());

        hBox.setSpacing(10);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setFitToHeight(true);

        button_choose.setOnMouseClicked(event -> {
            //shutdown exist
            if (audioPlayer != null) {
                audioPlayer.terminate();
            }
            if (videoPlayer != null) {
                videoPlayer.terminate();
            }
            try{
                FileInputStream fis = new FileInputStream(getClass().getResource("/snapshots/bg.png").getPath());
                Image image = new Image(fis);
                mediaplayer.setImage(image);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            progressbar_videoPlay.setProgress(0.0);
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Choose Video File");


            File file = fileChooser.showOpenDialog(mainApp.getPrimaryStage());
            if (file != null) {
                loadFile(file);
            }
        });

        button_play.setOnMouseClicked(event -> {
            if (videoPlayer.isPaused()) {
                videoPlayer.resume();
                audioPlayer.resume();
            }
        });

        button_pause.setOnMouseClicked(event -> {
            if (!videoPlayer.isPaused()) {
                videoPlayer.pause();
                audioPlayer.pause();
            }
        });
        try{
            FileInputStream fis = new FileInputStream(getClass().getResource("/snapshots/bg.png").getPath());
            Image image = new Image(fis);
            mediaplayer.setImage(image);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        progressbar_videoPlay.setProgress(0.0);


//        Platform.runLater(()->{
//            //set bufferedimages created in 24bit color space
//            //mediaReader.setBufferedImageTypeToGenerate(BufferedImage.TYPE_3BYTE_BGR);
//            //mediaReader.addListener(new ImageSnapListener());
//            //mediaReader.addListener(new VideoListener());
//            //while (mediaReader.readPacket() == null);
//            doCapture();
//            calculateCut();
//            updateGallery(snapShots);
//            Collections.sort(audioTimeStamps);
//        });

    }

    private void loadFile(final File file) {
        container = IContainer.make();
        int result = container.open(file.getPath(),IContainer.Type.READ,null);
        if (result < 0) throw new RuntimeException("Failed to open media file");
        FRAME_START = Integer.valueOf(text_framestart.getText());
        FRAME_END = Integer.valueOf(text_frameend.getText());
        if (FRAME_START >= FRAME_END) throw new RuntimeException("illegal input");
        snapShots = new SnapShot[FRAME_END - FRAME_START + 1];
        sds = new double[FRAME_END - FRAME_START + 1];

        int numStreams = container.getNumStreams(); // query how many streams the call to open found
        long duration = container.getDuration();// query for the total duration
        long fileSize = container.getFileSize();// query for the file size
        long bitRate = container.getBitRate();  // query for the bit rate
        String textArea = "";

        for(int i = 0; i < numStreams; ++i) {
            IStream stream = container.getStream((long)i);
            IStreamCoder coder = stream.getStreamCoder();
            if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_VIDEO) {
                videoStreamId = i;
                videoCoder = coder;
            }
            if (coder.getCodecType() == ICodec.Type.CODEC_TYPE_AUDIO) {
                audioStreamId = i;
                audioCoder = coder;
            }
            textArea += "stream : " + i + "\n" +
                    "type: " + coder.getCodecType() +"\n"+
                    "codec: "+ coder.getCodecID() +"\n"+
                    "durarion: "+ stream.getDuration() + "\n"+
                    "start time: "+ container.getStartTime()+"\n"+
                    "timebase: "+ stream.getTimeBase().getNumerator()+"/"+ stream.getTimeBase().getDenominator()+"\n"+
                    "format: "+ coder.getPixelType()+"\n"+
                    "frame-rate: "+ coder.getFrameRate().getDouble()+"\n";
        }



        label_filename.setText(file.getName());
        label_streams.setText(numStreams+"");
        label_durations.setText(duration/(1000*60)+" MIN");
        label_filesize.setText((int)(fileSize/(1024*1024))+" MB");
        label_bitrate.setText(bitRate+"");
        text_streaminfo.appendText(textArea);
        text_streaminfo.setEditable(false);

        pb = new ProgressBar();
        pb.setPrefWidth(1024);
        scrollPane.setContent(pb);
        pb.setProgress(0.0);

        new Thread(new Runnable() {
            @Override
            public void run() {
                doCapture();
                calculateCut();
                updateGallery(snapShots);
                Collections.sort(audioTimeStamps);
            }
        }).start();
        videoPlayer = new VideoPlayer(mediaplayer);
        videoThread = new Thread(videoPlayer);
        audioPlayer = new AudioPlayer();
        audioThread = new Thread(audioPlayer);
        openSound(container.getStream(audioStreamId).getStreamCoder());

    }
    private void calculateCut() {
        //calculate sds
        for (int i = 0; i < (FRAME_END - FRAME_START); i++) {
            for (int j = 0; j < 25; j++) {
                sds[i] += Math.abs(snapShots[i+1].getIntensityHistogram()[j] - snapShots[i].getIntensityHistogram()[j]);
            }
            pb.setProgress((double) i/(FRAME_END - FRAME_START + 1)*0.3+0.7);
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
        Cuts = new HashMap<>();
        Fade = new HashMap<>();
        FinalFade = new HashMap<>();
        Starters = new LinkedList<>();
        for (int i = 0; i < FRAME_END - FRAME_START; i++) {
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
            if (sum >= Tb*0.7){
                FinalFade.put(key, Fade.get(key));
            }
        }

        List<Integer> keys = new ArrayList<>(Cuts.keySet());
        List<Integer> keys2 = new ArrayList<>(FinalFade.keySet());
        Starters.addAll(keys);
        keys2.forEach( key -> {
            Starters.add(key+1);
        });
        Collections.sort(Starters);
        Collections.sort(keys);
        Collections.sort(keys2);
        //Cut
        System.out.println("Ce:");
        keys.forEach(key -> {
            System.out.println(key+FRAME_START+"-"+(Cuts.get(key)+FRAME_START));
        });

        //Fade
        System.out.println("Fs:");
        keys2.forEach(key -> {
            System.out.println(key+FRAME_START+"-"+(FinalFade.get(key)+FRAME_START));
        });

    }

//    private class VideoListener extends MediaListenerAdapter {
//        IPacket packet = IPacket.make();
//        @Override
//        public void onAudioSamples(IAudioSamplesEvent event) {
//            if (event.getStreamIndex() != mAudioStreamIndex) {
//                if (mAudioStreamIndex == -1){
//                    mAudioStreamIndex = event.getStreamIndex();
//                    Controller.this.openSound(Controller.this.audioStreamCoder);
//                }
//                else return;
//            }
//            if (mAudioStreamIndex == 1) {
//                if ((double)event.getTimeStamp() / Global.DEFAULT_PTS_PER_SECOND >= 39.96 && (double)event.getTimeStamp()/Global.DEFAULT_PTS_PER_SECOND <= 199.92) {
//                    //System.out.println(Arrays.toString(event.getMediaData().getData().getByteArray(0, event.getAudioSamples().getSize())));
//                    //System.out.println("Audio"+ event.getTimeStamp());
//                    soundData.put(event.getTimeStamp(),
//                            event.getMediaData().getData().getByteArray(0, event.getAudioSamples().getSize()));
//
//                }
//            }
//        }
//    }

//    private class ImageSnapListener extends MediaListenerAdapter {
//        int count = 0;
//        @Override
//        public void onVideoPicture(IVideoPictureEvent event) {
//            if (event.getStreamIndex() != mVideoStreamIndex){
//                if(mVideoStreamIndex == -1) mVideoStreamIndex = event.getStreamIndex();
//                else return;
//            }
//            if (mVideoStreamIndex == 0){
//                if (mLastPtsWrite == Global.NO_PTS)
//                    mLastPtsWrite = event.getTimeStamp() - MICRO_SECONDS_BETWEEN_FRAMES;
//
//                if ((double)event.getTimeStamp() / Global.DEFAULT_PTS_PER_SECOND >= 39.96/*40.88,40*/ && (double)event.getTimeStamp()/Global.DEFAULT_PTS_PER_SECOND <= 199.92/*200.84,199.960*/) {
//                    if ((double)event.getTimeStamp() - mLastPtsWrite >= MICRO_SECONDS_BETWEEN_FRAMES) {
//                        //String outputFilename = dumpImageToFile(event.getImage(), event.getTimeStamp());
//                        //String outputFilename = dumpImageToMemory(event.getImage());
//                        snapShots[count] = new SnapShot(event.getImage(),event.getTimeStamp(), count);
//                        count++;
//                        //double seconds= ((double) event.getTimeStamp())/ Global.DEFAULT_PTS_PER_SECOND;
//                        //System.out.printf("at elapsed time of %6.3f seconds wrote: %s\n", seconds, outputFilename);
//                        mLastPtsWrite += MICRO_SECONDS_BETWEEN_FRAMES;
//                    }
//                }
//            }
//
//
//        }
//
//        private String dumpImageToMemory(BufferedImage image){
//            return null;
//        }
//
//        private String dumpImageToFile(BufferedImage image, long timeStamp){
//            try{
//                String outputFilename = outputFilePrefix + "frame_"+timeStamp+".png";
//                ImageIO.write(image, "png", new File(outputFilename));
//                return outputFilename;
//            } catch (IOException e) {
//                e.printStackTrace();
//                return null;
//            }
//        }
//
//    }

    private void updateGallery(SnapShot[] imageObjects) {

        Platform.runLater(() -> {
            scrollPane.setContent(hBox);
            for (int i  = 0; i < imageObjects.length; i++) {
                if ( Cuts.keySet().contains(i) || FinalFade.keySet().contains(i-1)){
                    //System.out.println(i);
                    //System.out.println(imageObjects[i].getId());
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

    private void doCapture(){
        audioSamplesMap = new HashMap<>();
        audioTimeStamps = new LinkedList<>();
        if (videoStreamId == -1 || audioStreamId == -1) {
            throw new RuntimeException("could not find video or audio stream in container: ");
        } else if (videoCoder.open() < 0) {
            throw new RuntimeException("could not open video decoder for container: ");
        } else if (audioCoder.open() < 0) {
            throw new RuntimeException("could not open audio decoder for container: ");
        } else {
            IVideoResampler videoResampler = null;

            if (videoCoder.getPixelType() != IPixelFormat.Type.BGR24) {
                videoResampler = IVideoResampler.make(videoCoder.getWidth(), videoCoder.getHeight(), com.xuggle.xuggler.IPixelFormat.Type.BGR24, videoCoder.getWidth(), videoCoder.getHeight(), videoCoder.getPixelType());
                if (videoResampler == null) {
                    throw new RuntimeException("could not create color space resampler for: ");
                }
            }

            IPacket packet = IPacket.make();
            int count = 0;


            while (true) {
                IVideoPicture picture;
                label123:
                do {
                    while(container.readNextPacket(packet) >= 0) {

                        int offset = 0;
                        if (packet.getStreamIndex() == videoStreamId){
                            picture = IVideoPicture.make(videoCoder.getPixelType(), videoCoder.getWidth(), videoCoder.getHeight());
                            offset = videoCoder.decodeVideo(picture, packet, 0);
                            if (offset < 0) {

                            }
                            continue label123;
                        }
                        if (packet.getStreamIndex() == audioStreamId) {
                            if(count >= FRAME_START && count <= FRAME_END){
                                IAudioSamples samples = IAudioSamples.make(1024L, (long) audioCoder.getChannels());
                                offset = 0;
                                while (offset < packet.getSize()) {
                                    int bytesDecoded = audioCoder.decodeAudio(samples, packet, offset);
                                    if (bytesDecoded < 0) {

                                    }
                                    offset += bytesDecoded;

                                    if (samples.isComplete()) {
                                        //store
                                        audioSamplesMap.put(samples.getTimeStamp(), samples);
                                        audioTimeStamps.add(samples.getTimeStamp());
                                    }

                                }
                            }
                        }
                    }

                    if (videoCoder != null) {
                        videoCoder.close();
                        videoCoder = null;
                    }
                    if (container != null) {
                        container.close();
                        container = null;
                    }
                    if (container != null) {
                        container.close();
                        container = null;
                    }

                    return;

                } while (!picture.isComplete());

                IVideoPicture newPic = picture;

                if (count >= FRAME_START && count <= FRAME_END){

                    if (videoResampler != null) {
                        newPic = IVideoPicture.make(videoResampler.getOutputPixelFormat(), picture.getWidth(), picture.getHeight());
                        if (videoResampler.resample(newPic, picture) < 0) {

                        }
                    }

                    if (newPic.getPixelType() != IPixelFormat.Type.BGR24) {

                    }
                    BufferedImage image = Utils.videoPictureToImage(newPic);
                    snapShots[count - FRAME_START] = new SnapShot(image, picture.getTimeStamp(),count - FRAME_START);
                }
                count++;
                pb.setProgress((double)count/(FRAME_END - FRAME_START + 1) * 0.7);
            }
        }


    }

    private void openSound (IStreamCoder aAudioCoder){
        AudioFormat audioFormat = new AudioFormat((float) aAudioCoder.getSampleRate(),
                (int)IAudioSamples.findSampleBitDepth(aAudioCoder.getSampleFormat()),
                aAudioCoder.getChannels(),
                true,
                false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

        try {
            mLine = (SourceDataLine) AudioSystem.getLine(info);
            mLine.open(audioFormat);
            mLine.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private void playSound(IAudioSamples aSamples) {
        byte[] rawBytes = aSamples.getData().getByteArray(0, aSamples.getSize());
        mLine.write(rawBytes, 0, aSamples.getSize());

    }

    public void closeJavaSound() {
        if (mLine != null) {
            mLine.drain();
            mLine.close();
            mLine = null;
        }
    }

    /**
     * @param imageObject
     * @return
     */
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

                    start = imageObject.getId();
                    int nextIndex = Starters.indexOf(start)+1;
                    if (nextIndex >= Starters.size()) end = FRAME_END - FRAME_START;
                    else end = Starters.get(nextIndex) - 1;
                    System.out.println("Start: "+start+" end: "+end);
                    long startTimeStamp =  imageObject.getTimestamp();
                    long endTimeStamp = snapShots[end].getTimestamp();

                    //System.out.println(thread.getId()+thread.getState().toString());

                    if (videoThread.getState() == Thread.State.RUNNABLE || videoThread.getState() == Thread.State.TIMED_WAITING) {
                        //playing other shots
                        videoThreads.forEach(thread -> thread.terminate());
                        videoThreads.clear();

                    }
                    if (audioThread.getState() == Thread.State.RUNNABLE || audioThread.getState() == Thread.State.TIMED_WAITING) {
                        //playing other shots
                        audioThreads.forEach(thread -> thread.terminate());
                        audioThreads.clear();
                    }

                    videoPlayer = new VideoPlayer(mediaplayer);
                    audioPlayer = new AudioPlayer();
                    videoPlayer.setDurationAndStatus(start, end);
                    audioPlayer.setTimeStamps(startTimeStamp, endTimeStamp);

                    videoThread = new Thread(videoPlayer);
                    audioThread = new Thread(audioPlayer);
                    audioThreads.add(audioPlayer);
                    videoThreads.add(videoPlayer);
                    videoThread.start();
                    audioThread.start();

                }

            }

        });


        return imageView;
    }

    class VideoPlayer implements Runnable{
        ImageView view;
        private volatile boolean running = true;
        private volatile boolean paused = false;
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
                        if (!paused){
                            view.setImage(SwingFXUtils.toFXImage(snapShots[i++].getBufferedImage(), null));
                            if (i == end) i = start;
                            view.setCache(true);
                            progressbar_videoPlay.setProgress((double) (i-start)/(end-start));
                            Thread.sleep(40);
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
            System.out.println("Thread "+Thread.currentThread().getId()+" stopped");
        }
        public void pause() {
            this.paused = true;
        }

        public void resume() {
            this.paused = false;
        }
        public void setDurationAndStatus(int start, int end){
            this.start = start;
            this.end = end;
            this.running = true;
        }

        public boolean isPaused() {
            return paused;
        }
    }

    class AudioPlayer implements Runnable{
        private volatile boolean running = true;
        private volatile boolean paused = false;
        private long startTimestamp;
        private long endTimestamp;
        @Override
        public void run() {
            int startIndex = 0;
            int endIndex = 0;
            int index;
            boolean foundStart = false;
            boolean foundEnd = false;

            for (long timestamp : Controller.this.audioTimeStamps) {
                if (!foundStart && timestamp >= startTimestamp) {
                    //startTimestamp = timestamp;
                    startIndex = Controller.this.audioTimeStamps.indexOf(timestamp);
                    foundStart = true;
                }
                if (!foundEnd && timestamp>= endTimestamp) {
                    //endTimestamp = endTimestamp;
                    endIndex = Controller.this.audioTimeStamps.indexOf(timestamp);
                    foundEnd = true;
                    break;
                }
            }
            if (!foundEnd) {
                endIndex = Controller.this.audioTimeStamps.size() - 1;
            }

            System.out.println("start audio:"+startIndex+" "+endIndex);
            index = startIndex;
            while (running) {
                if (!paused) {
                    Controller.this.playSound(Controller.this.audioSamplesMap.get(Controller.this.audioTimeStamps.get(index++)));
                    if (index == endIndex) index = startIndex;
                }
            }
        }

        public void terminate() {
            this.running = false;
        }

        public void pause() {
            this.paused = true;
        }

        public void resume() {
            this.paused = false;
        }

        public boolean isPaused() {
            return paused;
        }

        public void setTimeStamps(long startTimestamp, long endTimestamp){
            this.startTimestamp = startTimestamp;
            this.endTimestamp = endTimestamp;
        }
    }

    public VideoPlayer getVideoPlayer() {
        return videoPlayer;
    }

    public AudioPlayer getAudioPlayer() {
        return audioPlayer;
    }


}
