package com.sunsheen.jfids.studio.monitor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.ui.IStartup;

import com.sunsheen.jfids.studio.monitor.timer.MonitorUpload;

/**
 * 启动eclipse应用程序时，先运行当前类的启动方法
 * @author WangSong
 *
 */
public class StartupMonitor implements IStartup{
	private final ExecutorService service = Executors.newCachedThreadPool();

	@Override
	public void earlyStartup() {
		
		final MonitorUpload upload = new MonitorUpload();
		
		service.execute(new Runnable(){
			@Override
			public void run() {
//				upload.regularMonitor();//定时监控
				try{
					upload.realTimeMonitor();//实时监控
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		});
//		service.shutdown();
		
//		System.out.println(
//				"____  ____    __  __	\t"+				""+" __     _____  ________\n"+
//				"|_   ||   _||_  ||_  __|	\t"+				""+"|   |    |   _    ||      ____| \n"+
//				"   | |__| |    | |_/ /		\t"+				""+"|   |    |  |  |  ||  |   \n"+
//				"   |  __   |    |  __'.		\t"+				""+"|   |    |  |  |  ||  |     ____ \n"+
//				" _| |    | |_  | |  "+"\\\\"+"__		\t"+	""+"|   |__|  |_|  ||  |____  | | \n"+
//				"|______|_  ||_ ||___|	\t"+				""+"|___ || ____||________| \n"
//			);
	}

}
