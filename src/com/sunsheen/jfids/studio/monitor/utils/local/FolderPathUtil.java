package com.sunsheen.jfids.studio.monitor.utils.local;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.eclipse.core.runtime.Platform;

import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.MonitorInfo;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
/**
 * 根据日志常量类拼接本地和服务器真实的文件路径信息
 * @author WangSong
 *
 */
public class FolderPathUtil {
	private final static HKMonitor monitor = HKMonitorFactory.getLogger(FolderPathUtil.class.getName());
	
	private FolderPathUtil(){}
	
	/**
	 * 服务器用户层文件夹路径
	 * @return
	 */
	public static String getServiceUserFolder(){
		//获取本机用户名跟ip
		InetAddress addr = null;
		try {
			addr = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}  
        String ip=addr.getHostAddress().toString(); //获取本机ip  
        String hostName=addr.getHostName().toString(); //获取本机计算机名称
        
		MonitorInfo userInfo = new MonitorInfo();
		userInfo.setName(hostName);
		userInfo.setIp(ip);
		
		String serverParentFolder = LogInfo.SERVERS_PARENT_USER_FOLDER;
		return serverParentFolder + "/" + hostName+ "-" +ip ;
	}
	
	//	 eclipse安装路径
	private static String getEclipseInstallPath(){
		String ss = Platform.getInstallLocation().getURL().toString();
		int index = ss.indexOf("/");
		String path = ss.substring(index + 1, ss.length() - 1);
		return path ;
	}
	
	/**
	 * 日志存放路径
	 * @return
	 */
	public static String getLogPath(){
		String logPath = getEclipseInstallPath()+ File.separator + LogInfo.LOCAL_LOG_BACKUP_FOLDER_NAME;
		System.out.println("项目日志文件备份路径：["+logPath+"]");
		return logPath;
	}
	
	/**
	 * 离线，日志存放目录
	 * @return
	 */
	public static String getOfflineLogPath(){
		String logPath = getEclipseInstallPath()+ File.separator + LogInfo.LOCAL_LOG_OFFLINE_BACKUP_FOLDER_NAME;
		System.out.println("项目离线日志存放路径：["+logPath+"]");
		//在当前文件夹下创建一个提示文件
		File file = new File(logPath);
		if(!file.exists())
			file.mkdir();
		boolean existReminder = false;
		for(File txt : file.listFiles()){
			if(txt.isFile() && txt.getName().equals("必读！！！")){
				existReminder = true;
				break;
			}
			existReminder = false;
		}
		if(!existReminder){
			File reminder = new File(logPath + File.separator + "必读！！！.txt");
			FileWriter fileWriter = null;
			try {
				fileWriter = new FileWriter(reminder);
				fileWriter.write("注意：当前文件夹下所有文件勿动！"+ System.lineSeparator() + System.lineSeparator() +
						"**************** 离线时所有日志文件都备份在当前文件夹，删除或者移动会导致日志丢失！****************");
		        fileWriter.flush();
		        fileWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
				monitor.error("离线日志备份文件夹创建异常：",e);
			}
		}
		return logPath;
	}
	
}
