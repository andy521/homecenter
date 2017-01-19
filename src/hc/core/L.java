package hc.core;

import hc.core.util.LogManager;

public class L {
	public static boolean O = false;
	public static boolean V = false;
	public static boolean WShop = false;
	
	/**
	 * 平台开发环境，而非应用工程开发环境<BR>
	 * it is equal with isSimu
	 */
	public static boolean isInWorkshop = false;
	
	public static void enable(final boolean enable){
		O = !enable;
		LogManager.setEnable(enable);
	}
	
	public static void setInWorkshop(final boolean isWorkshop){
		isInWorkshop = isWorkshop;
		WShop = !isWorkshop;
	}
}
