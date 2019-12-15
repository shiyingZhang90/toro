/*
 * Copyright (c) 2018 Nam Nguyen, nam@ene.im
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

package im.ene.toro.exoplayer;

import android.net.Uri;
import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import im.ene.toro.ToroPlayer;
import im.ene.toro.ToroPlayer.VolumeChangeListeners;
import im.ene.toro.ToroUtil;
import im.ene.toro.media.PlaybackInfo;
import im.ene.toro.media.VolumeInfo;

import static im.ene.toro.ToroUtil.checkNotNull;
import static im.ene.toro.exoplayer.ToroExo.with;
import static im.ene.toro.media.PlaybackInfo.INDEX_UNSET;
import static im.ene.toro.media.PlaybackInfo.TIME_UNSET;

/**
 * [20180225]
 *
 * Default implementation of {@link Playable}.
 *
 * Instance of {@link Playable} should be reusable. Retaining instance of Playable across config
 * change must guarantee that all {@link EventListener} are cleaned up on config change.
 *
 * @author eneim (2018/02/25).
 */
@SuppressWarnings("WeakerAccess") //
class PlayableImpl implements Playable {

  private final PlaybackInfo playbackInfo = new PlaybackInfo(); // never expose to outside.

  protected final EventListeners listeners = new EventListeners();  // original listener.
  protected final VolumeChangeListeners volumeChangeListeners = new VolumeChangeListeners();
  protected final ToroPlayer.ErrorListeners errorListeners = new ToroPlayer.ErrorListeners();

  protected final Uri videoUri; // immutable, parcelable
  protected final Uri[] videoUris; // immutable, parcelable
  protected final Uri audioUri;
  protected final String fileExt;
  protected final ExoCreator creator; // required, cached

  protected SimpleExoPlayer player; // on-demand, cached
  protected MediaSource videoSource;  // on-demand, since we do not reuse MediaSource now.
  protected ConcatenatingMediaSource concatenatingMediaSource;  // on-demand, since we do not reuse MediaSource now.
  protected MediaSource audioSource;  // on-demand, since we do not reuse MediaSource now.
  protected PlayerView playerView; // on-demand, not always required.

  private boolean sourcePrepared = false;
  private boolean listenerApplied = false;

  PlayableImpl(ExoCreator creator, Uri videoUri,Uri audioUri, String fileExt) {
    this.creator = creator;
    this.videoUri = videoUri;
    this.videoUris = null;
    this.audioUri = audioUri;
    this.fileExt = fileExt;
  }

  PlayableImpl(ExoCreator creator, Uri[] videoUris,Uri audioUri, String fileExt) {
    this.creator = creator;
    this.videoUri = null;
    this.videoUris = videoUris;
    this.audioUri = audioUri;
    this.fileExt = fileExt;
  }

  PlayableImpl(ExoCreator creator, Uri videoUri, String fileExt) {
    this.creator = creator;
    this.videoUri = videoUri;
    this.videoUris = null;
    this.audioUri = null;
    this.fileExt = fileExt;
  }

  @CallSuper @Override public void prepare(boolean prepareSource) {
    if (prepareSource) {
      ensureMediaSource();
      ensurePlayerView();
    }
  }

  @CallSuper @Override public void setPlayerView(@Nullable PlayerView playerView) {
    if (this.playerView == playerView) return;
    if (playerView == null) {
      this.playerView.setPlayer(null);
    } else {
      if (this.player != null) {
        PlayerView.switchTargetView(this.player, this.playerView, playerView);
      }
    }

    this.playerView = playerView;
  }

  @Override public final PlayerView getPlayerView() {
    return this.playerView;
  }

  @CallSuper @Override public void play() {
    ensureMediaSource();
    ensurePlayerView();
    checkNotNull(player, "Playable#play(): Player is null!");
    player.setPlayWhenReady(true);
  }

  @CallSuper @Override public void pause() {
    // Player is not required to be non-null here.
    if (player != null) player.setPlayWhenReady(false);
  }

