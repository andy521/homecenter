package hc.core.util;

import hc.core.L;

public class ThumbnailHelper {

	/**
	 * 计算远屏缩略快照尺寸
	 * @param m_w
	 * @param m_h
	 * @return
	 */
	public static int[] calcThumbnail(int m_w, int m_h, final int pc_w, final int pc_h){
		final int maxSize = (m_h>m_w?m_w:m_h);
		final int levelSize = (m_w <= 240 || m_h <= 240)?120:240;
		final int MAX_SCREEN_SIZE = (maxSize > levelSize) ? levelSize : maxSize;
		if (m_h > m_w) {
			// 直屏机器
//			m_h = (m_w>MAX_SCREEN_SIZE?(MAX_SCREEN_SIZE*m_h/m_w):m_h);
			m_w = (m_w>MAX_SCREEN_SIZE?MAX_SCREEN_SIZE:m_w);
			m_h = m_w * pc_h / pc_w;
		} else {
			// 横屏机器
//			m_w = (m_h>MAX_SCREEN_SIZE?(MAX_SCREEN_SIZE*m_w/m_h):m_w);
			m_h = (m_h>MAX_SCREEN_SIZE?MAX_SCREEN_SIZE:m_h);
			m_w = m_h * pc_w / pc_h;
		}
	
		int[] out = {m_w, m_h};
		return out;
	}

//	private static int convertZoomUnzoomXY(int xy, final int zoomMultiples){
//		if(zoomMultiples != 1){
//			int zoomSize = xy / zoomMultiples;
//			if(zoomSize * zoomMultiples != xy){
//				zoomSize++;//增加放大后，末尾舍掉的一位像素
//			}
//			return zoomSize;
//		}
//		return xy;
//	}
	
	public static int[] calNewLocXY(int oldZoomMultiple, int zoomMultiples, int locX, int locY, int pcW, int pcH, int mobileW, int mobileH){
		final int oldLocX = locX;
		final int oldLocY = locY;
		
		if(zoomMultiples > oldZoomMultiple){
			locX += (mobileW / oldZoomMultiple - mobileW / zoomMultiples) / 2;
			locY += (mobileH / oldZoomMultiple - mobileH / zoomMultiples) / 2;
		}else{
			locX -= (mobileW / zoomMultiples - mobileW / oldZoomMultiple) / 2;
			locY -= (mobileH / zoomMultiples - mobileH / oldZoomMultiple) / 2;
			if(locX > (pcW - mobileW)){
				locX = pcW - mobileW;//有可能产生负值
			}
			//故locX要置于后
			if(locX < 0){
				locX = 0;
			}

			if(locY > (pcH - mobileH)){
				locY = pcH - mobileH;//有可能产生负值
			}
			//有可能手机分辨率高于电脑，故locY要置于后
			if(locY < 0){
				locY = 0;
			}
		}
		
		final int zoomWidth = (zoomMultiples != 1)?(mobileW / zoomMultiples):mobileW;		
		final int zoomHeight = (zoomMultiples != 1)?(mobileH / zoomMultiples):mobileH;		
		
		int pcBottomY = 0, pcMaxRightX = 0;
		pcBottomY = pcH - zoomHeight;
		if(pcBottomY < 0){
			pcBottomY = 0;
		}
		pcMaxRightX = pcW - zoomWidth;
		if(pcMaxRightX < 0){
			pcMaxRightX = 0;
		}
//		L.V = L.O ? false : LogManager.log("oldZoomMulti : " + oldZoomMultiple + "currentZoomMulti : " + zoomMultiples + ", oldLocX : " + oldLocX + ", oldLocY : " + oldLocY 
//		+ ", locX : " + locX + ", locY : " + locY + ", mobileW : " + mobileW + ", mobileH : " + mobileH + ", zoomW : " + zoomWidth + ", zoomH : " + zoomHeight + 
//		"maxRightX : " + pcMaxRightX + ", bottomY : " + pcBottomY);

		final int[] back = {locX, locY, zoomWidth, zoomHeight, pcMaxRightX, pcBottomY, 
				(zoomWidth*zoomMultiples)==mobileW?0:1, //返回1表示，要补一个像素宽
				(zoomHeight*zoomMultiples)==mobileH?0:1};//返回1表示，要补一个像素高
		return back;
	}

}
