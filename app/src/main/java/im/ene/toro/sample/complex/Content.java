/*
 * Copyright (c) 2017 Nam Nguyen, nam@ene.im
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.ene.toro.sample.complex;

import android.net.Uri;

/**
 * @author eneim (7/1/17).
 */

public class Content {

  private static final String MP4_BUNNY = "file:///android_asset/bbb.mp4";
  private static final String MP4_TOS = "file:///android_asset/tos.mp4";
  private static final String MP4_COSMOS = "file:///android_asset/cosmos_nosound.mp4";
  private static final String MP4_NOSOUND = "file:///android_asset/cat_videoOnly.mp4";
  private static final String MP3_GUFENG = "file:///android_asset/GuFeng.mp3";

  static final String[] VIDEOS = { MP4_NOSOUND};
  static final String[][] AUDIOARRAYS = { {MP3_GUFENG,MP3_GUFENG}};
  static final String[][] VIDEOARRAYS = {{MP4_NOSOUND, MP4_COSMOS}};

  public static class Media {
    public final int index;
    public final Uri videoUri;
    public final Uri[] videoUris;
    public final Uri[] audioUris;

    public Media(int index,  Uri videoUri,Uri[] videoUris, Uri[] audioUris) {
      this.index = index;
      this.videoUri = videoUri;
      this.videoUris = videoUris;
      this.audioUris = audioUris;
    }

    static Media getItem(int index) {
      if( (VIDEOARRAYS.length != 0) &&  (AUDIOARRAYS.length == VIDEOARRAYS.length)){
        int length = VIDEOARRAYS[index % VIDEOARRAYS.length].length;
        Uri[] videoUris = new Uri[length];
        Uri[] audioUris = new Uri[length];
        for (int i=0; i<length; i++){
          videoUris[i] =  Uri.parse(VIDEOARRAYS[index % VIDEOARRAYS.length][i]);
          audioUris[i] =  Uri.parse(AUDIOARRAYS[index % AUDIOARRAYS.length][i]);
        }
        return new Media(index, null, videoUris,
            audioUris);
      } else {
        return new Media(index, Uri.parse(VIDEOS[index % VIDEOS.length]), null, null);
      }
    }


    @Override public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Media)) return false;

      Media media = (Media) o;

      if (index != media.index) return false;
      if((media.videoUris != null) && (media.audioUris != null)){
        return (videoUris.equals(media.videoUris) && audioUris.equals(media.audioUris));
      } else {
        return videoUri.equals(media.videoUri);
      }
    }

    @Override public int hashCode() {
      int result = index;
      if(videoUris != null) {
        result = 31 * result + videoUris.hashCode();
      } else {
        result = 31 * result + videoUri.hashCode();
      }
      return result;
    }
  }
}
