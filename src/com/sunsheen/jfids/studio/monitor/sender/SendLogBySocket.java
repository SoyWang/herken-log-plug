package com.sunsheen.jfids.studio.monitor.sender;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.Socket;

/**
 * 通過socket通信，發送文件
 * @author WangSong
 *
 */
public class SendLogBySocket {

	/**
	 * 文件上傳
	 * @param address	远程服务器地址
	 * @param port		远程服务器开放的端口号
	 * @param file		上传的文件
	 */
	public static void postFile(String address,int port,File file) {
        Socket st = null;
        BufferedOutputStream bos = null;
        FileInputStream fis = null;
        try {
            //指定端口号
        	//InetAddress.getLocalHost();
            st = new Socket(address,port);
            //要上传文件位置
            bos = new BufferedOutputStream(st.getOutputStream());
            fis = new FileInputStream(file);
            int len = 0;
            byte b[] = new byte[1024];
            //文件写入
            while ((len = fis.read(b)) != -1) {
                bos.write(b, 0, len);
                bos.flush();
            }
            System.out.println("客户端上传完成！");
        }
        catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                //关闭资源
                fis.close();
                bos.close();
                st.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
	
}
