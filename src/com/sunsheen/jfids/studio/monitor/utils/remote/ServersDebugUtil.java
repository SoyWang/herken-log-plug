package com.sunsheen.jfids.studio.monitor.utils.remote;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Properties;

import org.eclipse.core.runtime.Assert;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.StreamGobbler;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;

/**
 * 输入指令到服务器执行
 * @author WangSong
 *
 */
public class ServersDebugUtil {
	
	private static final HKMonitor monitor = HKMonitorFactory.getLogger(ServersDebugUtil.class.getName());

	private ServersDebugUtil(){}
	
	/**
	 * 转换指令到服务器执行
	 * @param command	要执行的指令
	 */
	public static void transferCommand(String... commands){
		String romoteAddr = LogInfo.SERVERS_ADDRESS;
		String username = LogInfo.SERVERS_USERNAME;
		String password = LogInfo.SERVERS_PASSWORD;
		try {
			  Connection connection = new Connection(romoteAddr);// 创建一个连接实例
			  connection.connect();// Now connect
			  boolean isAuthenticated = connection.authenticateWithPassword(username, password);//認證
			  Assert.isTrue(isAuthenticated, "用戶名或密碼錯誤！");
			  ch.ethz.ssh2.Session sess = connection.openSession();// 創建一個會話
			  sess.requestPTY("bash");
			  sess.startShell();
			  InputStream stdout = new StreamGobbler(sess.getStdout());
			  InputStream stderr = new StreamGobbler(sess.getStderr());
			  BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));
			  BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));
			  //向服务器上输入命令
			  PrintWriter out = new PrintWriter(sess.getStdin());
			  for(String command : commands){
				  out.println(command);
			  }
			  out.close();
			  sess.waitForCondition(ChannelCondition.CLOSED|ChannelCondition.EOF | ChannelCondition.EXIT_STATUS,100);
			  //关闭连接
			  sess.close();
			  connection.close();
			  stderrReader.close();
			  stdoutReader.close();
		  } catch (IOException e) {
			  e.printStackTrace();
			  monitor.error("服务器执行指令出错：", e);
		  }
	}
	
	/**
	 * 文件夹校验
	 * @param commands		进入对应文件夹指令集：第一个参数传log目录；第二个参数传用户log目录
	 */
	public static void foldedrCheck(String... commands){
		String username = LogInfo.SERVERS_USERNAME;
		String password = LogInfo.SERVERS_PASSWORD;
		String address = LogInfo.SERVERS_ADDRESS;
		int port = LogInfo.SERVERS_FTP_PORT;
		
		ChannelSftp sftp = null;
        Channel channel = null;
        Session sshSession = null;
        int index = 0;//记录有异常文件夹的下标
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
			//进入文件夹
			for(int i=0;i<commands.length;i++){
				index = i;
	            sftp.cd(commands[i]);//进入对应存放日志文件的目录
			}
			
        } catch (Exception e) {
			  if(e.toString().contains("No such file")){
				  System.out.println("服务器自动创建【"+commands[index]+"】文件夹");
				  try{
					  //创建父文件夹
					  if(index == 0){
						  sftp.mkdir(LogInfo.SERVERS_PARENT_USER_FOLDER);
						  System.out.println("服务器创建【"+ LogInfo.SERVERS_PARENT_USER_FOLDER+"】文件夹成功！");
						  monitor.info("服务器创建【"+ LogInfo.SERVERS_PARENT_USER_FOLDER+"】文件夹成功！");
					  }
					  //创建用户层
					  else if(index == 1){
						  sftp.mkdir(LogInfo.SERVERS_RECIVE_FOLDER);
						  System.out.println("服务器创建【"+ LogInfo.SERVERS_RECIVE_FOLDER+"】文件夹成功！");
						  monitor.info("服务器创建【"+ LogInfo.SERVERS_RECIVE_FOLDER+"】文件夹成功！");
					  }
					  //TODO 传入的其他指令
					  
				  }catch(SftpException e1){
					  e1.printStackTrace();
					  monitor.error("服务器创建用户层目录失败："+e1);
//					  System.exit(2);
				  }
		  }
	}
	}
}
