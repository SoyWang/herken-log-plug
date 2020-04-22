package com.sunsheen.jfids.studio.monitor.download;

import java.io.File;
import java.util.List;
import java.util.Properties;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
import com.sunsheen.jfids.studio.monitor.utils.remote.ServersFolderUtil;

/**
 * 下载服务器上的日志文件到本地
 * 
 * @author WangSong
 *
 */
public class DownloadLog {
	private final static HKMonitor monitor = HKMonitorFactory.getLogger(DownloadLog.class.getName());

	/**
	 * 下载指定文件夹日志文件
	 * @param folder	服务器上存放日志文件的项目名
	 */
	public static void downloadLogs(String... folders)  {
		String username = LogInfo.SERVERS_USERNAME;
		String password = LogInfo.SERVERS_PASSWORD;
		String address = LogInfo.SERVERS_ADDRESS;
		int port = LogInfo.SERVERS_FTP_PORT;
		
		ChannelSftp sftp = null;
		Channel channel = null;
		Session sshSession = null;
		try {
			// 创建连接
			JSch jsch = new JSch();
			sshSession = jsch.getSession(username, address, port);
			sshSession.setPassword(password);
			// 获取session
			Properties sshConfig = new Properties();
			sshConfig.put("StrictHostKeyChecking", "no");
			sshSession.setConfig(sshConfig);
			sshSession.connect();
			// 得到sftp
			channel = sshSession.openChannel("sftp");
			channel.connect();
			sftp = (ChannelSftp) channel;
			for(String folder : folders){
				// 进入存放日志文件的目录
				sftp.cd(LogInfo.SERVERS_RECIVE_FOLDER +"/"+ folder);
				// 下载
				String localFile = LogInfo.DOWNLOAD_FILE_LOCATION + File.separator + folder;//存放下载文件的位置
				File downloadFile = new File(localFile);
				if(!downloadFile.exists())
					downloadFile.mkdirs();
				String serversFile = LogInfo.SERVERS_RECIVE_FOLDER + "/" + folder + "/" + folder + ".zip";//要下载文件文件位置
				sftp.get(serversFile,localFile);
				System.out.println("【"+ folder + ".zip】下载完成！");
				monitor.info("【"+ folder + ".zip】下载完成！");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			// 关闭sftp信道
			if (sftp != null) {
				if (sftp.isConnected()) {
					sftp.disconnect();
				}
			}
			// 关闭channel管道
			if (channel != null) {
				if (channel.isConnected()) {
					channel.disconnect();
				}
			}
			// 关闭session
			if (sshSession != null) {
				if (sshSession.isConnected()) {
					sshSession.disconnect();
				}
			}
		}
	}
	
	/**
	 * 下载服务器上所有的日志文件
	 */
	public static void downloadLogs(){
		//取出当前服务器上面所有的日志文件夹
		List<Object> serversLogFolders = 
				ServersFolderUtil.getSubfolderName(LogInfo.SERVERS_RECIVE_FOLDER);
		//下载
		downloadLogs(serversLogFolders.toArray(new String[serversLogFolders.size()]));
	} 
	

}
