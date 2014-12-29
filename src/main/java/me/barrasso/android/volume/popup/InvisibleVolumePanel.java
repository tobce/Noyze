package me.barrasso.android.volume.popup;

import android.view.WindowManager;

import me.barrasso.android.volume.R;
import me.barrasso.android.volume.media.VolumePanelInfo;

/**
 * Simplest {@link VolumePanel}, doesn't display anything in
 * response to volume_3 change. Yeah, it's pretty boring.
 */
public class InvisibleVolumePanel extends VolumePanel {
	
	public static final String TAG = InvisibleVolumePanel.class.getSimpleName();
	
	public static final VolumePanelInfo<InvisibleVolumePanel> VOLUME_PANEL_INFO =
										new VolumePanelInfo<InvisibleVolumePanel>(InvisibleVolumePanel.class);
	
	public InvisibleVolumePanel(PopupWindowManager pWindowManager) {
		super(pWindowManager);
	}
	
    @Override public void onStreamVolumeChange(int streamType, int volume, int max) { }
    @Override public boolean isInteractive() { return false; }
    @Override public WindowManager.LayoutParams getWindowLayoutParams() { return null; }
	
	// Nullify these methods.
	@Override public void attach() { }
	@Override public void detach() { }
	@Override public void show() { }
	@Override public void hide() { }
	
}