  @CallSuper @Override public void reset() {
    this.playbackInfo.reset();
    if (player != null) {
      // reset volume to default
      ToroExo.setVolumeInfo(this.player, new VolumeInfo(false, 1.f));
      player.stop(true);
    }
    this.concatenatingMediaSource = null;
    this.videoSource = null; // so it will be re-prepared when play() is called.
    this.audioSource = null; // so it will be re-prepared when play() is called.
    this.sourcePrepared = false;
  }

  @CallSuper @Override public void release() {
    this.setPlayerView(null);
    if (this.player != null) {
      // reset volume to default
      ToroExo.setVolumeInfo(this.player, new VolumeInfo(false, 1.f));
      this.player.stop(true);
      if (listenerApplied) {
        player.removeListener(listeners);
        player.removeVideoListener(listeners);
        player.removeTextOutput(listeners);
        player.removeMetadataOutput(listeners);
        if (this.player instanceof ToroExoPlayer) {
          ((ToroExoPlayer) this.player).removeOnVolumeChangeListener(this.volumeChangeListeners);
        }
        listenerApplied = false;
      }
      with(checkNotNull(creator.getContext(), "ExoCreator has no Context")) //
          .releasePlayer(this.creator, this.player);
    }
    this.player = null;
    this.concatenatingMediaSource = null;
    this.videoSource = null; // so it will be re-prepared when play() is called.
    this.audioSource = null; // so it will be re-prepared when play() is called.
    this.sourcePrepared = false;
  }

  @CallSuper @NonNull @Override public PlaybackInfo getPlaybackInfo() {
    updatePlaybackInfo();
    return new PlaybackInfo(playbackInfo.getResumeWindow(), playbackInfo.getResumePosition(),
        playbackInfo.getVolumeInfo());
  }

  @CallSuper @Override public void setPlaybackInfo(@NonNull PlaybackInfo playbackInfo) {
    this.playbackInfo.setResumeWindow(playbackInfo.getResumeWindow());
    this.playbackInfo.setResumePosition(playbackInfo.getResumePosition());
    this.setVolumeInfo(playbackInfo.getVolumeInfo());

    if (player != null) {
      ToroExo.setVolumeInfo(player, this.playbackInfo.getVolumeInfo());
      boolean haveResumePosition = this.playbackInfo.getResumeWindow() != INDEX_UNSET;
      if (haveResumePosition) {
        player.seekTo(this.playbackInfo.getResumeWindow(), this.playbackInfo.getResumePosition());
      }
    }
  }

  @Override public final void addEventListener(@NonNull EventListener listener) {
    //noinspection ConstantConditions
    if (listener != null) this.listeners.add(listener);
  }

  @Override public final void removeEventListener(EventListener listener) {
    this.listeners.remove(listener);
  }

  @CallSuper @Override public void setVolume(float volume) {
    checkNotNull(player, "Playable#setVolume(): Player is null!");
    playbackInfo.getVolumeInfo().setTo(volume == 0, volume);
    ToroExo.setVolumeInfo(player, this.playbackInfo.getVolumeInfo());
  }

  @CallSuper @Override public float getVolume() {
    return checkNotNull(player, "Playable#getVolume(): Player is null!").getVolume();
  }

  @Override public boolean setVolumeInfo(@NonNull VolumeInfo volumeInfo) {
    boolean changed = !this.playbackInfo.getVolumeInfo().equals(checkNotNull(volumeInfo));
    if (changed) {
      this.playbackInfo.getVolumeInfo().setTo(volumeInfo.isMute(), volumeInfo.getVolume());
      if (player != null) ToroExo.setVolumeInfo(player, this.playbackInfo.getVolumeInfo());
    }
    return changed;
  }

  @NonNull @Override public VolumeInfo getVolumeInfo() {
    return this.playbackInfo.getVolumeInfo();
  }

  @Override public void setParameters(@Nullable PlaybackParameters parameters) {
    checkNotNull(player, "Playable#setParameters(PlaybackParameters): Player is null") //
        .setPlaybackParameters(parameters);
  }

