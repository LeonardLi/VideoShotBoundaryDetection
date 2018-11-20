package sample;

import java.awt.image.BufferedImage;

/**
 * Created by liyangde on Nov, 2018
 */
public class SnapShot {
    private BufferedImage bufferedImage;
    private String filename;
    private long timestamp;
    private int[] intensityHistogram = new int[25] ;

    public SnapShot(BufferedImage image, long timestamp){
        this.bufferedImage = image;
        this.filename = "frame_"+timestamp+".png";
        this.timestamp = timestamp;
        initialize();
    }

    private void initialize() {
        for (int i = 0; i < this.bufferedImage.getWidth(); i++) {
            for (int j = 0; j < this.bufferedImage.getHeight(); j++) {
                int rgb = this.bufferedImage.getRGB(i, j);
                int b = rgb & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                double intensity = 0.299 * r + 0.587 * g + 0.114 * b;
                if (intensity < 240) intensityHistogram[((int) intensity / 10)]++;
                else intensityHistogram[24]++;
            }
        }
    }

    public BufferedImage getBufferedImage() {
        return bufferedImage;
    }

    public void setBufferedImage(BufferedImage bufferedImage) {
        this.bufferedImage = bufferedImage;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int[] getIntensityHistogram() {
        return intensityHistogram;
    }

    public void setIntensityHistogram(int[] intensityHistogram) {
        this.intensityHistogram = intensityHistogram;
    }
}
