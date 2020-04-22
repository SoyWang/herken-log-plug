package com.sunsheen.jfids.studio.monitor.timer;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.actuator.LocalBackupsLogSizeActuator;
import com.sunsheen.jfids.studio.monitor.actuator.LocalLogResetActuator;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
import com.sunsheen.jfids.studio.monitor.sender.SendLogByFtp;
import com.sunsheen.jfids.studio.monitor.utils.ZipFileUtil;
import com.sunsheen.jfids.studio.monitor.utils.local.FileUtil;
import com.sunsheen.jfids.studio.monitor.utils.local.LocalLogUtil;
import com.sunsheen.jfids.studio.monitor.utils.remote.NetStateUtil;
import com.sunsheen.jfids.studio.monitor.utils.remote.ServersDebugUtil;
import com.sunsheen.jfids.studio.monitor.utils.remote.ServersFolderUtil;

//定时上传监控日志
public class MonitorUpload {
	
	private final HKMonitor monitor = HKMonitorFactory.getLogger(MonitorUpload.class.getName());
	// 项目对应的日志文件map
	private Map<String, Set<File>> initialLogMap = new HashMap<String,Set<File>>();

	// 分配线程
	private final int cpuSize = Runtime.getRuntime().availableProcessors();// cpu核心数
	private double threadWaitTime = 0.5;//初始化线程等待时间	
	private double threadRunTime = 0.4;//初始化线程执行时间
	private final ScheduledExecutorService uploadPool = new ScheduledThreadPoolExecutor(
			(int) ((threadWaitTime / threadRunTime + 1) * cpuSize));//上传线程池
	private final ScheduledExecutorService deletePool = Executors.newSingleThreadScheduledExecutor();//删除备份文件线程池
	private final ScheduledExecutorService monitorPool =  Executors.newSingleThreadScheduledExecutor();//监控线程池
	private final ExecutorService localLogSizeControlPool =  Executors.newCachedThreadPool();//本地离线监控上传线程池
	
	private boolean isNeedUpload = false;//是否需要上传
	private Map<String,Set<File>> waitingUploadLogs = new ConcurrentHashMap<String,Set<File>>();//待上传的日志文件
	private boolean isNotUserCatalog = true;	//是否有用户目录
	private boolean isNotLogCatalog = true;	//是否有日志目录
	private boolean isNotUploadZip = true;	//是否还没有上传过zip文件
	private Map<String,Double> logFolderSizeMap = new ConcurrentHashMap<String,Double>();//文件夹对应长度map
	
	
	/**
	 * 构造器：初始化原来日志关联	
	 */
	public MonitorUpload(){
		//初始化文件夹跟文件的关联
		Map<String,Set<File>> orFolderMap = LocalLogUtil.getPlugLogs();
		this.initialLogMap.clear();
		this.initialLogMap.putAll(orFolderMap);
		//获取到开始所有日志项目文件夹的大小
		for(String path : initialLogMap.keySet()){
			String folderPath = LogInfo.RUNTIME_SPACE +LogInfo.LOG_PARENT_PATH+ path;
			folderPath = folderPath.substring(1,folderPath.length());
			//存入当前文件夹名跟当前文件夹的大小
			FileUtil fu = new FileUtil();
			double size = fu.getTotalSizeOfFile(folderPath);
			if(size == 0){
				System.out.println("项目【"+path+"】第一次引用当前日志插件记录日志！");
				monitor.info("项目【"+path+"】第一次引用当前日志插件记录日志！");
			}
			logFolderSizeMap.put(path, size);
			fu = null;
		}
	}
	
	
	
