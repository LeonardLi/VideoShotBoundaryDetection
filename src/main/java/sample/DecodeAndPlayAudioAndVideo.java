package sample;

/**
 * Created by liyangde on Dec, 2018
 */
import com.xuggle.mediatool.IMediaReader;
import com.xuggle.mediatool.IMediaViewer;
import com.xuggle.mediatool.MediaListenerAdapter;
import com.xuggle.mediatool.ToolFactory;

public class DecodeAndPlayAudioAndVideo extends MediaListenerAdapter {
    public DecodeAndPlayAudioAndVideo() {
    }

    public static void main(String[] args) {
        if (args.length <= 0) {
            throw new IllegalArgumentException("must pass in a filename as the first argument");
        } else {
            IMediaReader reader = ToolFactory.makeReader("20020924_juve_dk_02a.avi");

            reader.addListener(ToolFactory.makeViewer(true));

            while(reader.readPacket() == null) {
            }

        }
    }
}
