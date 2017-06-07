/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryonet;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.FrameworkMessage.KeepAlive;

public class MultipleSlowClientsTest extends KryoNetTestCase {

	public void testMultipleSlowClients () throws IOException {
		
		/**
		 * Client发给Server的消息
		 */
		final String client2SeverMessage = "Hello World From Slow Client.";
		
		/**
		 * Server完整接收之后给Client回复的消息
		 */
		final String server2ClientMessage = "Finished.";
		
		/**
		 * Client每发送一个字符（char)就等待如下时间
		 */
		final int sleepMillis = 1000;
		/**
		 * Client数目
		 */
		final int clients = 1000;
		
		/**
		 * Connection Timeout设置
		 */
		final int connectionTimeout =60*1000;
		
		/**
		 * TestCase Timeout设置
		 */
		final int testcaseTimeout = 60*1000;

		final Server server = new Server(16384, 8192);
		server.getKryo().register(String[].class);
		startEndPoint(server);
		server.bind(tcpPort, udpPort);
		server.addListener(new Listener() {
			Map<String,StringBuilder> contentMap = new HashMap();
			
			String msg = client2SeverMessage;
			
			public void received (Connection connection, Object object) {
				if (object instanceof KeepAlive){
					return;
				}
				
				String clientId = connection.getRemoteAddressTCP().toString();
				Byte rawValue = (Byte)object;

				if (contentMap.containsKey(clientId)){
					contentMap.get(clientId).append((char)rawValue.byteValue());
					System.out.println("Timestamp=" + System.currentTimeMillis() + " update content of clientId=" + clientId + " ReceivedBuf=" + contentMap.get(clientId).toString());
					if (contentMap.get(clientId).toString().equals(msg)){
						try {
							System.out.println("Timestamp=" + System.currentTimeMillis() +" Connection id=" + connection.getID() + " server send confirm.");
							connection.tcp.send(connection, server2ClientMessage);
						} catch (IOException e) {
							e.printStackTrace();
						} finally{
							contentMap.remove(clientId);
							System.out.println("Timestamp=" + System.currentTimeMillis() + " discard content of clientId=" + clientId);
							connection.close();
						}
					}
				}else{
					contentMap.put(clientId, new StringBuilder());
					contentMap.get(clientId).append((char)rawValue.byteValue());
					System.out.println("Timestamp=" + System.currentTimeMillis() + " create StringBuilder for client id=" + clientId);
				}				
			}
		});

		// ----

		for (int i = 0; i < clients; i++) {
			Client client = new Client(16384, 8192);
			client.getKryo().register(String[].class);
			startEndPoint(client);
			client.addListener(new Listener() {
				int index = 0;
				
				public void connected (Connection connection){
					while (index < client2SeverMessage.getBytes().length){
						connection.sendTCP(client2SeverMessage.getBytes()[index++]);
						try {
							Thread.sleep(sleepMillis);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					}
				}
				
				public void received (Connection connection, Object object){
					if (!(object instanceof KeepAlive)){
						assertEquals(server2ClientMessage, (String)object);
					}
					
				}
			});
			client.connect(connectionTimeout, host, tcpPort, udpPort);
		}

		waitForThreads(testcaseTimeout);
	}
}