	/**
	 * 实时监控日志文件（有变动时，再上传）:
	 * 			可以配置轮询监控时间间隔----在LogInfo中配置
	 */
	public void realTimeMonitor(){
		//只上传修改过日志文件的项目
		monitorPool.scheduleAtFixedRate(new Runnable(){
			@Override
			public void run() {
				/** 文件夹是否改动 **/
				//日志文件夹监控同步
				boolean isOnline = sychronizedFolder();
				////////////  离线操作 ////////////
				if(!isOnline){
					uploadPool.shutdownNow();//定时备份线程池关闭
					monitorPool.shutdownNow();//停止联网备份
					deletePool.shutdownNow();//停止本地备份日志文件大小控制
					return;
				}
				////////////  在线操作 ////////////
				//第一次启动时：上传所有日志文件到服务器；
				if(isNotUploadZip){
					zipAndUpload();
					isNotUploadZip = false;
				}
				//文件夹改动，重新打包同步上传
				if(isNeedUpload){
					zipAndUpload(waitingUploadLogs);//打包并备份
					isNeedUpload = false;
					waitingUploadLogs.clear();//清空需要打包上传的logs
				}
				/** 文件夹没有改动时，文件是否改动 **/
				Map<String, Set<File>> newLogMap = LocalLogUtil.getPlugLogs();//刷新当前文件夹跟文件
				//如果刷新后文件夹不一致，说明刷新不及时，进入下一轮循环重新刷新文件夹
				//刷新后文件夹一致，文件变化监控
				if((initialLogMap.keySet()).containsAll(newLogMap.keySet()))
					fileDynamicTracking();//文件变化监控
			}
		}, 0, LogInfo.UPLOAD_POLLING_TIME, TimeUnit.SECONDS);// 10min/次
//		}, 0, 2L, TimeUnit.SECONDS);
		
		//本地备份文件大小监控
		deletePool.scheduleAtFixedRate(new LocalBackupsLogSizeActuator(), 0, 
				LogInfo.BACKUP_FILE_DELETION_POLLING_TIME, TimeUnit.SECONDS);//12h/次
		
		//本地日志文件定时策略（保证磁盘log大小不指数倍增长）
		final LocalLogResetActuator llra = new LocalLogResetActuator();
		localLogSizeControlPool.execute(new Runnable(){
			public void run(){
				llra.startTiming();
			}
		});
		
		//TODO 服务器日志文件夹大小控制策略
		
	}
	
	
	/**
	 * 定时上传文件到服务器，删除本地日志文件
	 */
	public void regularMonitor() {
		// 上传备份日志文件
		uploadPool.scheduleWithFixedDelay(new Runnable() {
			@Override
			public void run() {
				// 同步服务器跟本地的日志文件夹目录
				boolean isOnline = sychronizedFolder();
				if(!isOnline)
					return;
				// 上传文件
				zipAndUpload();
			}
		}, 0, LogInfo.UPLOAD_POLLING_TIME, TimeUnit.SECONDS);//30s执行一次

		// 本地备份文件大小监控
		deletePool.scheduleAtFixedRate(new LocalBackupsLogSizeActuator(), 0, 
				LogInfo.BACKUP_FILE_DELETION_POLLING_TIME, TimeUnit.SECONDS);
		// TODO 服务器文件大小监控
		
		//TODO 各项目中日志文件大小控制(每天凌晨定时清除当前文件，创建新文件)
		
	}
	
