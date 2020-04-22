package com.sunsheen.jfids.studio.monitor.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.eclipse.core.runtime.Assert;

import com.sunsheen.jfids.studio.monitor.HKMonitor;
import com.sunsheen.jfids.studio.monitor.HKMonitorFactory;

import ch.ethz.ssh2.ChannelCondition;
import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
/**
 * 多文件的打包压缩跟解压
 * @author WangSong
 *
 */
public class ZipFileUtil {
    private final static HKMonitor moniter = HKMonitorFactory.getLogger(ZipFileUtil.class.getName());

    /**
     * 压缩离线备份log文件成一个zip
     * @param srcFiles
     * @param zipFile
     * @return
     */
    public static File zipOfflineLogs(File[] srcFiles,File zipFile){
    	if(srcFiles.length == 0 || null==srcFiles)
            return new File("");
        
        byte[] buf=new byte[1024];
        try {
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));//定义要写入的文件
            for(int i=0;i<srcFiles.length;i++){
                FileInputStream in = new FileInputStream(srcFiles[i]);//读取文件
                out.putNextEntry(new ZipEntry(srcFiles[i].getName()));//设置内文件名
                //写入数据
                int length;
                while((length = in.read(buf)) > 0){
                        out.write(buf,0,length);
                }
                out.closeEntry();
                in.close();
                }
                out.close();
//                System.out.println("文件【"+zipFile.getName()+"】压缩完成！");
          } catch (Exception e) {
              e.printStackTrace();
          }
    	
    	return zipFile;
    }
    
	  /**
	   * 功能:压缩多个log文件成一个zip文件（本地）
       * @param srcfile：源文件列表
       * @param zipfile：压缩后的文件
	   * @return	压缩后的文件
	   */
      public static File zipFiles(File[] srcfile,File zipfile){
          if(srcfile.length == 0 || null==srcfile)
              return null;
          
          //存在当前压缩文件时，保存新文件（文件名 + 1）	eg:log.zip  ---->  log(1).zip
          if(zipfile.exists() && zipfile.length() != 0){
        	  File[] files = new File(zipfile.getParent()).listFiles();//当前目录的所有zip文件
        	  //截取出当前需要增加数字标号的名字部分
        	  String[] sourceArray = (zipfile.getName()).split("\\.");//将\\. 转义成.
        	  //防止异常
        	  Assert.isTrue(sourceArray.length != 0, "程序异常：文件名划分错误！");
        	  String change = sourceArray[sourceArray.length - 2];//get需要修改部分  eg:monitor
        	  //备份第二个时，增加数字下标
        	  if(files.length>0 && files.length<2){
        		  change = change + "("+ 1 +")";
        		  sourceArray[sourceArray.length - 2] = change;//需要修改部分重新赋值
        		  StringBuffer newName = new StringBuffer();
        		  for(String source : sourceArray){
        			  //最后一次添加不能添加：.
        			  if(source.equals(sourceArray[sourceArray.length - 1])){
        				  newName.append(source);
        				  break;
        			  }
        			  newName.append(source+".");
        		  }
        		  String path = zipfile.getPath();
        		  path = path.substring(0, path.lastIndexOf(File.separator));
            	  zipfile = new File(path + File.separator + newName);
        	  }
        	  //需要排除掉第一个备份（没有数字的）,且最大数字+1
        	  else if(files.length >= 2){        		  
        		  int[] nums = new int[files.length - 1];
        		  int k = 0;
        		  //取出当前文件夹下所有文件名的数字部分
        		  for(File file : files){
        			  //排除第一次备份（没有数字标号）的
        			  if((zipfile.getName()).equals(file.getName()))
        				  continue;
            		  
                	 String[] oldArray = null;	//存放切割出来的zip文件名
                	 String target = null; //有数字的字符串
                	 int index = 0;
                	 oldArray = (file.getName()).split("\\.");
         			 target = oldArray[oldArray.length - 2];
        			 index = Integer.parseInt(target.substring(target.lastIndexOf("(") + 1, target.lastIndexOf(")")));
        			 nums[k] = index;
        			 k++;
        		  }
        		  //找出最大的数字
        		  int max=0;
        		  for(int i=0;i<nums.length;i++){
        			  if(nums[i] > max){
        				  	max=nums[i];
        			  }
        		  }
        		  //重新设置数字
        		  max++;
        		  change = change + "("+ max +")";
        		  sourceArray[sourceArray.length - 2] = change;//需要修改部分重新赋值
        		  StringBuffer newName = new StringBuffer();
        		  for(String source : sourceArray){
        			  //最后一次添加后退出
        			  if(source.equals(sourceArray[sourceArray.length - 1])){
        				  newName.append(source);
        				  break;
        			  }
        			  newName.append(source+".");
        		  }
        		  String path = zipfile.getPath();
        		  path = path.substring(0, path.lastIndexOf(File.separator));
            	  zipfile = new File(path + File.separator + newName);
        	  }
          }
 
          //压缩
          byte[] buf=new byte[1024];
          try {
              //ZipOutputStream ：完成文件或文件夹的压缩
              ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipfile));
              for(int i=0;i<srcfile.length;i++){
            	  if(!srcfile[i].exists())
            		  continue;
                  FileInputStream in = new FileInputStream(srcfile[i]);//读取文件
                  String zipName = srcfile[i].getName();
                  out.putNextEntry(new ZipEntry(zipName));//设置内文件名
                  //写入数据
                  int length;
                  while((length = in.read(buf)) > 0){
                          out.write(buf,0,length);
                  }
                  out.closeEntry();
                  in.close();
              	}
              	out.close();
            } catch (Exception e) {
            	e.printStackTrace();
            }
          return zipfile;
       }
      
      /**
       * 功能:解压缩（本地）
       * @param zipfile：需要解压缩的文件
       * @param descDir：解压后的目标目录
       */
      public static void unZipFiles(File zipfile,String descDir){
     
          try {
              ZipFile zf = new ZipFile(zipfile);//格式化
              //循环遍历出压缩的每个文件
              for(Enumeration entries = zf.entries();entries.hasMoreElements();){
                  ZipEntry entry = (ZipEntry) entries.nextElement();
                  String zipEntryName = entry.getName();//当前压缩文件中文件名
                  InputStream in = zf.getInputStream(entry);
                  
                  File mkFile = new File(descDir + zipEntryName);
                  mkFile.createNewFile();
                  
                  OutputStream out=new FileOutputStream(descDir + zipEntryName);
                  byte[] buf1 = new byte[1024];
                  int len;
                  while((len=in.read(buf1)) > 0){
                      out.write(buf1,0,len);
                  }
                  in.close();
                  out.close();
                  moniter.info("文件【"+zipfile.getName()+"】，解压缩完成！");
                  System.out.println("解压缩完成.");
              }
            } catch (Exception e) {
                e.printStackTrace();
            }
      }
      
      /**
       * 壓縮指定文件夾下所有文件
       * @param targetZipFile    压缩后的文件     eg：d://logs//log.zip
       * @param logFolder    日志文件所在的文件夹    eg：e://logs
       * @return
       */
      public static File zipLogs(String targetZipFile,String logFolder){
        //定义日志压缩文件
        File[] logsFileArr = new File(logFolder).listFiles();//所有的日志文件
        //創建一個空的目標zip文件
        File resultZip = new File(targetZipFile);
        //壓縮
        zipFiles(logsFileArr, resultZip);
        return resultZip;
      }
      
      
      /**
       * 解压文件（远程）
       * @param romoteAddr    服务器地址
       * @param username    服务器用户名
       * @param password    服务器密码
       * @param targetFolder    服务器上需要解压文件的目录    eg:/usr/local/hk-logs
       * @param zipFileName    需要解压文件的文件名    eg:logs.zip
       */
      public static void remoteUnZip(String romoteAddr,String username,String password,
              String targetFolder,String zipFileName){
          
          try {
              Connection connection = new Connection(romoteAddr);// 创建一个连接实例
              connection.connect();// Now connect
              boolean isAuthenticated = connection.authenticateWithPassword(username, password);//認證
              Assert.isTrue(isAuthenticated, "用戶名或者密碼錯誤！");
              Session sess = connection.openSession();// 創建一個會話
              sess.requestPTY("bash");
              sess.startShell();
              InputStream stdout = new StreamGobbler(sess.getStdout());
              InputStream stderr = new StreamGobbler(sess.getStderr());
              BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(stdout));
              BufferedReader stderrReader = new BufferedReader(new InputStreamReader(stderr));
              //向服务器上输入命令
              PrintWriter out = new PrintWriter(sess.getStdin());
              out.println("cd " + targetFolder);//進入日志文件存放的目录
              out.println("ll");
              out.println("unzip -o -d "+targetFolder+" "+zipFileName);//解压文件到指定位置
              out.println("ll");
              out.println("exit");
              out.close();
              sess.waitForCondition(ChannelCondition.CLOSED|ChannelCondition.EOF | ChannelCondition.EXIT_STATUS,30000);
              //本机查看远程操作的指令及状态
              showRemoteShell(stdoutReader,stderrReader,sess);
              //查看当前退出状态
              System.out.println("ExitCode: " + sess.getExitStatus());
              //关闭连接
              sess.close();
              connection.close();
          } catch (IOException e) {
              moniter.error("远程解压文件【"+ zipFileName +"】，错误：" + e);
              e.printStackTrace(System.err);
//              System.exit(2);
          }
      }
      
      //打印远程指令及状态
      private static void showRemoteShell(BufferedReader stdoutReader,BufferedReader stderrReader,
              Session sess) throws IOException{
          System.out.println("输入在服务器的指令：");
          while (true) {
              String line = stdoutReader.readLine();
              if (line == null)
                  break;
              System.out.println(line);
          }
          System.out.println("输入指令后对应的显示信息：");
          while (true) {
              String line = stderrReader.readLine();
              if (line == null)
                  break;
              System.out.println(line);
          }
      }
      
}