  @Override public PlaybackParameters getParameters() {
    return checkNotNull(player, "Playable#getParameters(): Player is null").getPlaybackParameters();
  }

  @Override
  public void addOnVolumeChangeListener(@NonNull ToroPlayer.OnVolumeChangeListener listener) {
    volumeChangeListeners.add(ToroUtil.checkNotNull(listener));
  }

  @Override
  public void removeOnVolumeChangeListener(@Nullable ToroPlayer.OnVolumeChangeListener listener) {
    volumeChangeListeners.remove(listener);
  }

  @Override public boolean isPlaying() {
    return player != null && player.getPlayWhenReady();
  }

  @Override public void addErrorListener(@NonNull ToroPlayer.OnErrorListener listener) {
    this.errorListeners.add(checkNotNull(listener));
  }

  @Override public void removeErrorListener(@Nullable ToroPlayer.OnErrorListener listener) {
    this.errorListeners.remove(listener);
  }

  final void updatePlaybackInfo() {
    if (player == null || player.getPlaybackState() == Player.STATE_IDLE) return;
    playbackInfo.setResumeWindow(player.getCurrentWindowIndex());
    playbackInfo.setResumePosition(player.isCurrentWindowSeekable() ? //
        Math.max(0, player.getCurrentPosition()) : TIME_UNSET);
    playbackInfo.setVolumeInfo(ToroExo.getVolumeInfo(player));
  }

  private void ensurePlayerView() {
    if (playerView != null && playerView.getPlayer() != player) playerView.setPlayer(player);
  }

  // TODO [20180822] Double check this.
  private void ensureMediaSource() {
    if (videoUri != null) {  // Only actually prepare the source when play() is called. One problem is if no music will be added, we only need to check videoSource
      sourcePrepared = false;
      videoSource = creator.createMediaSource(videoUri, fileExt);
    }
    if (videoUris != null) {  // Only actually prepare the source when play() is called. One problem is if no music will be added, we only need to check videoSource
      sourcePrepared = false;
      concatenatingMediaSource = new ConcatenatingMediaSource(creator.createMediaSource(videoUris[0], fileExt));
      for(int i=1; i<videoUris.length; i++) {
        videoSource = creator.createMediaSource(videoUris[i], fileExt);
        concatenatingMediaSource.addMediaSource(videoSource);
      }
    }
    if (audioUri != null ) {
      audioSource = creator.createMediaSource(audioUri, fileExt);
    }

    if (!sourcePrepared) {
      ensurePlayer(); // sourcePrepared is set to false only when player is null.
      if(audioUri != null && videoUris == null){
        player.prepare(new MergingMediaSource(videoSource, audioSource),
            playbackInfo.getResumeWindow() == C.INDEX_UNSET, false);
      } else if (audioUri != null && videoUris != null){
        player.prepare(concatenatingMediaSource, playbackInfo.getResumeWindow() == C.INDEX_UNSET, false);
      }else {
        player.prepare(videoSource, playbackInfo.getResumeWindow() == C.INDEX_UNSET, false);
      }
      sourcePrepared = true;
    }
  }



  private void ensurePlayer() {
    if (player == null) {
      sourcePrepared = false;
      player = with(checkNotNull(creator.getContext(), "ExoCreator has no Context")) //
          .requestPlayer(creator);
      listenerApplied = false;
    }

    if (!listenerApplied) {
      if (player instanceof ToroExoPlayer) {
        ((ToroExoPlayer) player).addOnVolumeChangeListener(volumeChangeListeners);
      }
      player.addListener(listeners);
      player.addVideoListener(listeners);
      player.addTextOutput(listeners);
      player.addMetadataOutput(listeners);
      listenerApplied = true;
    }

    ToroExo.setVolumeInfo(player, this.playbackInfo.getVolumeInfo());
    boolean haveResumePosition = playbackInfo.getResumeWindow() != C.INDEX_UNSET;
    if (haveResumePosition) {
      player.seekTo(playbackInfo.getResumeWindow(), playbackInfo.getResumePosition());
    }
  }
}
