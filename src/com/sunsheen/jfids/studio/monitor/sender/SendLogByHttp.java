package com.sunsheen.jfids.studio.monitor.sender;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.FormBodyPart;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.eclipse.core.runtime.Assert;

/**
 * 發送日誌文件到服務器 ：需要将文件打包压缩
 * 
 * @author WangSong
 *
 */
public class SendLogByHttp {

	/**
	 * 发送日志文件
	 * @param url		远程地址
	 * @param param		String类型的map数据，可以为空
	 * @param file		上传的文件
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static String postFile(String url, Map<String, Object> param,
			File file) throws ClientProtocolException, IOException {
		
		String res = null;
		CloseableHttpClient httpClient = HttpClients.createDefault();//创建http客户端
		HttpPost httppost = new HttpPost(url);
		httppost.setEntity(getMutipartEntry(param, file));//设置发送的消息体
		
		CloseableHttpResponse response = httpClient.execute(httppost);//发送消息到指定服务器 
		
		HttpEntity entity = response.getEntity();
		
		if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
			res = EntityUtils.toString(entity, "UTF-8");
			response.close();
		} else {
			res = EntityUtils.toString(entity, "UTF-8");
			response.close();
			throw new IllegalArgumentException(res);
		}
		return res;
	}

	//得到当前文件实体
	private static MultipartEntity getMutipartEntry(Map<String, Object> param,
			File file) throws UnsupportedEncodingException {
		Assert.isTrue(null == file, "文件不能为空！");
		
		FileBody fileBody = new FileBody(file);//通过文件路径，得到文件体
		FormBodyPart filePart = new FormBodyPart("file", fileBody);//格式化
		MultipartEntity multipartEntity = new MultipartEntity();
		multipartEntity.addPart(filePart);

		if(null != param){
			Iterator<String> iterator = param.keySet().iterator();
			while (iterator.hasNext()) {
				String key = iterator.next();
				FormBodyPart field = new FormBodyPart(key, new StringBody(
						(String) param.get(key)));
				multipartEntity.addPart(field);
			}
		}

		return multipartEntity;
	}

}
