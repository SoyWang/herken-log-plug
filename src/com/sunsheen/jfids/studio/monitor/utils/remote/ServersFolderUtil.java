package com.sunsheen.jfids.studio.monitor.utils.remote;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;

import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;
import com.sunsheen.jfids.studio.monitor.common.LogInfo;
import com.sunsheen.jfids.studio.monitor.timer.MonitorOffline;

/**
 * 遍历出远程服务器上指定目录下的所有文件夹
 * 
 * @author WangSong
 *
 */
public class ServersFolderUtil {
	private static final HKMonitor monitor = HKMonitorFactory.getLogger(ServersFolderUtil.class.getName());

	private ServersFolderUtil() {
	}

	/**
	 * 得到服务器上指定文件夹下所有子文件夹（第一级子文件夹）
	 * 
	 * @return
	 */
	public static List<Object> getSubfolderName(final String targetFolder) {
		final String romoteAddr = LogInfo.SERVERS_ADDRESS;
		final String username = LogInfo.SERVERS_USERNAME;
		final String password = LogInfo.SERVERS_PASSWORD;

		List<Object> folderNameList = new ArrayList<Object>();
		try {
			Connection connection = new Connection(romoteAddr);// 创建一个连接实例
			connection.connect();// 没有网络连接时，这一步会抛出连接异常
			boolean isAuthenticated = connection.authenticateWithPassword(username, password);// 認證
			Assert.isTrue(isAuthenticated, "用戶名或密碼錯誤！");
			ch.ethz.ssh2.Session sess = connection.openSession();// 創建一個會話
			sess.requestPTY("bash");
			sess.startShell();
			InputStream stdout = new StreamGobbler(sess.getStdout());
			InputStream stderr = new StreamGobbler(sess.getStderr());
			BufferedReader stdoutReader = new BufferedReader(
					new InputStreamReader(stdout));
			BufferedReader stderrReader = new BufferedReader(
					new InputStreamReader(stderr));
			// 向服务器上输入命令
			PrintWriter out = new PrintWriter(sess.getStdin());
			out.println("cd " + targetFolder);// 進入日志文件存放的目录
			out.println("ls -ld */");
			out.println("exit");
			out.close();
			/** 如果服务器没有返回信息过来，线程现在一直在c.wait这里，等待服务器返回信息将他唤醒。 **/
			// TODO 需要解决当前阻塞...
			 sess.waitForCondition(ChannelCondition.CLOSED|ChannelCondition.EOF|ChannelCondition.EXIT_STATUS,30000);
			 while (true) {
                 String line = stdoutReader.readLine();//当前读取到的整行数据
                 //数据读取完，退出
                 if (line == null)
                     break;
                 //取出文件夹
                 if(line.contains("drwxr-xr-x")){
                     //取出正确的文件夹名
                     StringBuffer sb = 
                             new StringBuffer(line.substring(line.lastIndexOf(" "),line.lastIndexOf("/")));
                     line = sb.toString().replace(" ", "");
                     folderNameList.add(line); 
                 }
             }

			// 关闭连接
			sess.close();
			connection.close();
			stderrReader.close();
			stdoutReader.close();
		} catch (IOException e) {
			//如果没有网络连接，离线记录日志文件
			if(e.toString().contains("There was a problem while connecting to")){
				System.out.println("连接服务器失败:"+e);
				monitor.error("连接服务器失败:", e);
				//本地离线备份日志线程执行
				final MonitorOffline mo = new MonitorOffline();
				mo.monitor();
			}
//			e.printStackTrace(System.err);
//			System.exit(2);
		}
		return folderNameList;
	}

	/**
	 * 返回当前目录下所有文件夹
	 * @param remoteDir
	 * @return
	 */
	@Deprecated
	public static List<Object> getChildFolders(String remoteDir){
		List<Object> results = new ArrayList<Object>();//返回所有的文件夹名
		String[] resultArr = {};
		try {
			resultArr = getRemoteDirFileNames(remoteDir);
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (String line : resultArr) {
//			if (line.contains("drwxr-xr-x")) {
//				// 取出正确的文件夹名
//				StringBuffer sb = new StringBuffer(line.substring(
//						line.lastIndexOf(" "), line.lastIndexOf("/")));
//				line = sb.toString().replace(" ", "");
//				results.add(line);
//			}
			System.out.println(line);
		}
		return results;
	}
	
	/**
	 * 列出指定文件夹下所有文件
	 * 
	 * @param conn
	 * @param remoteDir
	 * @return
	 * @throws IOException 
	 */
	 public static String[] getRemoteDirFileNames(String remoteDir) throws IOException{
	 Connection conn = new Connection(LogInfo.SERVERS_ADDRESS);// 创建一个连接实例
	 conn.connect();// Now connect
		boolean isAuthenticated = conn.authenticateWithPassword(
				LogInfo.SERVERS_USERNAME, LogInfo.SERVERS_PASSWORD);// 認證
		Assert.isTrue(isAuthenticated, "用戶名或密碼錯誤！");
	 Session sess=null;
     try {
         sess = conn.openSession();
         sess.execCommand("ls -lt "+remoteDir);
         InputStream stdout = new StreamGobbler(sess.getStdout());
         InputStream stderr = new StreamGobbler(sess.getStderr());

         byte[] buffer = new byte[100];
         String result = null;
         while (true) {
             if ((stdout.available() == 0)) {
                 int conditions = sess.waitForCondition(ChannelCondition.STDOUT_DATA |
                         ChannelCondition.STDERR_DATA | ChannelCondition.EOF, 1000*5);
                 if ((conditions & ChannelCondition.TIMEOUT) != 0) {
                     break;//超时后退出循环，要保证超时时间内，脚本可以运行完成
                 }
                 if ((conditions & ChannelCondition.EOF) != 0) {
                     if ((conditions & (ChannelCondition.STDOUT_DATA |
                             ChannelCondition.STDERR_DATA)) == 0) {
                         break;
                     }
                 }
             }

             if(stdout!=null){
            	 ByteArrayOutputStream baos = new ByteArrayOutputStream();
					int i;
					while ((i = stdout.read()) != -1) {
						baos.write(i);
					}
					String fileNames = baos.toString();

					if (fileNames != null) {
						String[] resultArr = fileNames.split("\n");
						return resultArr;
             }

             while (stderr.available() > 0) {
                 int len = stderr.read(buffer);
                 if (len > 0){
                     result += new String(buffer, 0, len);
                 }
             }
         }
         }
         stdout.close();
         stderr.close();
     } catch (Exception e) {
//         log.info("获取指定目录下文件列表失败："+e.getMessage());
    	 System.out.println("获取指定目录下文件列表失败："+e.getMessage());
     }finally {
         sess.close();
     }
     return null;
	 }
	 
}
