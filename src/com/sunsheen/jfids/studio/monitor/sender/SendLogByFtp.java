package com.sunsheen.jfids.studio.monitor.sender;

import java.io.File;
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
import com.sunsheen.jfids.studio.monitor.utils.remote.ServersDebugUtil;
/**
 * 上传到远程服务器
 * @author WangSong
 *
 */
public class SendLogByFtp {
	private final static HKMonitor monitor = HKMonitorFactory.getLogger(SendLogByFtp.class.getName());
	
	/**
	 * 多个文件上传（文件跟文件夹对应）
	 * @param files		上传的所有文件
	 * @param remoteFolders	每个需要上传文件对应的远程文件夹
	 */
	public static void postFile(File[] files,String[] remoteFolders){
		
		//上传文件的个数应该跟对应文件夹相同
		if((null==files || null==remoteFolders) || (files.length != remoteFolders.length) ||
				files.length == 0 || remoteFolders.length == 0)
			return;
				
		String username = LogInfo.SERVERS_USERNAME;
		String password = LogInfo.SERVERS_PASSWORD;
		String address = LogInfo.SERVERS_ADDRESS;
		int port = LogInfo.SERVERS_FTP_PORT;
		
		ChannelSftp sftp = null;
        Channel channel = null;
        Session sshSession = null;
        int uploadIndex = 0;//记录有异常文件夹的下标
        int mkdirIndex = 0;//记录存在当前时间文件夹的项目下标

        try {
        	//创建连接
            JSch jsch = new JSch();
            sshSession = jsch.getSession(username, address, port);
            sshSession.setPassword(password);
            //获取session
            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            sshSession.setConfig(sshConfig);
            sshSession.connect();
            //得到sftp
            channel = sshSession.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
            
    		//每个远程下面需要对应一个当天时间
            String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            StringBuffer sb = null;
            for(String folder : remoteFolders){
            	//保证不重复创建当前日期文件夹
            	if(!remoteFolders[mkdirIndex].contains(today)){
                	sb = new StringBuffer(folder).append("/" + today);
            		remoteFolders[mkdirIndex] = sb.toString();
                	sftp.mkdir(remoteFolders[mkdirIndex]);
//                	ServersDebugUtil.transferCommand("mkdir -p " + remoteFolders[mkdirIndex]);
            	}
            	mkdirIndex ++;
            }
            
			//上传
			for(int i=0;i<files.length;i++){
				uploadIndex = i;
	            sftp.cd(remoteFolders[i]);//进入对应存放日志文件的目录
	            //传入文件
	            String newName = files[i].getName(); //com.sunsheen.jfids.studio.umleditor(1).zip
				if(newName.contains("("))
					newName = newName.substring(0,newName.lastIndexOf("(")) + ".zip";
				sftp.put(new FileInputStream(files[i]), newName);//写入文件
				monitor.info("【"+ newName.substring(0,newName.lastIndexOf(".")) +"】项目日志已上传！");
			}
			
        } catch (Exception e) {
//        	System.out.println("上传日志文件："+e.toString());
        	//远程服务器已经存在当前日期的文件夹
        	if(e.toString().contains("4: Failure")){
//        		System.out.println("服务器已经存在【"+remoteFolders[mkdirIndex]+"】文件夹！");
        		try{
        			sftp.cd(remoteFolders[mkdirIndex]);//进入对应存放日志文件的目录
    	            //传入文件
    	            String newName = files[mkdirIndex].getName(); //com.sunsheen.jfids.studio.umleditor(1).zip
    	            //保证服务器上一个项目只有一份当前最新的log文件
    				if(newName.contains("("))
    					newName = newName.substring(0,newName.lastIndexOf("(")) + ".zip";
    				sftp.put(new FileInputStream(files[mkdirIndex]), newName);//写入文件
    				monitor.info("【"+ newName.substring(0,newName.lastIndexOf(".")) +"】项目日志已上传！");
    				/** 保证所有项目都创建了子日期文件夹 **/
//    				if(mkdirIndex < remoteFolders.length-1){
//						SendLogByFtp.postFile(files, remoteFolders);
//					}
    				//排除已经上传过文件的文件夹
    				String[] waitingFolders = new String[remoteFolders.length-1];
    				System.arraycopy(remoteFolders, 1, waitingFolders, 0, waitingFolders.length);
    				//排除已经上传过文件
    				File[] waitingFiles = new File[files.length - 1];
    				System.arraycopy(files, 1, waitingFiles, 0, waitingFiles.length);
    				//上传
    				SendLogByFtp.postFile(waitingFiles, waitingFolders);
        		}catch(Exception mkdirE){
        			mkdirE.printStackTrace();
        		}
        	}
        	
        	//没有文件夹，重新服务器创建文件夹并上传文件
        	else if(e.toString().contains("No such file")){
				try {
					System.out.println("自动创建需要的文件夹(上传文件时没有找到对应文件夹).");
	        		//创建文件夹
	        		String folder = remoteFolders[uploadIndex];
//	        		sftp.mkdir(folder);
//	        		ServersDebugUtil.transferCommand("mkdir  -p  " + folder);
	        		String fatherPath = folder.substring(0,folder.indexOf("com."));
	        		sftp.cd(fatherPath);
	        		String[] paths = folder.substring(folder.indexOf("com."),folder.length()).split("/");
	        		for(String path : paths){
	        			sftp.mkdir(path);
	        			sftp.cd(path);
	        		}
					//创建文件
					File file = files[uploadIndex];
					sftp.cd(folder);//进入对应存放日志文件的目录
					String newName = file.getName();
					//保证服务器只有一个最新文件
					if(newName.contains("("))
						newName = newName.substring(0,newName.lastIndexOf("(")) + ".zip";
					sftp.put(new FileInputStream(file), newName);
					monitor.info("【"+ newName.substring(0,newName.lastIndexOf(".")) +"】项目日志已上传！");
				} catch (Exception e1  ) {
					e1.printStackTrace();
				} 
        	}
        } finally {
        	//关闭sftp信道
        	if (sftp != null) {
                if (sftp.isConnected()) {
                	sftp.disconnect();
                }
            }
            //关闭channel管道
        	if (channel != null) {
                if (channel.isConnected()) {
                	channel.disconnect();
                }
            }
            //关闭session
        	if (sshSession != null) {
                if (sshSession.isConnected()) {
                	sshSession.disconnect();
                }
            }
        }		
	}
	
