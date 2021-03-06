package hc.util;

import hc.core.util.ExceptionReporter;
import hc.res.ImageSrc;
import hc.server.PlatformManager;
import hc.server.util.HCJFrame;

import java.awt.Graphics;
import java.awt.Image;
import java.io.IOException;

public class TransparentFrame extends HCJFrame {

	private Image img;

	public TransparentFrame(final Image image) {
		super();

		img = image;

		try {
			initialize();// 窗体初始化
		} catch (final IOException e) {
			ExceptionReporter.printStackTrace(e);
		}

		setVisible(true);

	}

	/**
	 * 窗体初始化
	 *
	 * @throws IOException
	 */
	private void initialize() throws IOException {
		this.setSize(img.getWidth(null), img.getHeight(null));
		this.setUndecorated(true);

		try {
			// Shape形状
			PlatformManager.getService().setWindowShape(this, ImageSrc.getImageShape(img));
			// 透明度
			// AWTUtilities.setWindowOpacity
		} catch (final Throwable e) {
		}
	}

	@Override
	public void paint(final Graphics g) {
		// super //闪烁
		g.drawImage(img, 0, 0, null);
	}

	public void refresh(final Image image) {
		this.img = image;
		repaint();
	}

}
