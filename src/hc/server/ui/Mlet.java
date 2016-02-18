package hc.server.ui;

import hc.core.ContextManager;
import hc.core.util.HCURL;
import hc.core.util.HCURLUtil;
import hc.server.html5.syn.DiffManager;
import hc.server.html5.syn.DifferTodo;
import hc.server.html5.syn.JPanelDiff;
import hc.util.ResourceUtil;

import java.awt.image.BufferedImage;

import javax.swing.JPanel;

/**
 * {@link Mlet} is an instance running in HomeCenter server, and the <STRONG>snapshot</STRONG> of {@link Mlet} is presented on mobile.
 * It looks more like a form, dialog, control panel or game canvas running in mobile.
 * <BR><BR>
 * {@link Mlet} is extends {@link javax.swing.JPanel JPanel}, so you can bring all JPanel features to mobile UI, no matter whether your mobile is Android, iPhone or other.
 * <BR>for demo, please goto <a target="_blank" href="http://homecenter.mobi/en/pc/steps_mlet.htm">http://homecenter.mobi/en/pc/steps_mlet.htm</a>
 * <BR><BR>
 * if you want display all JComponents to mobile in HTML (not snapshot) and set CSS for these JComponents, you can use {@link HTMLMlet}, which is extends {@link Mlet}.
 * @see HTMLMlet
 * @since 7.0
 */
public class Mlet extends JPanel implements ICanvas {
	private static final long serialVersionUID = 7;
	
	/**
	 * construct this instance.
	 */
	public static final int STATUS_INIT = 0;
	
	/**
	 * when {@link #onStart()} or {@link #onResume()}, {@link Mlet} is changed to this status.
	 */
	public static final int STATUS_RUNNING = 1;
	
	/**
	 * when {@link #onPause()}, {@link Mlet} is changed to this status.
	 */
	public static final int STATUS_PAUSE = 2;
	
	/**
	 * when {@link #onExit()}, {@link Mlet} is changed to this status.
	 */
	public static final int STATUS_EXIT = 3;
	
	int status = STATUS_INIT;
	
	/**
	 * 
	 * @return {@link #STATUS_INIT}, {@link #STATUS_RUNNING}, {@link #STATUS_PAUSE}, {@link #STATUS_EXIT} or other.
	 */
	public final int getStatus(){
		return status;
	}

	/**
	 * @deprecated
	 */
	@Deprecated
	public final void notifyStatusChanged(final int newStatus){
		synchronized (this) {//要置于外部
			status = newStatus;
			
			if(this instanceof HTMLMlet){
				final HTMLMlet htmlMlet = (HTMLMlet)this;
				final DifferTodo diffTodo = htmlMlet.diffTodo;
				if(newStatus == STATUS_RUNNING && diffTodo != null){
					if(htmlMlet.isFlushCSS){
						return;
					}else{
						htmlMlet.isFlushCSS = true;
					}
					
					//有可能为MletSnapCanvas模式调用本方法
					ServerUIAPIAgent.loadStyles(htmlMlet);
					JPanelDiff.addContainerEvent(this, diffTodo);
					DiffManager.getDiff(JPanelDiff.class).diff(0, this, diffTodo);//必须置于onStart之前，因为要初始手机端对象结构树
					ServerUIAPIAgent.flushCSS(htmlMlet, diffTodo);
				}
			}
		}
	}

	/**
	 * @since 7.0
	 */
	public static final String URL_EXIT = HCURL.URL_CMD_EXIT;
	/**
	 * @since 7.0
	 */
	public static final String URL_SCREEN = HCURL.URL_HOME_SCREEN;
	
	/**
	 * @deprecated
	 */
	@Deprecated
	public Mlet(){
		__context = ProjectContext.getProjectContext();
		if(__context != null){//测试用例时(TestMlet.java)，产生null
			__target = ServerUIAPIAgent.__getTargetFromInnerMethod(__context);
		}
	}
	