	/**
	 * 单个文件上传
	 * @param file		上传的文件
	 * @param remoteFolder	上传文件对应的远程文件夹
	 * @param uploadFileName	上传文件的名字
	 */
	public static void postFile(File file,String remoteFolder,String uploadFileName){
		
		//上传文件的个数应该跟对应文件夹相同
		if((null==file || null==remoteFolder))
			return;
		
		String username = LogInfo.SERVERS_USERNAME;
		String password = LogInfo.SERVERS_PASSWORD;
		String address = LogInfo.SERVERS_ADDRESS;
		int port = LogInfo.SERVERS_FTP_PORT;
		
		ChannelSftp sftp = null;
        Channel channel = null;
        Session sshSession = null;
        try {
        	//创建连接
            JSch jsch = new JSch();
            sshSession = jsch.getSession(username, address, port);
            sshSession.setPassword(password);
            //获取session
            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            sshSession.setConfig(sshConfig);
            sshSession.connect();
            //得到sftp
            channel = sshSession.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
            
            sftp.cd(remoteFolder);//进入对应存放日志文件的目录
			sftp.put(new FileInputStream(file), uploadFileName);//写入文件
			
        } catch (Exception e) {
//        	monitor.error("上传日志文件失败！" ,e);
            e.printStackTrace();
        } finally {
        	//关闭sftp信道
        	if (sftp != null) {
                if (sftp.isConnected()) {
                	sftp.disconnect();
                }
            }
            //关闭channel管道
        	if (channel != null) {
                if (channel.isConnected()) {
                	channel.disconnect();
                }
            }
            //关闭session
        	if (sshSession != null) {
                if (sshSession.isConnected()) {
                	sshSession.disconnect();
                }
            }
        }		
	}
	
	/**
	 * 离线zip文件上传
	 * @param zip
	 * @param serversFolders
	 */
	public static void offlineLogsUpload(Map<String,File> toUploadZips){
		
		if(toUploadZips.isEmpty())
			return;
		
		String username = LogInfo.SERVERS_USERNAME;
		String password = LogInfo.SERVERS_PASSWORD;
		String address = LogInfo.SERVERS_ADDRESS;
		int port = LogInfo.SERVERS_FTP_PORT;
		
		ChannelSftp sftp = null;
        Channel channel = null;
        Session sshSession = null;
        
        String errorFolder = null;
        try {
        	//创建连接
            JSch jsch = new JSch();
            sshSession = jsch.getSession(username, address, port);
            sshSession.setPassword(password);
            //获取session
            Properties sshConfig = new Properties();
            sshConfig.put("StrictHostKeyChecking", "no");
            sshSession.setConfig(sshConfig);
            sshSession.connect();
            //得到sftp
            channel = sshSession.openChannel("sftp");
            channel.connect();
            sftp = (ChannelSftp) channel;
			for(String path : toUploadZips.keySet()){
				errorFolder = path;
				sftp.cd(path);
				sftp.put(new FileInputStream(toUploadZips.get(path)),toUploadZips.get(path).getName());
			}
        } catch (Exception e) {
        	if(e.toString().contains("No such file")){
        		System.out.println("服务器【"+errorFolder+"】文件夹不存在！[自动创建]");
        		try {
					sftp.mkdir(errorFolder);
					SendLogByFtp.offlineLogsUpload(toUploadZips);//直到所有的文件上传完成
				} catch (SftpException e1) {
					e1.printStackTrace();
				}
        	}
            e.printStackTrace();
        } finally {
        	//关闭sftp信道
        	if (sftp != null) {
                if (sftp.isConnected()) {
                	sftp.disconnect();
                }
            }
            //关闭channel管道
        	if (channel != null) {
                if (channel.isConnected()) {
                	channel.disconnect();
                }
            }
            //关闭session
        	if (sshSession != null) {
                if (sshSession.isConnected()) {
                	sshSession.disconnect();
                }
            }
        }		
	}
}