	//日志文件是否变化
	private void fileDynamicTracking(){
		/** 方法一：通过检测文件夹长度是否改变来控制文件改动后自动上传 **/
		FileUtil fu = new FileUtil();
		//遍历改变了文件的文件夹
		for(String name : logFolderSizeMap.keySet()){
			//原始长度
			double pristineSize = logFolderSizeMap.get(name);
			//现在长度
			String folderPath = LogInfo.RUNTIME_SPACE +LogInfo.LOG_PARENT_PATH+name;
			folderPath = folderPath.substring(1,folderPath.length());
			double currentSize = fu.getTotalSizeOfFile(folderPath);
//			System.out.println("文件夹【"+name+"】—原始size:"+pristineSize+"—现在size:"+currentSize);
			//如果改变，存入改变的文件夹及文件
			if(pristineSize != currentSize && currentSize>0){
				//存入改变的项目
//				System.out.println("文件夹【"+name+"】中日志文件有新记录，已自动上传该项目日志！");
				monitor.info("文件夹【"+name+"】中日志文件有新记录，已自动上传该项目日志！");
				isNeedUpload = true;
				waitingUploadLogs.put(name, LocalLogUtil.getPlugLogs().get(name));
				//文件长度信息更新
				logFolderSizeMap.put(name, currentSize);
			}
		}	
		
		//////////////////////////////////////////////////////////
		/** 方法二：通过检测文件最后修改时间来控制文件改动后自动上传 **/
//		File modifiedFile = null;
//		File beforFile = null;
//		//查看项目下文件是否改动
//		for(Map.Entry<String, Set<File>> map : newLogMap.entrySet()){
//			System.out.println("当前文件夹"+map.getKey());
//			
//			Set<File> newLogs =  map.getValue();//新  当前目录下所有的log
//			Set<File> oldLogs = initialLogMap.get(map.getKey());//旧  当前目录下所有的log
//			//比较当前目录下面文件是否有改动
//			for(File newLog : newLogs){
//				beforFile = newLog;
//				String newLogName = newLog.getName();//新  log名字
//				long newlogModifiedTime = newLog.lastModified();//新   最后一次修改时间
//				long oldLogModifiedTime = 0;//旧   最后一次修改时间初始化
//				for(File oldLog : oldLogs){
//					if(newLogName.equals(oldLog.getName())){
//						oldLogModifiedTime = oldLog.lastModified();// 旧   最后一次修改时间
//						modifiedFile = oldLog;
//						break;
//					}
//				}
//				System.out.println("新文件最后日期："+new Date(newlogModifiedTime));
//				System.out.println("原文件最后日期："+new Date(oldLogModifiedTime));
//				System.out.println("新文件长度："+newLog.length());
//				System.out.println("原文件长度："+modifiedFile.length());
//				//文件发生过改动
//				if((newlogModifiedTime != oldLogModifiedTime) || (beforFile.length() != modifiedFile.length())){
//					System.out.println("文件：【"+modifiedFile.getName()+"】，修改了日志内容！");
//					isNeedUpload = true;
//					if(waitingUploadLogs.get(map.getKey()).isEmpty())
//						waitingUploadLogs.put(map.getKey(), newLogs);//将当前目录跟logs关联
//					initialLogMap = newLogMap;//文件改动时，需要重新拉去一下文件
//				}
//			}
//		}
		///////////////////////////////////////////////////////
		//文件改动，重新打包同步上传
		if(isNeedUpload){
			zipAndUpload(waitingUploadLogs);//打包并备份
			isNeedUpload = false;
			waitingUploadLogs.clear();//清空需要打包上传的logs
		}
	}
	
	// 上传文件（定時上傳/第一次上传使用）
	private void zipAndUpload() {
		//将每个项目文件夹下对应日志文件打包成一个zip
		Map<String[],File[]> resultMap = zipLogsByFolder(initialLogMap);
		//取出文件夹跟对应的zip
		Object[] folderArr = resultMap.keySet().toArray();
		String[] folders = (String[])folderArr[0];//所有文件夹
		Object[] logArr = resultMap.values().toArray();
		File[] logs = (File[]) logArr[0];//所有日志文件
		// 在所有的日志文件都打包之后，一次上传（保证原子性）
		Thread sendThread = new Thread(new Sender(logs, folders));
		sendThread.setDaemon(false);
		sendThread.start();
	}
	
	// 上传文件（實時上傳使用）
	public void zipAndUpload(Map<String,Set<File>> paramMap) {
		//将每个项目文件夹下对应日志文件打包成一个zip
		Map<String[],File[]> resultMap = zipLogsByFolder(paramMap);
		//取出文件夹跟对应的zip
		Object[] folderArr = resultMap.keySet().toArray();
		String[] folders = (String[])folderArr[0];//所有文件夹
		Object[] logArr = resultMap.values().toArray();
		File[] logs = (File[]) logArr[0];//所有日志文件
		// 在所有的日志文件都打包之后，一次上传（保证原子性）
		SendLogByFtp.postFile(logs, folders);
	}
		
