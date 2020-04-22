package com.sunsheen.jfids.studio.monitor.common;

import java.io.File;

import org.eclipse.core.runtime.Platform;

import com.sunsheen.jfids.studio.monitor.utils.local.FolderPathUtil;

/**
 * 日志常量
 * @author WangSong
 *
 */
public class LogInfo {
	
	/**
	 * 服务器可达监测轮询时间（秒s）
	 */
	public final static long SERVERS_AVALIABLE_POLLING_TIME = 1L;
	
	/**
	 * 日志上传监测轮询时间（秒s）
	 */
	public final static long UPLOAD_POLLING_TIME = 60 * 10;
	
	/**
	 * 日志备份文件删除监测轮询时间（秒s）
	 */
	public final static long BACKUP_FILE_DELETION_POLLING_TIME = 60*60*12;
	
	/**
	 * 日志文件夹最大值（M）
	 */
	public final static int MAX_LOG_SIZE = 32;
	
	/**
	 * 服务器上存放日志文件的用户目录的上层目录
	 */
	public final static String SERVERS_PARENT_USER_FOLDER = "/usr/local/hk-logs";
	
	/**
	 * 服务器上存放日志文件的地址（有一层用户目录）
	 */
	public final static String SERVERS_RECIVE_FOLDER = FolderPathUtil.getServiceUserFolder();
	
	/**
	 * 服务器地址
	 */
	public final static String SERVERS_ADDRESS = "172.18.130.51";
	
	/**
	 * 服务器用户名
	 */
	public final static String SERVERS_USERNAME = "root";
	
	/**
	 * 服务器密码
	 */
	public final static String SERVERS_PASSWORD = "bigdata2010";
	
	/**
	 * 连接服务器端口号(socket)
	 */
	public final static int SERVERS_SOCKET_PORT = 6789;
	
	/**
	 * 连接服务器端口号(ftp)
	 */
	public final static int SERVERS_FTP_PORT = 22;
	
	/**
	 * 运行空间  eg：E:\\WangSong\\runtime-Eclipse应用程序
	 */
	public final static String RUNTIME_SPACE = Platform.getInstanceLocation().getURL().getPath();
	
	/**
	 * 项目文件夹的上层文件夹（所有插件项目运行空间文件夹）
	 */
	public final static String LOG_PARENT_PATH = ".metadata/.plugins/";
	
	/**
	 * 本地项目备份日志文件夹
	 */
	public final static String LOCAL_LOG_BACKUP_FOLDER_NAME = "hk-logs-backup"; 
	
	/**
	 * 本地项目离线备份日志文件夹
	 */
	public final static String LOCAL_LOG_OFFLINE_BACKUP_FOLDER_NAME = "hk-logs-offline";
	
	/**
	 * 压缩之后的文件存放位置
	 */
	public final static String LOCAL_LOG_ZIP_FILE_LOCATION = FolderPathUtil.getLogPath();
	
	/**
	 * 本地离线日志存放文件夹
	 */
	public final static String OFFLINE_LOG_BACKUP_PATH  = FolderPathUtil.getOfflineLogPath();
	
	/**
	 * 从远程下载的文件存放位置
	 */
	public final static String DOWNLOAD_FILE_LOCATION = "E:"+File.separator+"hk-logs-servers-download";
}