	/**
	 * @deprecated
	 */
	@Deprecated
	private String __target;
	/**
	 * @deprecated
	 */
	@Deprecated
	ProjectContext __context;
	
	/**
	 * @return for example, screen://myMlet or cmd://playMusic
	 * @since 7.0
	 */
	public final String getTarget(){
		return __target;
	}
	
	/**
	 * @return current project context
	 * @since 7.0
	 */
	public final ProjectContext getProjectContext(){
		return __context;
	}
	
	private boolean isKeyOnExit;
	
	/**
	 * you can go to resources as following:<BR>
	 * 1. open and show a screen/form ({@link #URL_SCREEN}, screen://myMlet, or form://myHtmlMlet), <BR>
	 * 2. open and show a controller (controller://myctrl), <BR>
	 * 3. run script commands ({@link #URL_EXIT}, cmd://myCmd)
	 * <BR><BR>
	 * <STRONG>Tip : </STRONG><BR>if you had jump to <i>form://myTwo</i> from <i>form://myOne</i>, 
	 * and ready jump to <i>form://myOne</i> from current <i>form://myTwo</i>.<BR>system will bring the target (form://myOne) to top level if it is opened.
	 * @param url
	 * @since 7.0
	 */
	public final void go(final String url){
		if(url.equals(URL_EXIT)){
			synchronized (this) {
		    	if(isKeyOnExit){
		    		return;
		    	}
		    	isKeyOnExit = true;
	    	}
		}
		
		try {
//			不需要转码，可直接支持"screen://我的Mlet"
//			final String encodeURL = URLEncoder.encode(url, IConstant.UTF_8);
			ContextManager.getThreadPool().run(new Runnable() {
				@Override
				public void run() {
					HCURLUtil.sendCmd(HCURL.DATA_CMD_SendPara, HCURL.DATA_PARA_TRANSURL, url);
				}
			}, ServerUIAPIAgent.threadToken);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * resize a BufferedImage to the target size.
	 * @param src
	 * @param to_width
	 * @param to_height
	 * @return the resized image.
	 */
	public final static BufferedImage resizeImage(final BufferedImage src, final int to_width, final int to_height){
		return ResourceUtil.resizeImage(src, to_width, to_height);
	}
	
	
	/**
	 * enter this {@link Mlet}, server will call this method.
	 * <BR><BR>
	 * calling {@link #getStatus()} in this method will returns {@link #STATUS_RUNNING}.
	 * <BR><BR>
	 * {@link #STATUS_INIT} is a status before {@link #STATUS_RUNNING}.
	 * @since 7.0
	 */
	@Override
	public void onStart() {
	}

	/**
	 * if user click a button to jump other screen, the server will call {@link #onPause()} method before enter next screen, see sample in HomeCenter server.
	 * <BR><BR>
	 * calling {@link #getStatus()} in this method will returns {@link #STATUS_PAUSE}.
	 * @see #onResume()
	 * @since 7.0
	 */
	@Override
	public void onPause() {
	}

	/**
	 * the server will call this method when exit from next screen, and re-enter this {@link Mlet}.
	 * <BR><BR>
	 * calling {@link #getStatus()} in this method will returns {@link #STATUS_RUNNING}.
	 * @see #onPause()
	 * @since 7.0
	 */
	@Override
	public void onResume() {
	}

	/**
	 * the server will call this method when exit this {@link Mlet}, and return/back.
	 * <BR><BR>
	 * calling {@link #getStatus()} in this method will returns {@link #STATUS_EXIT}.
	 * <BR><BR>
	 * <STRONG>Important : </STRONG>if there is a running {@link Runnable} is started by this {@link Mlet} via {@link ProjectContext#run(Runnable)}, 
	 * it is a good practice that the running {@link Runnable} will check {@link #getStatus()} in loop or after {@link Thread#sleep(long)}.
	 * <BR>Please DON'T try to get <code>Thread</code> of it and call {@link Thread#stop()}.
	 * @since 7.0
	 */
	@Override
	public void onExit() {
	}
}
