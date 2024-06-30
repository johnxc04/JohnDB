/*
 *@Type SocketServerHandler.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 12:50
 * @version
 */
package org.yy.controller;

import org.yy.dto.ActionDTO;
import org.yy.dto.ActionTypeEnum;
import org.yy.dto.RespDTO;
import org.yy.dto.RespStatusTypeEnum;
import org.yy.service.Store;
import org.yy.utils.LoggerUtil;

import java.io.*;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SocketServerHandler implements Runnable {
    private final Logger LOGGER = LoggerFactory.getLogger(SocketServerHandler.class);
    private final Socket socket;
    private final Store store;

    public SocketServerHandler(Socket socket, Store store) {
        this.socket = socket;
        this.store = store;
    }

    @Override
    public void run() {
        try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {

            // 接收序列化对象
            ActionDTO dto = (ActionDTO) ois.readObject();
            LoggerUtil.debug(LOGGER, "[SocketServerHandler][ActionDTO]: {}", dto.toString());
            System.out.println("服务器接收到来自客户端的请求:" + dto.toString());

            // 处理命令逻辑(TODO://改成可动态适配的模式)
            if (dto.getType() == ActionTypeEnum.GET) {
                String value = this.store.Get(dto.getKey());
                LoggerUtil.debug(LOGGER, "[SocketServerHandler][run]: {}", "get action resp" + dto.toString());
                RespDTO resp = new RespDTO(RespStatusTypeEnum.SUCCESS, value);
                oos.writeObject(resp);
                oos.flush();
            }
            if (dto.getType() == ActionTypeEnum.SET) {
                this.store.Set(dto.getKey(), dto.getValue());
                String value = this.store.Get(dto.getKey());
                LoggerUtil.debug(LOGGER, "[SocketServerHandler][run]: {}", "set action resp" + dto.toString());
                RespDTO resp = new RespDTO(RespStatusTypeEnum.SUCCESS, value);
                oos.writeObject(resp);
                oos.flush();
            }
            if (dto.getType() == ActionTypeEnum.RM) {
                String value = this.store.Get(dto.getKey());
                this.store.Remove(dto.getKey());
                LoggerUtil.debug(LOGGER, "[SocketServerHandler][run]: {}", "rm action resp" + dto.toString());
                RespDTO resp = new RespDTO(RespStatusTypeEnum.SUCCESS, value);
                oos.writeObject(resp);
                oos.flush();
            }

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}
