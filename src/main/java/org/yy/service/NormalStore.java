/*
 *@Type NormalStore.java
 * @Desc
 * @Author urmsone urmsone@163.com
 * @date 2024/6/13 02:07
 * @version
 */
package org.yy.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import org.yy.model.command.Command;
import org.yy.model.command.CommandPos;
import org.yy.model.command.RmCommand;
import org.yy.model.command.SetCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yy.utils.CommandUtil;
import org.yy.utils.LoggerUtil;
import org.yy.utils.RandomAccessFileUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NormalStore implements Store {

    public static final String TABLE = ".table";
    public static final String RW_MODE = "rw";
    public static final String NAME = "data";
    private final Logger LOGGER = LoggerFactory.getLogger(NormalStore.class);
    private final String logFormat = "[NormalStore][{}]: {}";

    /**
     * 内存表，类似缓存
     */
    private TreeMap<String, Command> memTable;

    /**
     * hash索引，存的是数据长度和偏移量
     * */
    @Getter
    private HashMap<String, CommandPos> index;

    /**
     * 数据目录
     */
    private final String dataDir;

    /**
     * 读写锁，支持多线程，并发安全写入
     */
    private final ReadWriteLock indexLock;

    /**
     * 暂存数据的日志句柄
     */
    private RandomAccessFile writerReader;

    /**
     * 持久化阈值
     */
    private final int storeThreshold = 5;

    /**
     * 计数
     */
    private int storeOperateNumber;

    public NormalStore(String dataDir) {
        this.dataDir = dataDir;
        this.indexLock = new ReentrantReadWriteLock();
        this.memTable = new TreeMap<String, Command>();
        this.index = new HashMap<>();
        storeOperateNumber = 0;

        File file = new File(dataDir);
        if (!file.exists()) {
            LoggerUtil.info(LOGGER,logFormat, "NormalStore","dataDir isn't exist,creating...");
            file.mkdirs();
        }
        this.reloadIndex();
    }

    public String genFilePath() {
        return this.dataDir + File.separator + NAME + TABLE;
    }

    public void reloadIndex() {
        try {
            RandomAccessFile file = new RandomAccessFile(this.genFilePath(), RW_MODE);
            long len = file.length();
            long start = 0;
            file.seek(start);
            while (start < len) {
                int cmdLen = file.readInt();
                byte[] bytes = new byte[cmdLen];
                file.read(bytes);
                JSONObject value = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8));
                Command command = CommandUtil.jsonToCommand(value);
                start += 4;
                if (command != null) {
                    CommandPos cmdPos = new CommandPos((int) start, cmdLen);
                    index.put(command.getKey(), cmdPos);
                    // 如果日志中记载该键值是被删除的，就将其从内存里删去
                    if (command.getClass().equals(RmCommand.class)) {
                        index.remove(command.getKey(), cmdPos);
                    }
                }
                start += cmdLen;
            }
            file.seek(file.length());
        } catch (Exception e) {
            e.printStackTrace();
        }
        LoggerUtil.debug(LOGGER, logFormat, "reload index: "+index.toString());
    }

    @Override
    public void Set(String key, String value) {
        try {
            SetCommand command = new SetCommand(key, value);
            byte[] commandBytes = command.toByte();
            // 加锁
            indexLock.writeLock().lock();
            // 先写内存表，内存表达到一定阀值再写进磁盘
            if (storeOperateNumber >= storeThreshold) {
                storeOperateNumber = 0;

//                RotateDataBaseFile();

                RotateDataBaseFile(this);
            }
            // 写table（wal）文件
            RandomAccessFileUtil.writeInt(this.genFilePath(), commandBytes.length);
            int pos = RandomAccessFileUtil.write(this.genFilePath(), commandBytes);
            // 保存到memTable
            // 添加索引
            CommandPos cmdPos = new CommandPos(pos, commandBytes.length);
            index.put(key, cmdPos);
            storeOperateNumber++;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public String Get(String key) {
        try {
            indexLock.readLock().lock();
            // 从索引中获取信息
            CommandPos cmdPos = index.get(key);
            if (cmdPos == null) {
                return null;
            }
            byte[] commandBytes = RandomAccessFileUtil.readByIndex(this.genFilePath(), cmdPos.getPos(), cmdPos.getLen());

            JSONObject value = null;
            if (commandBytes != null) {
                value = JSONObject.parseObject(new String(commandBytes));
            }
            Command cmd = null;
            if (value != null) {
                cmd = CommandUtil.jsonToCommand(value);
            }
            if (cmd instanceof SetCommand) {
                return ((SetCommand) cmd).getValue();
            }
            if (cmd instanceof RmCommand) {
                return null;
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            indexLock.readLock().unlock();
        }
        return null;
    }

    @Override
    public void Remove(String key) {
        try {
            RmCommand command = new RmCommand(key);
            byte[] commandBytes = command.toByte();
            // 加锁
            indexLock.writeLock().lock();

            // 先写内存表，内存表达到一定阀值再写进磁盘
            if (storeOperateNumber >= storeThreshold) {
                storeOperateNumber = 0;

//                RotateDataBaseFile();

                RotateDataBaseFile(this);
            }
            // 写table（wal）文件
            RandomAccessFileUtil.writeInt(this.genFilePath(), commandBytes.length);
            RandomAccessFileUtil.write(this.genFilePath(), commandBytes);
            // 保存到memTable
            index.remove(key);
            storeOperateNumber++;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void ReDoLog() {
        reloadIndex();

//                RotateDataBaseFile();

        RotateDataBaseFile(this);
    }

    @Override
    public void close() {}

    public void ClearDataBaseFile(String filePath) {
        try (FileWriter writer = new FileWriter(filePath)) {
            // 不写入任何内容，直接关闭writer
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void CompressIndexFile() {
        ArrayList<String> arrayList = new ArrayList<>();
        HashSet<String> hashSet = new HashSet<>();

        try (Scanner scanner = new Scanner(new File(this.genFilePath()))) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                // 处理每一行
                if (!hashSet.contains(line)) {
                    arrayList.add(line);
                    hashSet.add(line);
                }
            }
            //arrayList.remove(arrayList.size() - 1);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        ClearDataBaseFile(this.genFilePath());

        try (FileWriter writer = new FileWriter(this.genFilePath())) {
            for (String line :
                arrayList) {
                writer.write(line + "\r\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        index.clear();

        reloadIndex();
    }

    public void RotateDataBaseFile(NormalStore normalStore) {
        Rotate rotate = new Rotate(normalStore);
        rotate.start();
//        // 清空数据库文件
//        ClearDataBaseFile("YY-db");
//
//        // 压缩日志文件
//        CompressIndexFile();
//
//        // 重写数据库文件
//        try (FileWriter writer = new FileWriter("YY-db")) {
//            for (String key : index.keySet()) {
//                writer.write(key + "," + Get(key) + "\r\n");
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }

}
