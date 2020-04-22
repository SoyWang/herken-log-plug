package com.sunsheen.jfids.studio.monitor.timer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
import com.sunsheen.jfids.studio.monitor.utils.remote.NetStateUtil;

/**
 * 日志离线处理
 * @author WangSong
 *
 */
public class MonitorOffline {
	private final HKMonitor monitor = HKMonitorFactory.getLogger(MonitorOffline.class.getName());
	private ScheduledExecutorService logPool = Executors.newSingleThreadScheduledExecutor();
	
	/**
	 * 离线监控
	 */
	public void monitor(){
		
		logPool.scheduleAtFixedRate(new Runnable(){
			public void run(){
				boolean isOnline = NetStateUtil.isReachable(LogInfo.SERVERS_ADDRESS);
				if(isOnline){
					monitor.error("****** 服务器连接成功[日志备份策略转为在线] ******");
					System.out.println("****** 服务器连接成功[日志备份策略转为在线] ******");
					final MonitorUpload mu = new MonitorUpload();
					mu.realTimeMonitor();
					logPool.shutdown();
				}
			}
		}, 0, LogInfo.SERVERS_AVALIABLE_POLLING_TIME, TimeUnit.SECONDS);
	}
	
}
