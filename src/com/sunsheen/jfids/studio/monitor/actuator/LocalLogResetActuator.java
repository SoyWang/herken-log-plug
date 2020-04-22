package com.sunsheen.jfids.studio.monitor.actuator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sunsheen.jfids.studio.monitor.common.LogInfo;
import com.sunsheen.jfids.studio.monitor.sender.SendLogByFtp;
import com.sunsheen.jfids.studio.monitor.timer.MonitorUpload;
import com.sunsheen.jfids.studio.monitor.utils.ZipFileUtil;
import com.sunsheen.jfids.studio.monitor.utils.local.FileUtil;
import com.sunsheen.jfids.studio.monitor.utils.local.LocalLogUtil;
import com.sunsheen.jfids.studio.monitor.utils.remote.NetStateUtil;

/**
 * 定时器：每天定时重置当天log
 * 		每天23:59:59:
 * 				在线：当天日志备份到服务器，并重置log文件，开始记录第二天的日志；
 * 						  将所有离线备份的日志上传到服务器，并删除已经上传成功的备份日志；
 * 				离线：当天日志备份到本地指定离线文件夹，并重置log文件，开始记录第二天日志；
 * @author WangSong
 *
 */
public class LocalLogResetActuator {
		
	/**
	 * 定时器任务执行
	 */
	public void startTiming(){
		/** 设置定时启动时间 **/
        Calendar date = Calendar.getInstance();
        //设置时间为 xx-xx-xx 23:59:59
        date.set(date.get(Calendar.YEAR), date.get(Calendar.MONTH), date.get(Calendar.DAY_OF_MONTH), 23, 59, 59);
//        date.set(Calendar.HOUR_OF_DAY,23);
//        date.set(Calendar.MINUTE,59);
//        date.set(Calendar.SECOND,59);
        /** 设置时间间隔 **/
        long daySpan = 24 * 60 * 60 * 1000;
        /** 定时器执行 **/
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
        	@Override
            public void run() {
            	try {
					logRefresh();
				} catch (IOException e) {
					e.printStackTrace();
				}
            }
        }, date.getTime(), daySpan); //隔daySpan时长再在date时间执行
	}
	
	/**
	 * 本地项目log文件更新
	 * @throws IOException 
	 */
	private void logRefresh() throws IOException{
		//遍历出当前所有引用了日志插件的项目
		Map<String, Set<File>> allLogMap = LocalLogUtil.getPlugLogs();
		//服务器可达性监测
		/** 在线(打包备份；上传；本地重置) **/
		if(NetStateUtil.isReachable(LogInfo.SERVERS_ADDRESS)){
			// ///// //// 离线备份的项目文件上传 /////////////////
			offlineLogsUpload();
			// ////// ////所有项目log文件上传 ///// //// /////
			final Map<String,Set<File>> waitingUploadLogs = filterAndUpload(allLogMap);
			resetLogs(waitingUploadLogs);//上传后的项目文件夹本地log重置
		}
		/** 离线(备份所有项目log到离线文件夹，重置所有log) **/
		else{
			final Map<String,Set<File>> waitingUploadLogs = filteringToUploadLogs(allLogMap);//筛选出有日志记录的插件项目
			ExecutorService backupPool = Executors.newSingleThreadExecutor();
			//将有记录的log压缩到离线文件夹
			backupPool.execute(new Runnable(){
				public void run(){
					offlineLogsBackup(waitingUploadLogs);
				}
			});
			backupPool.shutdown();
			//重置所有项目log
			resetLogs(waitingUploadLogs);
		}

	}
	
	
	//离线备份的项目文件上传
	private void offlineLogsUpload() throws IOException{
		ExecutorService uploadPool = Executors.newCachedThreadPool();//动态扩容的上传线程池
		File offlineFolder = new File(LogInfo.OFFLINE_LOG_BACKUP_PATH);//离线日志的根目录
		File[] proFolders = offlineFolder.listFiles();//所有需要上传离线日志的项目
		Map<String,File> toUploadLogs = new HashMap<String,File>();//文件夹跟对应的离线log  Map
		Set<File> waitingBeDeleteDateFolders = new HashSet<File>();//本地需要删除的日期文件夹
		//有离线项目日志需要上传再执行
		if(null != proFolders && proFolders.length != 0){
			FileUtil fu = new FileUtil();
			for(File proFolder : proFolders){//项目下离线日志上传
				if(proFolder.isDirectory()){//排除警告文件
					File[] proDateFolders = proFolder.listFiles();//当前项目下所有按日期备份的日志
					for(File proDateFolder : proDateFolders){//当前日期文件夹
						String datePath = proDateFolder.getPath();
						String date = datePath.substring(datePath.lastIndexOf(File.separator) + 1, 
								datePath.length());//该项目当天备份日期
						File[] logs = proDateFolder.listFiles();//日期文件夹下所有log.zip文件
						for(final File log : logs){//所有log.zip备份文件都要上传到服务器对应文件夹
							//e:/hk-log-offline/com.sunsheen.jfids.monitor/2019-11-12/com....zip
							String logName = log.getName().substring(0,log.getName().lastIndexOf("."));
							String serversFolder = LogInfo.SERVERS_RECIVE_FOLDER + "/" + logName + "/" + date;
							toUploadLogs.put(serversFolder, log);//文件夹与文件的关联
						}
						//待删除的日志文件夹添加
						waitingBeDeleteDateFolders.add(proDateFolder);
					}
				}
			}
			//上传log
			uploadPool.execute(new uploadOfflineLogs(toUploadLogs));
			uploadPool.shutdown();
			//删除本地已上传的离线文件及日期文件夹
			for(File dateFolder : waitingBeDeleteDateFolders){
				fu.deleteFile(dateFolder);
//				FileUtils.deleteDirectory(proDateFolder);
//				proDateFolder.delete();//上传完成后删除当前日期log文件夹
			}
		}
	}
	
	//实现上传的线程
	private class uploadOfflineLogs implements Runnable{
		private Map<String,File> toUploadLogs;
		
		public uploadOfflineLogs(Map<String,File> toUploadLogs){
			this.toUploadLogs = toUploadLogs;
		}
		
		public void run(){
			SendLogByFtp.offlineLogsUpload(toUploadLogs);//上传离线log到服务器对应项目
		}
	}
	
	//重置所有需要离线上传项目的log
	private void resetLogs(final Map<String,Set<File>> waitingUploadLogs){
		ExecutorService resetPool = Executors.newSingleThreadExecutor();
		resetPool.execute(new Runnable(){
			public void run(){
				try{
					for(Set<File> logs : waitingUploadLogs.values()){
						//项目文件夹对应log清空
						for(File log : logs){
							FileWriter fileWriter = new FileWriter(log);
				            fileWriter.write("");
				            fileWriter.flush();
				            fileWriter.close();
						}
					}
				}catch(IOException e){
					e.printStackTrace();
				}
			}
		});
		resetPool.shutdown();
	}
	
	//压缩log到离线文件夹
	private void offlineLogsBackup(Map<String,Set<File>> waitingUploadLogs){
		String nowTime = DateFormat.getDateInstance().format(new Date());//当前年月日
		for(String pro : waitingUploadLogs.keySet()){
			//项目目录层创建
			File proFolder = new File(LogInfo.OFFLINE_LOG_BACKUP_PATH + File.separator + pro);
			if(!proFolder.exists())
				proFolder.mkdir();
			//日期目录层创建
			File proDateFolder = new File(proFolder.getPath() + File.separator + nowTime);
			if(!proDateFolder.exists())
				proDateFolder.mkdir();
			//文件压缩备份
			File backupZipFile = new File(proDateFolder.getPath() + File.separator + pro + ".zip");
			ZipFileUtil.zipOfflineLogs(waitingUploadLogs.get(pro).toArray(new File[waitingUploadLogs.get(pro).size()]), backupZipFile);
		}
	}
	
	//筛选非空log项目，并打包上传
	private Map<String,Set<File>> filterAndUpload(Map<String,Set<File>> allLogMap){
		//筛选出需要打包上传的项目跟log			
		Map<String,Set<File>> waitingUploadLogs = filteringToUploadLogs(allLogMap);
		//将筛选出的log文件备份压缩打包
		MonitorUpload mu = new MonitorUpload();
		mu.zipAndUpload(waitingUploadLogs);
		
		return waitingUploadLogs;
	}
	
	//筛选出需要打包上传的logs
	private Map<String,Set<File>> filteringToUploadLogs(Map<String,Set<File>> allLogMap){
		Map<String,Set<File>> waitingUploadLogs = new HashMap<String,Set<File>>();//待上传的日志文件
		//筛选出需要打包上传的项目跟log			
		for(Map.Entry<String, Set<File>> logs : allLogMap.entrySet()){
			String folder = logs.getKey();//当前项目
			Set<File> currentLogs = logs.getValue();//当前项目下所有日志文件
			//保证没有新日志记录的项目不打包上传
			File[] files = new File(folder).listFiles();
			int blankNum = 0;//初始化空白log个数
			int logsNum = 2;//一个项目下最多只有两个log文件
			if(null != files && files.length != 0){
				for(File file : files){
					if(file.getName().contains(".log")){
						if(file.length() == 0){
							blankNum ++;
							continue;
						}
					}
				}
			}
			if(blankNum == logsNum)
				continue;
			//需要上传的项目添加log
			waitingUploadLogs.put(folder, currentLogs);
		}
		return waitingUploadLogs;
	}
	
	
}