	/**
	 * 服务器项目文件夹跟本地文件夹同步（服务器不删除文件夹）
	 * @return	联网返回true ； 离线返回false
	 */
	private boolean sychronizedFolder() {
		/** 服务器在线状态监测 **/
		if(!NetStateUtil.isReachable(LogInfo.SERVERS_ADDRESS)){
			System.out.println("****** 服务器连接失败[日志备份策略转为离线] ******");
			monitor.error("****** 服务器连接失败[日志备份策略转为离线] ******");
			//本地离线备份日志线程执行
			final MonitorOffline mo = new MonitorOffline();
			mo.monitor();
			return false;
		}
		/** 本地操作 **/
		Map<String,Set<File>> associatedLogMap = LocalLogUtil.getPlugLogs();// 刷新当前eclipse的所有日志记录功能项目日志文件夹記錄
		//第一次进来还没有在服务器创建对应文件夹，先要创建文件夹,不执行下面if
		if(!isNotLogCatalog){
			//文件夹没有变化，不再执行
			Set<String> oldFolders = initialLogMap.keySet();
			Set<String> newFolders = associatedLogMap.keySet();
			if(oldFolders.equals(newFolders))
				return true;
			//忽略掉某些项目删除了rm
			//增加需要上传的日志文件
			FileUtil fu = new FileUtil();
			if(associatedLogMap.size() >= initialLogMap.size()){
				//存放需要上传日志的项目
				isNeedUpload = true;//上传标识
				for(Map.Entry<String, Set<File>> resultMap : associatedLogMap.entrySet()){
					final String folder = resultMap.getKey();//新增的文件夹名
					if(!initialLogMap.containsKey(folder)){
						System.out.println("项目【"+folder+"】加入日志记录功能！");
						monitor.info("项目【"+folder+"】加入日志记录功能！");
						waitingUploadLogs.put(folder, resultMap.getValue());
						//远程服务器创建当前文件夹
						ExecutorService mkFolderPool = Executors.newSingleThreadExecutor();
						mkFolderPool.execute(new Runnable(){
							public void run(){
								ServersDebugUtil.transferCommand("cd "
										+ LogInfo.SERVERS_RECIVE_FOLDER, "mkdir "+ folder);
							}
						});
						mkFolderPool.shutdown();
						//添加需要扫描文件夹里log文件变化的项目
						logFolderSizeMap.put(folder, fu.getTotalSizeOfFile
								(LogInfo.RUNTIME_SPACE.substring(1,LogInfo.RUNTIME_SPACE.length())
								+LogInfo.LOG_PARENT_PATH + folder));
					}
				}
			}else{
				//某个项目删除了当前日志插件，需要记录
				for(String folder : initialLogMap.keySet()){
					if(!associatedLogMap.keySet().contains(folder)){
						System.out.println("项目【"+folder+"】移除了日志记录功能！");
						monitor.info("项目【"+folder+"】移除了日志记录功能！");
						//移除当前项目的日志文件变化监控
						logFolderSizeMap.remove(folder);
					}
				}
			}
			//原始文件夹信息和对应文件，要跟着最新的信息联动
			initialLogMap.clear();
			initialLogMap.putAll(associatedLogMap);
			//服务器不需要跟着删除文件夹
			return true;
		}
		
		/** 服务器操作 **/ 
		Set<String> localLogFolders = associatedLogMap.keySet();//取出本地所有日志项目文件夹名
		// 第一次运行项目，需要服务端文件夹目录检查
		if(isNotUserCatalog){
			//循环检查文件夹参数个数次
			for(int i=0;i<2;i++)
				ServersDebugUtil.foldedrCheck(LogInfo.SERVERS_PARENT_USER_FOLDER,
						LogInfo.SERVERS_RECIVE_FOLDER);
			isNotUserCatalog = false;
		}
		// 获取服务器上的所有项目日志文件夹
		List<Object> serversLogFolders = ServersFolderUtil.getSubfolderName(LogInfo.SERVERS_RECIVE_FOLDER);
		
		//在联网途中突然没有网络连接，在这里停用当前联网备份策略（排除第一次创建文件夹）
		if(!isNotLogCatalog){
			if(serversLogFolders.isEmpty())
				return false;
		}

		//控制无效的服务器操作
		if(serversLogFolders.containsAll(localLogFolders)){
			isNotLogCatalog = false;
			return true;
		}
		// 在服务器上新增本地新创建的文件夹
		serversFolderOp(serversLogFolders, localLogFolders, true);

		// 在服务器上删除本地没有的文件夹
		//new Thread(new RunCommond(serversLogFolders, localLogFolders, false)).start();
		return true;
	}

	// 服务器增加或删除文件夹
	private void serversFolderOp(List<Object> serversLogFolders, Set<String> localLogFolders, boolean isAdd) {
		if (isAdd) {
			// 在服务器新增文件夹
			for (String local : localLogFolders) {
				if (!serversLogFolders.contains(local)) {
					ServersDebugUtil.transferCommand("cd "
							+ LogInfo.SERVERS_RECIVE_FOLDER, "mkdir "
							+ local);
					monitor.info("服务器上新增了【" + local + "】项目的日志记录！");
				}
			}
		} else {
			// 在服务器删除文件夹及文件夹里面文件
			for (Object servers : serversLogFolders) {
				if (!localLogFolders.contains(servers)) {
					ServersDebugUtil.transferCommand("cd "
							+ LogInfo.SERVERS_RECIVE_FOLDER, "rm -rf "
							+ servers);
					monitor.info("服务器上删除了【" + servers + "】项目的日志记录！");
				}
			}
		}
	}

	// 压缩打包
	private class Zip implements Runnable {
		private File[] zipFiles;
		private File target;
		public File resultZip;

		public Zip(final File[] zipFiles, final File target) {
			this.zipFiles = zipFiles;
			this.target = target;
		}

		@Override
		public void run() {
			zipLogs();
		}

		// 压缩打包
		private File zipLogs() {
			resultZip = ZipFileUtil.zipFiles(zipFiles, target);// 压缩文件到本地
			if(resultZip.getName().contains("(")){
				monitor.info("【"
						+ resultZip.getName().substring(0, resultZip.getName().indexOf("("))
						+ "】项目的日志已压缩到【"
						+ target.getPath().substring(0,
								target.getPath().lastIndexOf(File.separator)) + "】");
			}else{
				monitor.info("【"
						+ resultZip.getName()
						+ "】项目的日志已压缩到【"
						+ target.getPath().substring(0,
								target.getPath().lastIndexOf(File.separator)) + "】");	
			}
			return resultZip;
		}

	}

	// 发送文件
	private class Sender implements Runnable {
		private File[] toUploadFiles;
		private String[] serversZipFolders;

		public Sender(File[] toUploadFiles, String[] serversZipFolders) {
			this.toUploadFiles = toUploadFiles;
			this.serversZipFolders = serversZipFolders;
		}

		@Override
		public void run() {
			SendLogByFtp.postFile(toUploadFiles, serversZipFolders);
		}
	}
	
	//根据项目log map 压缩日志文件
	private Map<String[],File[]>  zipLogsByFolder(Map<String,Set<File>> paramMap){
		Map<String[],File[]> results = new HashMap<String[],File[]>();//文件夹跟对应zip文件的对应关系
		int size = paramMap.size();//文件夹个数
		Set<String> newLogFolders = paramMap.keySet();//文件夹名称

		Thread zipThreads[] = new Thread[size];// 守护线程
		File[] toUploadFiles = new File[size];// 已经打包完成需要上传到服务器的文件
		String[] serversZipFolders = new String[size];// 服务器存放文件的文件夹
		// 依照项目打包文件
		for (int i = 0; i < size; i++) {
			String logFolderName = (String) newLogFolders.toArray()[i];
			Set<File> currentProLogs = paramMap.get(logFolderName);// 当前项目下对应的日志文件
			// 本地创建一个存放压缩文件的目录
			File zipLogLocation = new File(LogInfo.LOCAL_LOG_ZIP_FILE_LOCATION
					+ File.separator + logFolderName);
			if (!zipLogLocation.exists())
				zipLogLocation.mkdirs();
			// 将当前项目目录下的日志文件压缩成一个zip文件
			File[] toZipFiles = currentProLogs.toArray(new File[currentProLogs
					.size()]);// 待压缩日志文件数组
			File compressedFile = 
					new File(LogInfo.LOCAL_LOG_ZIP_FILE_LOCATION + File.separator
					+ logFolderName + File.separator + logFolderName + ".zip");// 待压缩完成的目标zip文件

			/** 这里如果设置守护线程可能出现异常：可能在守护线程线程压缩完成之前主线程结束了，抛出找不到文件 **/
			Zip myZip = new Zip(toZipFiles, compressedFile);
			zipThreads[i] = new Thread(myZip);//将当前目录下所有log文件压缩成一个zip
			zipThreads[i].setDaemon(false);// 守护线程
			zipThreads[i].start();

			// 待打包上传文件
			if(null == myZip.resultZip)
				toUploadFiles[i] = ZipFileUtil.zipFiles(toZipFiles, compressedFile);// 压缩文件到本地
			else
				toUploadFiles[i] = myZip.resultZip; 
			
			// 服务器存放日志文件的文件夹
			serversZipFolders[i] = LogInfo.SERVERS_RECIVE_FOLDER + "/"
					+ logFolderName;
		}
		results.put(serversZipFolders, toUploadFiles);
		return results;
	}

}
